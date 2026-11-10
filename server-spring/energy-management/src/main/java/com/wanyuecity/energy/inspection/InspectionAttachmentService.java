package com.wanyuecity.energy.inspection;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.verification.entity.Attachment;
import com.wanyuecity.energy.verification.mapper.AttachmentMapper;
import com.wanyuecity.energy.verification.service.dto.AttachmentView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class InspectionAttachmentService {
    private final AttachmentMapper mapper; private final JdbcTemplate jdbc;
    @Value("${energy.upload.base-dir}") private String baseDir;

    @Transactional public AttachmentView upload(Long taskId,String photoType,MultipartFile file,String username){
        String businessType=requireTask(taskId); if(file==null||file.isEmpty())throw BizException.badRequest("请选择照片");
        if(file.getSize()>10*1024*1024)throw BizException.badRequest("单张照片不能超过10MB");
        String mime=Optional.ofNullable(file.getContentType()).orElse("");if(!Set.of("image/jpeg","image/png").contains(mime))throw BizException.badRequest("只支持JPEG和PNG照片");
        try{byte[] bytes=file.getBytes();String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));String ext="image/png".equals(mime)?".png":".jpg";String folder="CALENDAR_TASK".equals(businessType)?"calendar":"inspection";Path root=Path.of(baseDir).toAbsolutePath().normalize(),dir=root.resolve(LocalDate.now().toString()).resolve(folder).resolve(String.valueOf(taskId)).normalize();if(!dir.startsWith(root))throw BizException.badRequest("非法附件路径");Files.createDirectories(dir);String stored=UUID.randomUUID()+ext;Path target=dir.resolve(stored);Files.write(target,bytes,StandardOpenOption.CREATE_NEW);Attachment a=new Attachment();a.setBusinessType(businessType);a.setBusinessId(taskId);a.setFileType("IMAGE");a.setFileName(safe(file.getOriginalFilename(),stored));a.setStorageProvider("LOCAL");a.setStoragePath(root.relativize(target).toString());a.setFileSize(file.getSize());a.setMimeType(mime);a.setSha256(sha);a.setPhotoType(photoType==null?"TASK":photoType.trim().toUpperCase());a.setUploadedBy(username);a.setUploadedAt(LocalDateTime.now());mapper.insert(a);return view(a);}catch(Exception e){if(e instanceof BizException b)throw b;throw new BizException("任务照片保存失败（"+e.getClass().getSimpleName()+"）");}
    }
    public List<AttachmentView> list(Long taskId){String businessType=requireTask(taskId);return mapper.selectList(new QueryWrapper<Attachment>().eq("business_type",businessType).eq("business_id",taskId).orderByAsc("uploaded_at")).stream().map(this::view).toList();}
    public ResourceContent read(Long taskId,Long id){String businessType=requireTask(taskId);Attachment a=mapper.selectById(id);if(a==null||!businessType.equals(a.getBusinessType())||!taskId.equals(a.getBusinessId()))throw BizException.notFound("任务照片不存在");Path root=Path.of(baseDir).toAbsolutePath().normalize(),file=root.resolve(a.getStoragePath()).normalize();if(!file.startsWith(root)||!Files.isRegularFile(file))throw BizException.notFound("任务照片文件不存在");return new ResourceContent(new FileSystemResource(file),a.getMimeType(),a.getFileName());}
    private String requireTask(Long id){List<String> rows=jdbc.queryForList("SELECT source_type FROM inspection_task WHERE id=?",String.class,id);if(rows.isEmpty())throw BizException.notFound("任务不存在");String s=rows.get(0);return "CALENDAR".equals(s)||"MANUAL".equals(s)?"CALENDAR_TASK":"INSPECTION_TASK";}
    private AttachmentView view(Attachment a){AttachmentView v=new AttachmentView();v.setId(a.getId());v.setDeviceId(a.getBusinessId());v.setFileName(a.getFileName());v.setFileSize(a.getFileSize());v.setMimeType(a.getMimeType());v.setSha256(a.getSha256());v.setPhotoType(a.getPhotoType());v.setUploadedAt(a.getUploadedAt());return v;}
    private String safe(String n,String f){return n==null||n.isBlank()?f:Path.of(n).getFileName().toString();}
    public record ResourceContent(Resource resource,String mimeType,String fileName){}
}
