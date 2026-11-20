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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
    /** 缩略图长边像素，画册/列表只加载缩略图，体积约为原图的 1/10 */
    private static final int THUMB_MAX_SIDE = 480;
    private final JdbcTemplate jdbc;
    @Value("${energy.upload.base-dir}") private String baseDir;
    public OperationFormImageService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String,Object> upload(String qrToken, String formType, String fieldCode, MultipartFile file) {
        String type = text(formType).toUpperCase();
        if (!FORM_TYPES.contains(type)) throw bad("不支持的表单类型");
        if (!text(fieldCode).matches("[A-Za-z][A-Za-z0-9_]{0,40}")) throw bad("图片题编码不正确");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM stall WHERE operation_qr_token=? AND status<>'DECOMMISSIONED'", Integer.class, qrToken);
        if (count == null || count == 0) throw bad("铺位二维码无效");
        if (file == null || file.isEmpty()) throw bad("请选择图片");
        if (file.getSize() > 8L * 1024 * 1024) throw bad("图片不能超过8MB，请压缩后上传");
        try {
            byte[] bytes = file.getBytes();
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) throw bad("图片文件无法识别");
            String id = UUID.randomUUID().toString().replace("-", "");
            Path dir = Path.of(baseDir, "operation-form-images");
            Files.createDirectories(dir);
            Files.write(dir.resolve(id + ".jpg"), bytes);
            writeThumb(source, dir.resolve(id + "_thumb.jpg"));
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("id", id); result.put("fileName", text(file.getOriginalFilename())); result.put("fileSize", bytes.length);
            result.put("url", "/api/v1/operations/form-images/" + id);
            result.put("thumbUrl", "/api/v1/operations/form-images/" + id + "/thumb");
            return result;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败"); }
    }

    public Resource load(String id) {
        Path path = originPath(id);
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        return new FileSystemResource(path);
    }

    /** 缩略图；历史图片没有缩略图时按需生成一次并落盘，后续直接命中文件 */
    public Resource loadThumb(String id) {
        Path origin = originPath(id);
        if (!Files.isRegularFile(origin)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        Path thumb = origin.resolveSibling(id + "_thumb.jpg");
        if (Files.isRegularFile(thumb)) return new FileSystemResource(thumb);
        try {
            BufferedImage source = ImageIO.read(origin.toFile());
            if (source == null) return new FileSystemResource(origin);
            writeThumb(source, thumb);
            return new FileSystemResource(thumb);
        } catch (Exception ex) {
            return new FileSystemResource(origin);
        }
    }

    private Path originPath(String id) {
        if (!text(id).matches("[a-fA-F0-9]{32}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        return Path.of(baseDir, "operation-form-images", id + ".jpg");
    }

    private void writeThumb(BufferedImage source, Path target) throws Exception {
        int w = source.getWidth(), h = source.getHeight();
        BufferedImage out;
        if (w <= THUMB_MAX_SIDE && h <= THUMB_MAX_SIDE) {
            out = toRgb(source);
        } else {
            double scale = THUMB_MAX_SIDE / (double) Math.max(w, h);
            int tw = Math.max(1, (int) Math.round(w * scale));
            int th = Math.max(1, (int) Math.round(h * scale));
            BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, tw, th, java.awt.Color.WHITE, null);
            g.dispose();
            out = thumb;
        }
        ImageIO.write(out, "jpg", target.toFile());
    }

    /** PNG 带透明通道时转 RGB 铺白底，否则 JPEG 编码会失败 */
    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) return source;
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(source, 0, 0, java.awt.Color.WHITE, null);
        g.dispose();
        return rgb;
    }
    private String text(Object value) { return value == null ? "" : value.toString().trim(); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
