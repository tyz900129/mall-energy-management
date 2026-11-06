package com.wanyuecity.energy.qrinspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 二维码巡检模板（场景 × 角色组 × 部门 维度） */
@Data
@TableName("qr_inspection_template")
public class QrInspectionTemplate {
    @TableId(type = IdType.AUTO) private Long id;
    /** WAREHOUSE_COMMERCIAL / WAREHOUSE_PROPERTY / JOINT */
    private String scene;
    private String name;
    /** COMMERCIAL / PROPERTY / null(联合巡检共用) */
    private String roleGroup;
    /** 进一步按部门细分时填，共用模板可为空 */
    private String deptCode;
    private Integer version;
    /** [{code,label,controlType,options,normalValue,requirePhotoOnAbnormal,required,enabled,sortOrder,category,responsibleDeptCode}] */
    private String fieldsJson;
    private Integer enabled;
    private String remark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
