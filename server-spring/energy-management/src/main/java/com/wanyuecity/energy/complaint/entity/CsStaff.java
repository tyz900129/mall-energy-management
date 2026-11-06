package com.wanyuecity.energy.complaint.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cs_staff")
public class CsStaff {

    public static final String TYPE_HANDLER = "HANDLER";
    public static final String TYPE_MANAGER = "MANAGER";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String staffType;
    private String staffName;
    private String department;
    private String phone;
    private Long sourceUserId;
    private String status;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
