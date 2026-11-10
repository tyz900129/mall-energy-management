package com.wanyuecity.energy.system.service.dto;

import lombok.Data;

/** 部门新增/编辑请求。编辑时 parentId 不生效，移动走单独接口。 */
@Data
public class SysDepartmentUpsertRequest {
    private String code;
    private String name;
    private Long parentId;
    private String deptGroup;
    private Integer sortOrder;
}
