package com.wanyuecity.energy.complaint.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cs_complaint_dict")
public class CsComplaintDict {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** CATEGORY / CHANNEL / SATISFACTION / TAG / STATUS */
    private String dictType;
    private String itemValue;
    private String itemLabel;
    private String color;
    private Integer sortOrder;
    private Integer enabled;
}
