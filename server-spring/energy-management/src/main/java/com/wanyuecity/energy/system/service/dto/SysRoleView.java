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
    private Long deptId;
    private String deptName;
    private String postCategory;
    private Integer isAssignable;
    private Integer isSystem;
    private Integer headcount;
    private List<Long> menuIds;
    private Integer userCount;
    private Integer permissionCount;

    public static SysRoleView from(SysRole r, String deptName, List<Long> menuIds,
                                   Integer userCount, Integer permissionCount) {
        SysRoleView v = new SysRoleView();
        v.setId(r.getId());
        v.setCode(r.getCode());
        v.setName(r.getName());
        v.setDescription(r.getDescription());
        v.setDeptId(r.getDeptId());
        v.setDeptName(deptName);
        v.setPostCategory(r.getPostCategory());
        v.setIsAssignable(r.getIsAssignable() == null ? 1 : r.getIsAssignable());
        v.setIsSystem(r.getIsSystem() == null ? 0 : r.getIsSystem());
        v.setHeadcount(r.getHeadcount() == null ? 0 : r.getHeadcount());
        v.setMenuIds(menuIds);
        v.setUserCount(userCount);
        v.setPermissionCount(permissionCount);
        return v;
    }
}
