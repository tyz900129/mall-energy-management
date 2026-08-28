package com.wanyuecity.energy.workflow.service.dto;
import com.wanyuecity.energy.workflow.entity.WorkflowDevice;
import com.wanyuecity.energy.workflow.entity.WorkflowExpectedChange;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data public class WorkflowView {
    private Long id; private String workflowNo; private String workflowType;
    private String merchantCode; private String merchantName; private String contactName; private String contactPhone;
    private Long stallId; private String stallCode; private String stallName; private String floorCode; private String floorName; private String stallBusinessType; private String stallBusinessCategory;
    private String targetAccountNumber; private LocalDateTime requestedBillingStartTime;
    private String billingAccountName;
    private String applicantName; private String status; private LocalDateTime submittedAt;
    private String expectedAccountAction; private String remark; private String oaProcessNo; private String completionRemark; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    private List<WorkflowDevice> devices; private List<WorkflowExpectedChange> expectedChanges;
}

