package com.wanyuecity.energy.verification.service.dto; import lombok.Data; import java.time.LocalDateTime;
@Data public class AttachmentView {private Long id;private Long deviceId;private String fileName;private Long fileSize;private String mimeType;private String sha256;private String photoType;private LocalDateTime uploadedAt;}

