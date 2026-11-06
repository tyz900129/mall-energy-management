package com.wanyuecity.energy.complaint.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_complaint_participant")
public class CsComplaintParticipant {

    public static final String HANDLER = "HANDLER";
    public static final String MANAGER = "MANAGER";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long complaintId;
    /** 名单来源，离职或账号注销后仍保留。 */
    private Long staffId;
    /** 可选账号关联，仅用于自动带出与权限范围，不参与展示。 */
    private Long userId;
    /** 姓名快照，历史数据与离职人员仍可正常展示。 */
    private String userName;
    /** HANDLER = 经办人 / MANAGER = 责任商管。 */
    private String participantType;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
