package com.wanyuecity.energy.system.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 角色默认权限：全量权限目录 + 该角色已授权码。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysRolePermissionView {
    private Long roleId;
    private String roleCode;
    private String roleName;
    private List<UserPermissionProfile.PermissionModule> modules;
    private List<String> permissionCodes;
}
