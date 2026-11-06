package com.wanyuecity.energy.patrol;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PatrolAttachmentService {
    private static final long MAX_SIZE = 8L * 1024 * 1024;
    private final JdbcTemplate jdbc;
    @Value("${energy.upload.base-dir}") private String baseDir;

    public PatrolAttachmentService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> saveAll(String businessType, Long businessId, MultipartFile[] files) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (files == null) return result;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            result.add(save(businessType, businessId, file));
        }
        return result;
    }

    public Map<String, Object> save(String businessType, Long businessId, MultipartFile file) {
        if (file.getSize() > MAX_SIZE) throw bad("单张照片不能超过 8MB");
        try {
            byte[] bytes = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw bad("照片文件无法识别，请上传 JPEG/PNG 图片");
            String fileId = UUID.randomUUID().toString().replace("-", "");
            Path dir = Path.of(baseDir, "patrol-images");
            Files.createDirectories(dir);
            Path target = dir.resolve(fileId + ".jpg");
            Files.write(target, bytes);
            String sha = sha256(bytes);
            jdbc.update("INSERT INTO patrol_attachment(business_type,business_id,file_name,file_path,mime_type,file_size,sha256) VALUES(?,?,?,?,?,?,?)",
                    businessType, businessId, safeName(file.getOriginalFilename()), fileId + ".jpg",
                    file.getContentType() == null ? "image/jpeg" : file.getContentType(), bytes.length, sha);
            Long id = jdbc.queryForObject("SELECT id FROM patrol_attachment WHERE file_path=? ORDER BY id DESC LIMIT 1", Long.class, fileId + ".jpg");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("fileName", safeName(file.getOriginalFilename()));
            row.put("fileSize", bytes.length);
            return row;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "照片保存失败");
        }
    }

    public List<Map<String, Object>> list(String businessType, Long businessId) {
        return jdbc.queryForList("SELECT id,file_name AS fileName,file_size AS fileSize,mime_type AS mimeType,created_at AS createdAt FROM patrol_attachment WHERE business_type=? AND business_id=? ORDER BY id",
                businessType, businessId);
    }

    /** 物理删除某个业务对象的全部照片：先删库记录，再删磁盘文件。 */
    public void deleteByBusiness(String businessType, Long businessId) {
        List<String> paths = jdbc.queryForList("SELECT file_path FROM patrol_attachment WHERE business_type=? AND business_id=?",
                String.class, businessType, businessId);
        jdbc.update("DELETE FROM patrol_attachment WHERE business_type=? AND business_id=?", businessType, businessId);
        for (String name : paths) {
            if (name == null || name.isBlank()) continue;
            try { Files.deleteIfExists(Path.of(baseDir, "patrol-images", name)); } catch (Exception ignored) { }
        }
    }

    /** 删除单张照片：先删库记录，再删磁盘文件。 */
    public void deleteById(Long attachmentId) {
        List<String> paths = jdbc.queryForList("SELECT file_path FROM patrol_attachment WHERE id=?", String.class, attachmentId);
        jdbc.update("DELETE FROM patrol_attachment WHERE id=?", attachmentId);
        for (String name : paths) {
            if (name == null || name.isBlank()) continue;
            try { Files.deleteIfExists(Path.of(baseDir, "patrol-images", name)); } catch (Exception ignored) { }
        }
    }

    public Long findIdInIssue(Long attachmentId, Long issueId, Long submissionId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM patrol_attachment WHERE id=? AND ((business_type='SUBMISSION' AND business_id=?) OR (business_type='RECTIFY' AND business_id=?))",
                Long.class, attachmentId, submissionId, issueId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public Resource load(Long attachmentId) {
        List<String> paths = jdbc.queryForList("SELECT file_path FROM patrol_attachment WHERE id=?", String.class, attachmentId);
        if (paths.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "照片不存在");
        Path path = Path.of(baseDir, "patrol-images", paths.get(0));
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "照片不存在");
        return new FileSystemResource(path);
    }

    public String mimeType(Long attachmentId) {
        List<String> types = jdbc.queryForList("SELECT mime_type FROM patrol_attachment WHERE id=?", String.class, attachmentId);
        return types.isEmpty() || types.get(0) == null ? "image/jpeg" : types.get(0);
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private String safeName(String name) {
        if (name == null) return "photo.jpg";
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
