package com.wanyuecity.energy.complaint.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_complaint_log")
public class CsComplaintLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long complaintId;
    /** create/update/status/close/import/delete */
    private String action;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private String note;
    private LocalDateTime createdAt;
}
