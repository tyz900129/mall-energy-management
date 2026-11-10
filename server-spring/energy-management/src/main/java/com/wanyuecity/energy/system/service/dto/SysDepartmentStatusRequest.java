package com.wanyuecity.energy.system.service.dto;

import lombok.Data;

/** 部门启停请求：ACTIVE / DISABLED。 */
@Data
public class SysDepartmentStatusRequest {
    private String status;
}
