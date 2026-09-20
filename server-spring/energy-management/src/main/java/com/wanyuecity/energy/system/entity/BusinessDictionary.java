package com.wanyuecity.energy.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("business_dictionary")
public class BusinessDictionary {
    @TableId(type = IdType.AUTO) private Long id;
    private String dictType;
    private String itemValue;
    private String itemLabel;
    private Integer sortOrder;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String remark;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
