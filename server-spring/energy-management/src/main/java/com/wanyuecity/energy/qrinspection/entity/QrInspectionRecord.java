package com.wanyuecity.energy.qrinspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 巡检记录 */
@Data
@TableName("qr_inspection_record")
public class QrInspectionRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private String scene;
    private Long planId;
    private Long planRoundId;
    private Long taskId;
    private Long stallId;
    private String stallCode;
    private String stallName;
    private Long templateId;
    private Integer templateVersion;
    /** 提交时的表单结构快照，后续模板修改不影响历史记录 */
    private String templateSnapshot;
    private String roleGroup;
    private Long submitterId;
    private String submitterName;
    private String submitterPhone;
    private String submitterDept;
    private String deptCode;
    /** NORMAL / ABNORMAL */
    private String status;
    private String answersJson;
    private String photosJson;
    private LocalDateTime submittedAt;
}
