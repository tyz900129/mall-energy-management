package com.wanyuecity.energy.system.service.dto;

import lombok.Data;

import java.util.List;

@Data
public class SysRolePermissionAssignRequest {
    private List<String> permissionCodes;
}
