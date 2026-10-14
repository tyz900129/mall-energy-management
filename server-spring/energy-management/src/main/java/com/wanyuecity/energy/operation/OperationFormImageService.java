package com.wanyuecity.energy.operation;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationFormImageService {
    private static final Set<String> FORM_TYPES = Set.of("CLEARANCE", "PROPERTY");
    private final JdbcTemplate jdbc;
    @Value("${energy.upload.base-dir}") private String baseDir;
    public OperationFormImageService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String,Object> upload(String qrToken, String formType, String fieldCode, MultipartFile file) {
        String type = text(formType).toUpperCase();
        if (!FORM_TYPES.contains(type)) throw bad("不支持的表单类型");
        if (!text(fieldCode).matches("[A-Za-z][A-Za-z0-9_]{0,40}")) throw bad("图片题编码不正确");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stall WHERE operation_qr_token=? AND status<>'DISABLED'", Integer.class, qrToken);
        if (count == null || count == 0) throw bad("铺位二维码无效");
        if (file == null || file.isEmpty()) throw bad("请选择图片");
        if (file.getSize() > 8L * 1024 * 1024) throw bad("图片不能超过8MB，请压缩后上传");
        try {
            byte[] bytes = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw bad("图片文件无法识别");
            String id = UUID.randomUUID().toString().replace("-", "");
            Path dir = Path.of(baseDir, "operation-form-images");
            Files.createDirectories(dir);
            Files.write(dir.resolve(id + ".jpg"), bytes);
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("id", id); result.put("fileName", text(file.getOriginalFilename())); result.put("fileSize", bytes.length);
            result.put("url", "/api/v1/operations/form-images/" + id);
            return result;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败"); }
    }

    public Resource load(String id) {
        if (!text(id).matches("[a-fA-F0-9]{32}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        Path path = Path.of(baseDir, "operation-form-images", id + ".jpg");
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        return new FileSystemResource(path);
    }
    private String text(Object value) { return value == null ? "" : value.toString().trim(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
