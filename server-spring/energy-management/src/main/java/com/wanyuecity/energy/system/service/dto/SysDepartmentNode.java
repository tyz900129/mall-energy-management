package com.wanyuecity.energy.system.service.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 组织架构树节点。 */
@Data
public class SysDepartmentNode {
    private Long id;
    private String code;
    private String name;
    private String deptGroup;
    private Long parentId;
    private String ancestors;
    private Integer sortOrder;
    private Integer depth;
    private String deptLevel;
    private String status;
    private Integer enabled;
    private Integer codeLocked;
    /** 直接挂在该部门下的人员数（不含下级）。 */
    private Long userCount;
    private List<SysDepartmentNode> children = new ArrayList<>();
}
