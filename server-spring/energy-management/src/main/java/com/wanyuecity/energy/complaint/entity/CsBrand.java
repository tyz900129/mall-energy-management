package com.wanyuecity.energy.complaint.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 非铺位品牌补充：超市品类、物业、自营主力等。 */
@Data
@TableName("cs_brand")
public class CsBrand {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String businessType;
    private String floorCode;

    /** MANUAL = 手工新增。 */
    private String source;

    private Integer active;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
