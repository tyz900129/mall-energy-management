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

    public static SysRoleView from(SysRole r, List<Long> menuIds) {
        return new SysRoleView(r.getId(), r.getCode(), r.getName(), r.getDescription(), menuIds);
    }
}
