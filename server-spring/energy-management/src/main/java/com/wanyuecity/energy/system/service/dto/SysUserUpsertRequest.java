package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SysUserUpsertRequest {

    @NotBlank
    @Size(min = 3, max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名仅支持字母数字与 _ . -")
    private String username;

    @Size(max = 64)
    private String displayName;

    @Email
    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String phone;

    /** 1 = enabled, 0 = disabled. */
    private Integer status = 1;

    /** 仅创建时必填；编辑时留空表示不改密 */
    @Size(min = 6, max = 64)
    private String password;

    /** 仅创建时生效；编辑角色请用 /users/{id}/roles */
    private List<Long> roleIds;
}

