package com.wanyuecity.energy.system.service.dto;

import lombok.Data;

/** 部门移动请求；parentId 为 null 表示移到根（部）。 */
@Data
public class SysDepartmentMoveRequest {
    private Long parentId;
}
