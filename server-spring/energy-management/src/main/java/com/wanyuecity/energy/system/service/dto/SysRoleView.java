package com.wanyuecity.energy.system.service.dto;

import com.wanyuecity.energy.system.entity.SysRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysRoleView {

    private Long id;
    private String code;
    private String name;
    private String description;
    private List<Long> menuIds;
    private Integer userCount;
    private Integer permissionCount;

    public static SysRoleView from(SysRole r, List<Long> menuIds) {
        SysRoleView v = new SysRoleView(r.getId(), r.getCode(), r.getName(), r.getDescription(), menuIds, null, null);
        v.setUserCount(0);
        v.setPermissionCount(0);
        return v;
    }

    public static SysRoleView from(SysRole r, List<Long> menuIds, Integer userCount, Integer permissionCount) {
        return new SysRoleView(r.getId(), r.getCode(), r.getName(), r.getDescription(), menuIds, userCount, permissionCount);
    }
}
