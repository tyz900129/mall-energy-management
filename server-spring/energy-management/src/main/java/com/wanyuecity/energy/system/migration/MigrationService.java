package com.wanyuecity.energy.system.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import com.wanyuecity.energy.platform.crypto.CredentialCryptoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {
    public static final int CHUNK_BYTES = 8 * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_MULTIPLIER = 6;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Set<String> COUNT_TABLES = Set.of(
            "sys_user", "platform_merchant_account", "stall", "platform_device",
            "stall_device_binding", "workflow", "field_verification_task", "attachment",
            "energy_usage_monthly", "energy_usage_daily", "energy_utility_bill");

    private final DataSourceProperties dataSourceProperties;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MigrationPackageCodec codec;
    private final MigrationMaintenance maintenance;
    private final Flyway flyway;
    private final CredentialCryptoService credentialCrypto;

    @Value("${energy.upload.base-dir}") private String uploadBaseDir;
    @Value("${energy.migration.base-dir}") private String migrationBaseDir;
    @Value("${energy.migration.max-package-bytes:21474836480}") private long maxPackageBytes;
    @Value("${energy.migration.retention-hours:24}") private long retentionHours;

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, UploadSession> uploads = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "system-migration");
        thread.setDaemon(true);
        return thread;
    });
    private Path baseDir;

    @PostConstruct
    void initialize() {
        try {
            baseDir = Path.of(migrationBaseDir).toAbsolutePath().normalize();
            Files.createDirectories(baseDir.resolve("exports"));
            Files.createDirectories(baseDir.resolve("imports"));
            Files.createDirectories(baseDir.resolve("safety"));
            restrictPermissions(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("cannot initialize migration directory", e);
        }
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }

    public JobView startExport(String password) {
        validatePassword(password);
        String id = UUID.randomUUID().toString();
        Job job = new Job(id, "EXPORT", "QUEUED", currentOperator());
        jobs.put(id, job);
        executor.submit(() -> exportPackage(job, password));
        return view(job);
    }

    public UploadView initializeUpload(String fileName, long fileSize) {
        if (fileSize <= 0 || fileSize > maxPackageBytes) throw BizException.badRequest("备份包大小无效或超过系统限制");
        String safeName = safeFileName(fileName);
        String id = UUID.randomUUID().toString();
        Path file = baseDir.resolve("imports").resolve(id + ".upload");
        UploadSession session = new UploadSession(id, safeName, fileSize, file, currentOperator());
        uploads.put(id, session);
        return new UploadView(id, CHUNK_BYTES, 0, fileSize, false);
    }

    public synchronized UploadView appendChunk(String uploadId, int index, byte[] chunk) {
        UploadSession session = requireUpload(uploadId);
        if (!Objects.equals(session.operator, currentOperator())) throw BizException.forbidden("不能续传其他用户的备份包");
        if (index != session.nextChunk.get()) throw BizException.badRequest("上传分块顺序不正确，请重新选择备份包");
        if (chunk == null || chunk.length == 0 || chunk.length > CHUNK_BYTES) throw BizException.badRequest("上传分块大小无效");
        if (session.received + chunk.length > session.fileSize) throw BizException.badRequest("上传内容超过声明的文件大小");
        try {
            Files.write(session.file, chunk, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new BizException(1, "备份包分块保存失败", e);
        }
        session.received += chunk.length;
        session.nextChunk.incrementAndGet();
        session.updatedAt = LocalDateTime.now();
        return new UploadView(session.id, CHUNK_BYTES, session.received, session.fileSize, session.received == session.fileSize);
    }

    public JobView startValidation(String uploadId, String password) {
        validatePassword(password);
        UploadSession upload = requireUpload(uploadId);
        if (upload.received != upload.fileSize) throw BizException.badRequest("备份包尚未上传完整");
        if (!Objects.equals(upload.operator, currentOperator())) throw BizException.forbidden("不能使用其他用户上传的备份包");
        String id = UUID.randomUUID().toString();
        Job job = new Job(id, "IMPORT", "QUEUED", upload.operator);
        job.uploadId = uploadId;
        jobs.put(id, job);
        executor.submit(() -> validatePackage(job, upload, password));
        return view(job);
    }

    public JobView startRestore(String jobId, String confirmation) {
        Job job = requireOwnedJob(jobId);
        if (!"IMPORT".equals(job.type) || !"READY".equals(job.status)) throw BizException.badRequest("备份包尚未通过校验");
        if (!"确认覆盖导入".equals(confirmation)) throw BizException.badRequest("请输入“确认覆盖导入”后再执行");
        job.status = "QUEUED_RESTORE";
        job.message = "等待执行导入";
        job.updatedAt = LocalDateTime.now();
        executor.submit(() -> restorePackage(job));
        return view(job);
    }

    public JobView getJob(String id) { return view(requireOwnedJob(id)); }

    public Download download(String id, String token) {
        Job job = jobs.get(id);
        if (job == null || !"EXPORT".equals(job.type) || !"COMPLETED".equals(job.status)
                || !Objects.equals(job.downloadToken, token) || job.outputFile == null || !Files.isRegularFile(job.outputFile)) {
            throw BizException.notFound("备份文件不存在或下载凭证已失效");
        }
        return new Download(new FileSystemResource(job.outputFile), job.fileName, size(job.outputFile));
    }

    private void exportPackage(Job job, String password) {
        Path working = null;
        try {
            job.update("RUNNING", 3, "正在准备数据库备份");
            working = Files.createTempDirectory(baseDir.resolve("exports"), "build-");
            Path database = working.resolve("database.sql.gz");
            dumpDatabase(database);
            job.update("RUNNING", 35, "正在整理附件文件");
            Path zip = working.resolve("package.zip");
            MigrationPackageCodec.PackageManifest manifest = createZip(zip, database, job.operator, job);
            job.preview = manifest;
            job.update("RUNNING", 82, "正在加密备份包");
            String name = "wanyuecity-full-" + STAMP.format(LocalDateTime.now()) + ".wymbackup";
            Path target = baseDir.resolve("exports").resolve(job.id + ".wymbackup");
            codec.encrypt(zip, target, password);
            job.outputFile = target;
            job.fileName = name;
            job.downloadToken = randomToken();
            job.update("COMPLETED", 100, "完整备份已生成");
        } catch (Exception e) {
            fail(job, "生成备份失败", e);
        } finally {
            deleteTreeQuietly(working);
        }
    }

    private MigrationPackageCodec.PackageManifest createZip(Path zipFile, Path database, String operator, Job job) throws Exception {
        Path uploadsRoot = uploadRoot();
        long uploadCount = 0;
        long uploadBytes = 0;
        if (Files.isDirectory(uploadsRoot)) {
            try (var stream = Files.walk(uploadsRoot)) {
                List<Path> files = stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
                uploadCount = files.size();
                for (Path file : files) uploadBytes += Files.size(file);
            }
        }
        MigrationPackageCodec.PackageManifest manifest = new MigrationPackageCodec.PackageManifest(
                1, "万悦城后勤管理系统", "0.1.0", schemaVersion(), LocalDateTime.now().toString(), operator,
                Files.size(database), uploadCount, uploadBytes, recordCounts());
        Map<String, String> checksums = new LinkedHashMap<>();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            addFile(zip, database, "database.sql.gz", checksums);
            addBytes(zip, objectMapper.writeValueAsBytes(exportPortableSecrets()), "portable-secrets.json", checksums);
            if (Files.isDirectory(uploadsRoot)) {
                try (var stream = Files.walk(uploadsRoot)) {
                    List<Path> files = stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
                    int completed = 0;
                    for (Path file : files) {
                        String relative = uploadsRoot.relativize(file).toString().replace('\\', '/');
                        addFile(zip, file, "uploads/" + relative, checksums);
                        completed++;
                        if (completed % 25 == 0 && !files.isEmpty()) {
                            job.progress = 35 + (int) Math.min(40, completed * 40L / files.size());
                        }
                    }
                }
            }
            byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            addBytes(zip, manifestBytes, "manifest.json", checksums);
            zip.putNextEntry(new ZipEntry("checksums.sha256"));
            codec.writeChecksums(zip, checksums);
            zip.closeEntry();
        }
        return manifest;
    }

    private void validatePackage(Job job, UploadSession upload, String password) {
        Path plain = baseDir.resolve("imports").resolve(job.id + ".zip");
        try {
            job.update("VALIDATING", 10, "正在解密备份包");
            codec.decrypt(upload.file, plain, password);
            job.update("VALIDATING", 55, "正在校验备份内容");
            MigrationPackageCodec.PackageManifest manifest = codec.validate(plain, maxPackageBytes * MAX_UNCOMPRESSED_MULTIPLIER);
            verifySchemaCompatibility(manifest.schemaVersion());
            job.decryptedZip = plain;
            job.preview = manifest;
            job.update("READY", 100, "校验通过，可以执行覆盖导入");
        } catch (Exception e) {
            deleteTreeQuietly(plain);
            fail(job, "备份包校验失败", e);
        }
    }

    private void restorePackage(Job job) {
        Path staging = null;
        Path safety = null;
        if (!maintenance.begin()) {
            fail(job, "另一项导入正在执行", null);
            return;
        }
        try {
            job.update("RESTORING", 5, "系统已进入维护模式");
            staging = Files.createTempDirectory(baseDir.resolve("imports"), "restore-");
            Path incomingDatabase = staging.resolve("database.sql.gz");
            Path incomingSecrets = staging.resolve("portable-secrets.json");
            Path incomingUploads = staging.resolve("uploads");
            codec.extract(job.decryptedZip, incomingDatabase, incomingSecrets, incomingUploads, maxPackageBytes * MAX_UNCOMPRESSED_MULTIPLIER);

            safety = baseDir.resolve("safety").resolve("before-import-" + STAMP.format(LocalDateTime.now()) + "-" + job.id.substring(0, 8));
            Files.createDirectories(safety);
            restrictPermissions(safety);
            job.update("RESTORING", 20, "正在创建导入前安全备份");
            dumpDatabase(safety.resolve("database.sql.gz"));
            copyTree(uploadRoot(), safety.resolve("uploads"));

            job.update("RESTORING", 48, "正在恢复数据库");
            importDatabase(incomingDatabase);
            flyway.migrate();
            importPortableSecrets(incomingSecrets);

            job.update("RESTORING", 75, "正在恢复附件文件");
            replaceUploads(incomingUploads, safety.resolve("uploads"));
            job.safetyBackup = safety.toString();
            job.update("COMPLETED", 100, "数据导入完成，请重新登录系统");
        } catch (Exception error) {
            log.error("system migration restore failed, job={}", job.id, error);
            if (safety != null && Files.isRegularFile(safety.resolve("database.sql.gz"))) {
                try {
                    job.update("ROLLING_BACK", 85, "导入失败，正在自动回滚");
                    importDatabase(safety.resolve("database.sql.gz"));
                    replaceUploads(safety.resolve("uploads"), null);
                    fail(job, "导入失败，已恢复到导入前状态", error);
                } catch (Exception rollbackError) {
                    log.error("system migration rollback failed, job={}", job.id, rollbackError);
                    fail(job, "导入及自动回滚均失败，请立即联系管理员并使用安全备份恢复", rollbackError);
                }
            } else {
                fail(job, "导入失败，原数据尚未被覆盖", error);
            }
        } finally {
            maintenance.end();
            deleteTreeQuietly(staging);
        }
    }

    private void dumpDatabase(Path target) throws Exception {
        DbTarget db = databaseTarget();
        Files.createDirectories(target.getParent());
        Path errorFile = target.resolveSibling(target.getFileName() + ".err");
        List<String> command = List.of("mysqldump", "--single-transaction", "--quick", "--routines", "--triggers",
                "--no-tablespaces", "--hex-blob", "--default-character-set=utf8mb4",
                "-h", db.host(), "-P", String.valueOf(db.port()), "-u", dataSourceProperties.getUsername(), db.database());
        ProcessBuilder builder = new ProcessBuilder(command).redirectError(errorFile.toFile());
        builder.environment().put("MYSQL_PWD", Optional.ofNullable(dataSourceProperties.getPassword()).orElse(""));
        Process process = builder.start();
        try (InputStream in = process.getInputStream(); GZIPOutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)))) {
            in.transferTo(gzip);
        }
        int exit = process.waitFor();
        if (exit != 0) throw new IOException("mysqldump exit " + exit + ": " + safeError(errorFile));
        Files.deleteIfExists(errorFile);
    }

    private void importDatabase(Path source) throws Exception {
        DbTarget db = databaseTarget();
        Path errorFile = source.resolveSibling(source.getFileName() + ".import.err");
        List<String> command = List.of("mysql", "--binary-mode=1", "--default-character-set=utf8mb4",
                "-h", db.host(), "-P", String.valueOf(db.port()), "-u", dataSourceProperties.getUsername(), db.database());
        ProcessBuilder builder = new ProcessBuilder(command).redirectError(errorFile.toFile());
        builder.environment().put("MYSQL_PWD", Optional.ofNullable(dataSourceProperties.getPassword()).orElse(""));
        Process process = builder.start();
        try (OutputStream out = new BufferedOutputStream(process.getOutputStream());
             GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            gzip.transferTo(out);
        }
        int exit = process.waitFor();
        if (exit != 0) throw new IOException("mysql exit " + exit + ": " + safeError(errorFile));
        Files.deleteIfExists(errorFile);
    }

    private void addFile(ZipOutputStream zip, Path file, String name, Map<String, String> checksums) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) if (n > 0) { zip.write(buffer, 0, n); digest.update(buffer, 0, n); }
        }
        zip.closeEntry();
        checksums.put(name, HexFormat.of().formatHex(digest.digest()));
    }

    private void addBytes(ZipOutputStream zip, byte[] bytes, String name, Map<String, String> checksums) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
        checksums.put(name, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    private void verifySchemaCompatibility(String source) {
        int sourceVersion = numericVersion(source);
        int targetVersion = numericVersion(schemaVersion());
        if (sourceVersion > targetVersion) throw BizException.badRequest("备份来自更高版本系统，请先升级当前系统后再导入");
    }

    private int numericVersion(String value) {
        try { return Integer.parseInt(value == null ? "0" : value.replaceAll("[^0-9].*$", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    private String schemaVersion() {
        try { return jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1", String.class); }
        catch (Exception e) { return "0"; }
    }

    private Map<String, Long> recordCounts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : COUNT_TABLES) {
            try { result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Long.class)); }
            catch (Exception ignored) { }
        }
        return result;
    }

    private PortableSecrets exportPortableSecrets() {
        if (!credentialCrypto.isAvailable()) throw new BizException("服务器安全密钥不可用，无法导出可迁移的安全配置");
        try {
            String platformPassword = null;
            Map<String, Object> row = jdbc.queryForMap("SELECT password_ciphertext,password_iv FROM platform_config WHERE id=1");
            String ciphertext = Objects.toString(row.get("password_ciphertext"), null);
            String iv = Objects.toString(row.get("password_iv"), null);
            if (ciphertext != null && !"__PENDING__".equals(ciphertext) && iv != null) {
                platformPassword = credentialCrypto.decrypt(ciphertext, iv);
            }
            Map<Long, String> webhookUrls = new LinkedHashMap<>();
            jdbc.query("SELECT id,webhook_url_ciphertext,webhook_url_iv FROM webhook_config", rs -> {
                String webhookCiphertext = rs.getString("webhook_url_ciphertext");
                String webhookIv = rs.getString("webhook_url_iv");
                if (webhookCiphertext != null && webhookIv != null) webhookUrls.put(rs.getLong("id"), credentialCrypto.decrypt(webhookCiphertext, webhookIv));
            });
            return new PortableSecrets(platformPassword, webhookUrls);
        } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {
            return new PortableSecrets(null, Map.of());
        } catch (Exception e) {
            throw new BizException(1, "安全配置无法解密，请检查服务器安全密钥后重试", e);
        }
    }

    private void importPortableSecrets(Path file) throws IOException {
        if (!credentialCrypto.isAvailable()) throw new IllegalStateException("target credential encryption is unavailable");
        PortableSecrets secrets = objectMapper.readValue(file.toFile(), PortableSecrets.class);
        if (secrets.platformPassword() != null && !secrets.platformPassword().isBlank()) {
            CredentialCryptoService.Encrypted encrypted = credentialCrypto.encrypt(secrets.platformPassword());
            jdbc.update("UPDATE platform_config SET password_ciphertext=?,password_iv=? WHERE id=1", encrypted.ciphertext(), encrypted.iv());
        }
        if (secrets.webhookUrls() != null) {
            for (Map.Entry<Long, String> entry : secrets.webhookUrls().entrySet()) {
                CredentialCryptoService.Encrypted encrypted = credentialCrypto.encrypt(entry.getValue());
                jdbc.update("UPDATE webhook_config SET webhook_url_ciphertext=?,webhook_url_iv=? WHERE id=?",
                        encrypted.ciphertext(), encrypted.iv(), entry.getKey());
            }
        }
    }

    private DbTarget databaseTarget() {
        String url = dataSourceProperties.getUrl();
        if (url == null || !url.startsWith("jdbc:mysql://")) throw new IllegalStateException("only MySQL datasource is supported");
        String value = url.substring("jdbc:mysql://".length());
        int slash = value.indexOf('/');
        if (slash <= 0) throw new IllegalStateException("invalid MySQL datasource URL");
        String address = value.substring(0, slash);
        String database = value.substring(slash + 1).split("\\?", 2)[0];
        int colon = address.lastIndexOf(':');
        String host = colon > 0 ? address.substring(0, colon) : address;
        int port = colon > 0 ? Integer.parseInt(address.substring(colon + 1)) : 3306;
        if (!database.matches("[A-Za-z0-9_]+")) throw new IllegalStateException("invalid MySQL database name");
        return new DbTarget(host, port, database);
    }

    private void replaceUploads(Path source, Path rollbackSource) throws IOException {
        Path root = uploadRoot();
        Path parent = root.getParent();
        if (parent == null || root.getNameCount() < 2) throw new IOException("unsafe upload directory");
        Path incoming = parent.resolve(root.getFileName() + ".migration-" + UUID.randomUUID()).normalize();
        if (!incoming.startsWith(parent)) throw new IOException("unsafe upload staging directory");
        try {
            copyTree(source, incoming);
            deleteTree(root);
            try {
                Files.move(incoming, root, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(incoming, root);
            }
        } catch (IOException error) {
            deleteTreeQuietly(incoming);
            if (rollbackSource != null && Files.exists(rollbackSource)) {
                deleteTreeQuietly(root);
                copyTree(rollbackSource, root);
            }
            throw error;
        }
    }

    private void copyTree(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        if (source == null || !Files.exists(source)) return;
        try (var stream = Files.walk(source)) {
            for (Path path : stream.sorted().toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target)) throw new IOException("unsafe file path");
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination);
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private Path uploadRoot() { return Path.of(uploadBaseDir).toAbsolutePath().normalize(); }

    private void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        if (root.getNameCount() < 2) throw new IOException("refusing to delete broad path");
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private void deleteTreeQuietly(Path root) {
        try { deleteTree(root); } catch (Exception ignored) { }
    }

    private void restrictPermissions(Path path) {
        try { Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)); }
        catch (UnsupportedOperationException | IOException ignored) { }
    }

    private UploadSession requireUpload(String id) {
        UploadSession session = uploads.get(id);
        if (session == null) throw BizException.notFound("上传任务不存在或已过期");
        return session;
    }

    private Job requireOwnedJob(String id) {
        Job job = jobs.get(id);
        if (job == null) throw BizException.notFound("迁移任务不存在或已过期");
        if (!Objects.equals(job.operator, currentOperator())) throw BizException.forbidden("不能查看其他用户的迁移任务");
        return job;
    }

    private JobView view(Job job) {
        return new JobView(job.id, job.type, job.status, job.progress, job.message, job.error,
                job.fileName, job.downloadToken, job.preview, job.safetyBackup, job.createdAt, job.updatedAt);
    }

    private void fail(Job job, String message, Exception error) {
        job.status = "FAILED";
        job.message = message;
        job.error = error == null ? null : safeMessage(error);
        job.updatedAt = LocalDateTime.now();
    }

    private String safeMessage(Exception error) {
        if (error instanceof BizException && error.getMessage() != null) return error.getMessage();
        return "执行失败（" + error.getClass().getSimpleName() + "），请查看服务日志";
    }

    private String safeError(Path path) {
        try {
            String value = Files.readString(path, StandardCharsets.UTF_8).replaceAll("(?i)password\\s*[:=]\\s*\\S+", "password=***");
            return value.substring(0, Math.min(500, value.length()));
        } catch (IOException e) { return "no command output"; }
    }

    private String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "unknown" : auth.getName();
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) throw BizException.badRequest("备份密码长度必须为 8 至 128 位");
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) return "backup.wymbackup";
        String result = Path.of(name).getFileName().toString();
        return result.length() > 180 ? result.substring(result.length() - 180) : result;
    }

    private long size(Path path) { try { return Files.size(path); } catch (IOException e) { return 0; } }

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(Math.max(1, retentionHours));
        jobs.entrySet().removeIf(entry -> {
            Job job = entry.getValue();
            if (job.updatedAt.isAfter(cutoff) || Set.of("RUNNING", "VALIDATING", "RESTORING", "ROLLING_BACK").contains(job.status)) return false;
            deleteTreeQuietly(job.outputFile);
            deleteTreeQuietly(job.decryptedZip);
            return true;
        });
        uploads.entrySet().removeIf(entry -> {
            UploadSession upload = entry.getValue();
            if (upload.updatedAt.isAfter(cutoff)) return false;
            deleteTreeQuietly(upload.file);
            return true;
        });
    }

    private record DbTarget(String host, int port, String database) { }
    private record PortableSecrets(String platformPassword, Map<Long, String> webhookUrls) { }

    private static final class Job {
        final String id;
        final String type;
        final String operator;
        final LocalDateTime createdAt = LocalDateTime.now();
        volatile LocalDateTime updatedAt = createdAt;
        volatile String status;
        volatile int progress;
        volatile String message;
        volatile String error;
        volatile String uploadId;
        volatile Path outputFile;
        volatile Path decryptedZip;
        volatile String fileName;
        volatile String downloadToken;
        volatile MigrationPackageCodec.PackageManifest preview;
        volatile String safetyBackup;

        Job(String id, String type, String status, String operator) {
            this.id = id; this.type = type; this.status = status; this.operator = operator;
            this.message = "任务已创建";
        }

        void update(String status, int progress, String message) {
            this.status = status; this.progress = progress; this.message = message; this.updatedAt = LocalDateTime.now();
        }
    }

    private static final class UploadSession {
        final String id;
        final String fileName;
        final long fileSize;
        final Path file;
        final String operator;
        final AtomicInteger nextChunk = new AtomicInteger();
        volatile long received;
        volatile LocalDateTime updatedAt = LocalDateTime.now();

        UploadSession(String id, String fileName, long fileSize, Path file, String operator) {
            this.id = id; this.fileName = fileName; this.fileSize = fileSize; this.file = file; this.operator = operator;
        }
    }

    public record JobView(String id, String type, String status, int progress, String message, String error,
                          String fileName, String downloadToken, MigrationPackageCodec.PackageManifest preview,
                          String safetyBackup, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record UploadView(String uploadId, int chunkBytes, long receivedBytes, long totalBytes, boolean complete) { }
    public record Download(FileSystemResource resource, String fileName, long size) { }
}
