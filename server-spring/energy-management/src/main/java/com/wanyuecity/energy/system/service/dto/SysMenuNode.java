package com.wanyuecity.energy.system.service.dto;

import com.wanyuecity.energy.system.entity.SysMenu;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SysMenuNode {

    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private String path;
    private String icon;
    private String type;
    private Integer sortOrder;
    private Integer enabled;
    private List<SysMenuNode> children = new ArrayList<>();

    public static SysMenuNode from(SysMenu m) {
        SysMenuNode n = new SysMenuNode();
        n.id = m.getId();
        n.parentId = m.getParentId();
        n.code = m.getCode();
        n.name = m.getName();
        n.path = m.getPath();
        n.icon = m.getIcon();
        n.type = m.getType();
        n.sortOrder = m.getSortOrder();
        n.enabled = m.getEnabled();
        return n;
    }
}
