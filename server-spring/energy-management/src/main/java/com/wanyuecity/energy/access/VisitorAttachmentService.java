package com.wanyuecity.energy.access;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.verification.entity.Attachment;
import com.wanyuecity.energy.verification.mapper.AttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.security.MessageDigest;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class VisitorAttachmentService {
    private final AttachmentMapper mapper;
    @Value("${energy.upload.base-dir}") private String baseDir;

    @Transactional public Map<String,Object> upload(Long appointmentId,String type,MultipartFile file,String username){
        String photoType=type==null?"":type.toUpperCase(Locale.ROOT);if(!Set.of("ID_FRONT","ID_BACK","FACE").contains(photoType))throw BizException.badRequest("照片类型不正确");if(file==null||file.isEmpty())throw BizException.badRequest("请选择照片");if(file.getSize()>10*1024*1024)throw BizException.badRequest("单张照片不能超过10MB");String mime=Optional.ofNullable(file.getContentType()).orElse("");if(!Set.of("image/jpeg","image/png").contains(mime))throw BizException.badRequest("只支持JPEG和PNG照片");
        try{byte[]bytes=file.getBytes();var image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null)throw BizException.badRequest("图片文件无法识别");if("FACE".equals(photoType)&&(image.getWidth()<200||image.getHeight()<200))throw BizException.badRequest("人脸照片分辨率不能低于200×200");Path root=Path.of(baseDir).toAbsolutePath().normalize(),dir=root.resolve(LocalDate.now().toString()).resolve("visitor").resolve(String.valueOf(appointmentId)).normalize();if(!dir.startsWith(root))throw BizException.badRequest("非法附件路径");Files.createDirectories(dir);String ext="image/png".equals(mime)?".png":".jpg",stored=UUID.randomUUID()+ext;Path target=dir.resolve(stored);Files.write(target,bytes,StandardOpenOption.CREATE_NEW);mapper.delete(new QueryWrapper<Attachment>().eq("business_type","VISITOR_APPOINTMENT").eq("business_id",appointmentId).eq("photo_type",photoType));Attachment a=new Attachment();a.setBusinessType("VISITOR_APPOINTMENT");a.setBusinessId(appointmentId);a.setFileType("IMAGE");a.setFileName(file.getOriginalFilename()==null?stored:Path.of(file.getOriginalFilename()).getFileName().toString());a.setStorageProvider("LOCAL");a.setStoragePath(root.relativize(target).toString());a.setFileSize(file.getSize());a.setMimeType(mime);a.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));a.setPhotoType(photoType);a.setUploadedBy(username);a.setUploadedAt(LocalDateTime.now());mapper.insert(a);return Map.of("id",a.getId(),"photoType",photoType,"fileName",a.getFileName());}catch(Exception ex){if(ex instanceof BizException b)throw b;throw new BizException("访客照片保存失败");}
    }
    public List<Map<String,Object>> list(Long id){return mapper.selectList(new QueryWrapper<Attachment>().eq("business_type","VISITOR_APPOINTMENT").eq("business_id",id).orderByAsc("id")).stream().map(a->Map.<String,Object>of("id",a.getId(),"photoType",a.getPhotoType(),"fileName",a.getFileName())).toList();}
    public ResourceContent read(Long appointmentId,Long attachmentId){Attachment a=mapper.selectById(attachmentId);if(a==null||!"VISITOR_APPOINTMENT".equals(a.getBusinessType())||!appointmentId.equals(a.getBusinessId()))throw BizException.notFound("照片不存在");Path root=Path.of(baseDir).toAbsolutePath().normalize(),path=root.resolve(a.getStoragePath()).normalize();if(!path.startsWith(root)||!Files.isRegularFile(path))throw BizException.notFound("照片文件不存在");return new ResourceContent(new FileSystemResource(path),a.getMimeType(),a.getFileName());}
    public byte[] bytes(Long appointmentId,String photoType){Attachment a=mapper.selectOne(new QueryWrapper<Attachment>().eq("business_type","VISITOR_APPOINTMENT").eq("business_id",appointmentId).eq("photo_type",photoType).last("LIMIT 1"));if(a==null)throw BizException.badRequest("缺少"+("FACE".equals(photoType)?"人脸照片":"必要证件照片"));Path root=Path.of(baseDir).toAbsolutePath().normalize(),path=root.resolve(a.getStoragePath()).normalize();if(!path.startsWith(root)||!Files.isRegularFile(path))throw BizException.badRequest("照片文件不存在");try{return Files.readAllBytes(path);}catch(Exception ex){throw new BizException("读取照片失败");}}
    public byte[] faceJpeg(Long appointmentId){try{byte[]source=bytes(appointmentId,"FACE");var image=ImageIO.read(new ByteArrayInputStream(source));if(image==null)throw BizException.badRequest("人脸照片无法识别");double scale=Math.min(1d,1000d/Math.max(image.getWidth(),image.getHeight()));int width=Math.max(200,(int)Math.round(image.getWidth()*scale)),height=Math.max(200,(int)Math.round(image.getHeight()*scale));var rgb=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=rgb.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(image,0,0,width,height,java.awt.Color.WHITE,null);g.dispose();byte[]encoded=null;for(float quality:new float[]{0.82f,0.68f,0.54f,0.42f}){encoded=encodeJpeg(rgb,quality);if(encoded.length<=200*1024)break;}if(encoded==null||encoded.length>300*1024)throw BizException.badRequest("人脸照片处理后仍过大，请重新拍摄清晰的正面照");return encoded;}catch(BizException ex){throw ex;}catch(Exception ex){throw new BizException("人脸照片转换失败："+ex.getClass().getSimpleName());}}
    private byte[] encodeJpeg(java.awt.image.BufferedImage image,float quality)throws Exception{var writer=ImageIO.getImageWritersByFormatName("jpeg").next();var out=new ByteArrayOutputStream();try(var stream=ImageIO.createImageOutputStream(out)){writer.setOutput(stream);var param=writer.getDefaultWriteParam();param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);param.setCompressionQuality(quality);writer.write(null,new javax.imageio.IIOImage(image,null,null),param);}finally{writer.dispose();}return out.toByteArray();}
    public record ResourceContent(org.springframework.core.io.Resource resource,String mimeType,String fileName){}
}
