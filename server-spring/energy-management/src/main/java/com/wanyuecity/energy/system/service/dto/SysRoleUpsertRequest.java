package com.wanyuecity.energy.system.service.dto;

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
}

