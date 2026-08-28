package com.wanyuecity.energy.verification.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("attachment") public class Attachment {
 @TableId(type=IdType.AUTO) private Long id; private String businessType; private Long businessId; private String fileType; private String fileName;
 private String storageProvider; private String storagePath; private Long fileSize; private String mimeType; private String sha256; private String photoType;
 private String uploadedBy; private LocalDateTime uploadedAt; private LocalDateTime capturedAt; private String remark;
}
