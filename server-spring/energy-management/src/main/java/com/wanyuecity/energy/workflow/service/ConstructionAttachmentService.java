package com.wanyuecity.energy.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.verification.entity.Attachment;
import com.wanyuecity.energy.verification.mapper.AttachmentMapper;
import com.wanyuecity.energy.verification.service.dto.AttachmentView;
import com.wanyuecity.energy.workflow.entity.ConstructionAcceptance;
import com.wanyuecity.energy.workflow.entity.ConstructionInspection;
import com.wanyuecity.energy.workflow.entity.ConstructionWorkflowDetail;
import com.wanyuecity.energy.workflow.mapper.ConstructionAcceptanceMapper;
import com.wanyuecity.energy.workflow.mapper.ConstructionInspectionMapper;
import com.wanyuecity.energy.workflow.mapper.ConstructionWorkflowDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConstructionAttachmentService {
    private static final Set<String> TYPES = Set.of("INSPECTION", "RECTIFICATION", "COMPLETION", "ACCEPTANCE");
    private final AttachmentMapper attachmentMapper;
    private final ConstructionInspectionMapper inspectionMapper;
    private final ConstructionAcceptanceMapper acceptanceMapper;
    private final ConstructionWorkflowDetailMapper detailMapper;
    @Value("${energy.upload.base-dir}") private String baseDir;

    @Transactional
    public AttachmentView upload(Long workflowId, String targetType, Long targetId, String photoType,
                                 MultipartFile file, String username) {
        String businessType = businessType(workflowId, targetType, targetId);
        String type = photoType == null ? "" : photoType.trim().toUpperCase();
        if (!TYPES.contains(type)) throw BizException.badRequest("不支持的施工照片类型");
        if (file == null || file.isEmpty()) throw BizException.badRequest("请选择照片");
        if (file.getSize() > 10 * 1024 * 1024) throw BizException.badRequest("单张照片不能超过10MB");
        String mime = Optional.ofNullable(file.getContentType()).orElse("");
        if (!Set.of("image/jpeg", "image/png").contains(mime)) throw BizException.badRequest("只支持JPEG和PNG照片");
        try {
            byte[] bytes = file.getBytes();
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String ext = "image/png".equals(mime) ? ".png" : ".jpg";
            Path root = Path.of(baseDir).toAbsolutePath().normalize();
            Path dir = root.resolve(LocalDate.now().toString()).resolve("construction").resolve(String.valueOf(workflowId)).normalize();
            if (!dir.startsWith(root)) throw BizException.badRequest("非法附件路径");
            Files.createDirectories(dir);
            String stored = UUID.randomUUID() + ext;
            Path target = dir.resolve(stored);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            Attachment a = new Attachment();
            a.setBusinessType(businessType); a.setBusinessId(targetId); a.setFileType("IMAGE");
            a.setFileName(safeName(file.getOriginalFilename(), stored)); a.setStorageProvider("LOCAL");
            a.setStoragePath(root.relativize(target).toString()); a.setFileSize(file.getSize()); a.setMimeType(mime);
            a.setSha256(sha); a.setPhotoType(type); a.setUploadedBy(username); a.setUploadedAt(LocalDateTime.now());
            attachmentMapper.insert(a);
            return view(a);
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BizException("施工照片保存失败（" + ex.getClass().getSimpleName() + "）");
        }
    }

    public List<AttachmentView> list(Long workflowId) {
        requireWorkflow(workflowId);
        List<Long> inspectionIds = inspectionMapper.selectList(new QueryWrapper<ConstructionInspection>().eq("workflow_id", workflowId)).stream().map(ConstructionInspection::getId).toList();
        List<Long> acceptanceIds = acceptanceMapper.selectList(new QueryWrapper<ConstructionAcceptance>().eq("workflow_id", workflowId)).stream().map(ConstructionAcceptance::getId).toList();
        QueryWrapper<Attachment> query = new QueryWrapper<>();
        query.and(q -> {
            boolean added = false;
            if (!inspectionIds.isEmpty()) { q.and(x -> x.eq("business_type", "CONSTRUCTION_INSPECTION").in("business_id", inspectionIds)); added = true; }
            if (!acceptanceIds.isEmpty()) { if (added) q.or(); q.and(x -> x.eq("business_type", "CONSTRUCTION_ACCEPTANCE").in("business_id", acceptanceIds)); }
        }).orderByAsc("uploaded_at");
        if (inspectionIds.isEmpty() && acceptanceIds.isEmpty()) return List.of();
        return attachmentMapper.selectList(query).stream().map(this::view).toList();
    }

    public ResourceContent read(String token, Long attachmentId) {
        ConstructionWorkflowDetail detail = detailMapper.selectOne(new QueryWrapper<ConstructionWorkflowDetail>().eq("public_token", token).last("LIMIT 1"));
        if (detail == null) throw BizException.notFound("施工许可证不存在");
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !belongsToWorkflow(detail.getWorkflowId(), attachment)) throw BizException.notFound("施工照片不存在");
        Path root = Path.of(baseDir).toAbsolutePath().normalize();
        Path file = root.resolve(attachment.getStoragePath()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw BizException.notFound("施工照片文件不存在");
        return new ResourceContent(new FileSystemResource(file), attachment.getMimeType(), attachment.getFileName());
    }

    private String businessType(Long workflowId, String targetType, Long targetId) {
        requireWorkflow(workflowId);
        if ("INSPECTION".equalsIgnoreCase(targetType)) {
            ConstructionInspection row = inspectionMapper.selectById(targetId);
            if (row == null || !workflowId.equals(row.getWorkflowId())) throw BizException.notFound("巡检记录不存在");
            return "CONSTRUCTION_INSPECTION";
        }
        if ("ACCEPTANCE".equalsIgnoreCase(targetType)) {
            ConstructionAcceptance row = acceptanceMapper.selectById(targetId);
            if (row == null || !workflowId.equals(row.getWorkflowId())) throw BizException.notFound("验收记录不存在");
            return "CONSTRUCTION_ACCEPTANCE";
        }
        throw BizException.badRequest("不支持的附件归属类型");
    }

    private boolean belongsToWorkflow(Long workflowId, Attachment a) {
        if ("CONSTRUCTION_INSPECTION".equals(a.getBusinessType())) { ConstructionInspection row = inspectionMapper.selectById(a.getBusinessId()); return row != null && workflowId.equals(row.getWorkflowId()); }
        if ("CONSTRUCTION_ACCEPTANCE".equals(a.getBusinessType())) { ConstructionAcceptance row = acceptanceMapper.selectById(a.getBusinessId()); return row != null && workflowId.equals(row.getWorkflowId()); }
        return false;
    }

    private void requireWorkflow(Long workflowId) {
        if (detailMapper.selectCount(new QueryWrapper<ConstructionWorkflowDetail>().eq("workflow_id", workflowId)) == 0) throw BizException.notFound("施工流程不存在");
    }

    private AttachmentView view(Attachment a) { AttachmentView v = new AttachmentView(); v.setId(a.getId()); v.setDeviceId(a.getBusinessId()); v.setFileName(a.getFileName()); v.setFileSize(a.getFileSize()); v.setMimeType(a.getMimeType()); v.setSha256(a.getSha256()); v.setPhotoType(a.getPhotoType()); v.setUploadedAt(a.getUploadedAt()); return v; }
    private String safeName(String name, String fallback) { return name == null || name.isBlank() ? fallback : Path.of(name).getFileName().toString(); }
    public record ResourceContent(Resource resource, String mimeType, String fileName) {}
}
