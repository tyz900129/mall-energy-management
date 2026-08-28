package com.wanyuecity.energy.verification.service.dto;
import com.wanyuecity.energy.verification.entity.FieldVerificationDevice; import lombok.Data; import java.time.LocalDateTime; import java.util.List;
@Data public class VerificationTaskView {
 private Long id; private String taskNo; private Long workflowId; private String workflowNo; private String workflowType; private String merchantCode; private String merchantName;
 private String stallCode; private String stallName; private String status; private String accessToken; private LocalDateTime openedAt; private LocalDateTime startedAt; private LocalDateTime completedAt;
 private String verificationResult; private String remark; private List<FieldVerificationDevice> devices;
}
