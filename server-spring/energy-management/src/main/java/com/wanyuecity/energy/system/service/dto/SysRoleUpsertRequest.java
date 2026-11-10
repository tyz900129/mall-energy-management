package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SysRoleUpsertRequest {

    @NotBlank
    @Size(min = 2, max = 64)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色编码必须为大写字母/数字/下划线，且以字母开头")
    private String code;

    @NotBlank
    @Size(max = 64)
    private String name;

    @Size(max = 255)
    private String description;

    /** 岗位所属部门，空=全局岗位 */
    private Long deptId;

    /** 岗位类别：MANAGE/TECH/OPER/ADMIN */
    @Pattern(regexp = "MANAGE|TECH|OPER|ADMIN", message = "岗位类别只能是 MANAGE/TECH/OPER/ADMIN")
    private String postCategory;

    /** 是否允许在用户管理中分配 */
    private Integer isAssignable;

    @Min(value = 0, message = "编制数不能为负数")
    private Integer headcount;
}
