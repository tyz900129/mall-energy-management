package com.wanyuecity.energy.system.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
public class MigrationPackageCodec {
    static final byte[] MAGIC = "WYMBAK01".getBytes(StandardCharsets.US_ASCII);
    static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final long MAX_ENTRY_COUNT = 100_000;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public void encrypt(Path plainZip, Path target, String password) {
        requirePassword(password);
        byte[] salt = random.generateSeed(SALT_BYTES);
        byte[] iv = random.generateSeed(IV_BYTES);
        try (OutputStream raw = new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
            raw.write(MAGIC);
            raw.write(salt);
            raw.write(iv);
            DataOutputStream data = new DataOutputStream(raw);
            data.writeInt(ITERATIONS);
            data.flush();
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iv, ITERATIONS);
            try (CipherOutputStream encrypted = new CipherOutputStream(raw, cipher);
                 InputStream in = new BufferedInputStream(Files.newInputStream(plainZip))) {
                in.transferTo(encrypted);
            }
        } catch (Exception e) {
            deleteQuietly(target);
            throw new BizException(1, "备份包加密失败", e);
        }
    }

    public void decrypt(Path encryptedFile, Path plainZip, String password) {
        requirePassword(password);
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(encryptedFile))) {
            if (!Arrays.equals(raw.readNBytes(MAGIC.length), MAGIC)) {
                throw BizException.badRequest("不是有效的万悦城系统备份包");
            }
            byte[] salt = raw.readNBytes(SALT_BYTES);
            byte[] iv = raw.readNBytes(IV_BYTES);
            int iterations = new DataInputStream(raw).readInt();
            if (salt.length != SALT_BYTES || iv.length != IV_BYTES || iterations < 100_000 || iterations > 1_000_000) {
                throw BizException.badRequest("备份包头信息无效");
            }
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, password, salt, iv, iterations);
            try (CipherInputStream decrypted = new CipherInputStream(raw, cipher);
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(plainZip, StandardOpenOption.CREATE_NEW))) {
                decrypted.transferTo(out);
            }
        } catch (BizException e) {
            deleteQuietly(plainZip);
            throw e;
        } catch (Exception e) {
            deleteQuietly(plainZip);
            throw BizException.badRequest("备份密码错误，或备份包已损坏");
        }
    }

    public PackageManifest validate(Path zipFile, long maxUncompressedBytes) {
        Map<String, String> actual = new LinkedHashMap<>();
        Map<String, String> expected = null;
        PackageManifest manifest = null;
        long total = 0;
        long entries = 0;
        Set<String> seen = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRY_COUNT) throw BizException.badRequest("备份包文件数量超过限制");
                String name = safeEntryName(entry.getName());
                if (!seen.add(name)) throw BizException.badRequest("备份包包含重复文件：" + name);
                if (entry.isDirectory()) continue;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                ByteArrayOutputStream capture = ("manifest.json".equals(name) || "checksums.sha256".equals(name))
                        ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = zip.read(buffer)) >= 0) {
                    if (n == 0) continue;
                    total += n;
                    if (total > maxUncompressedBytes) throw BizException.badRequest("备份包解压后容量超过限制");
                    digest.update(buffer, 0, n);
                    if (capture != null) capture.write(buffer, 0, n);
                }
                if (!"checksums.sha256".equals(name)) actual.put(name, HexFormat.of().formatHex(digest.digest()));
                if ("manifest.json".equals(name)) {
                    manifest = objectMapper.readValue(capture.toByteArray(), PackageManifest.class);
                } else if ("checksums.sha256".equals(name)) {
                    expected = parseChecksums(capture.toString(StandardCharsets.UTF_8));
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.badRequest("备份包结构无效或内容已损坏");
        }
        if (manifest == null || expected == null || !actual.containsKey("database.sql.gz")) {
            throw BizException.badRequest("备份包缺少清单、校验文件或数据库文件");
        }
        if (manifest.formatVersion() != 1) throw BizException.badRequest("暂不支持该备份包版本");
        if (!actual.equals(expected)) throw BizException.badRequest("备份包文件校验失败，内容可能不完整");
        return manifest;
    }

    public void extract(Path zipFile, Path databaseFile, Path secretsFile, Path uploadsDir, long maxUncompressedBytes) {
        long total = 0;
        try {
            Files.createDirectories(uploadsDir);
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = safeEntryName(entry.getName());
                    Path target = null;
                    if ("database.sql.gz".equals(name)) target = databaseFile;
                    else if ("portable-secrets.json".equals(name)) target = secretsFile;
                    else if (name.startsWith("uploads/") && name.length() > "uploads/".length()) {
                        target = uploadsDir.resolve(name.substring("uploads/".length())).normalize();
                        if (!target.startsWith(uploadsDir)) throw BizException.badRequest("备份包包含非法文件路径");
                    }
                    if (target == null || entry.isDirectory()) continue;
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
                        byte[] buffer = new byte[64 * 1024];
                        int n;
                        while ((n = zip.read(buffer)) >= 0) {
                            if (n == 0) continue;
                            total += n;
                            if (total > maxUncompressedBytes) throw BizException.badRequest("备份包解压后容量超过限制");
                            out.write(buffer, 0, n);
                        }
                    }
                }
            }
            if (!Files.isRegularFile(databaseFile)) throw BizException.badRequest("备份包中没有数据库文件");
            if (!Files.isRegularFile(secretsFile)) throw BizException.badRequest("备份包中没有可迁移的安全配置");
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(1, "备份包解压失败", e);
        }
    }

    public void writeChecksums(OutputStream out, Map<String, String> checksums) throws IOException {
        for (Map.Entry<String, String> entry : checksums.entrySet()) {
            out.write((entry.getValue() + "  " + entry.getKey() + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    public String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) if (n > 0) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Cipher cipher(int mode, String password, byte[] salt, byte[] iv, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        SecretKey derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec);
        spec.clearPassword();
        SecretKey key = new SecretKeySpec(derived.getEncoded(), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        return cipher;
    }

    private void requirePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw BizException.badRequest("备份密码长度必须为 8 至 128 位");
        }
    }

    private String safeEntryName(String input) {
        String name = input == null ? "" : input.replace('\\', '/');
        if (name.isBlank() || name.startsWith("/") || name.contains("../") || name.equals("..") || name.contains("\u0000")) {
            throw BizException.badRequest("备份包包含非法文件路径");
        }
        return name;
    }

    private Map<String, String> parseChecksums(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) continue;
            if (line.length() < 67 || line.charAt(64) != ' ' || line.charAt(65) != ' ') {
                throw BizException.badRequest("备份包校验文件格式无效");
            }
            String hash = line.substring(0, 64).toLowerCase(Locale.ROOT);
            if (!hash.matches("[0-9a-f]{64}")) throw BizException.badRequest("备份包校验值无效");
            String path = safeEntryName(line.substring(66));
            if (result.put(path, hash) != null) throw BizException.badRequest("备份包校验项重复");
        }
        return result;
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    public record PackageManifest(
            int formatVersion,
            String systemName,
            String applicationVersion,
            String schemaVersion,
            String createdAt,
            String createdBy,
            long databaseBytes,
            long uploadFileCount,
            long uploadBytes,
            Map<String, Long> recordCounts
    ) { }
}
