package com.wanyuecity.energy.system.service.dto;

import com.wanyuecity.energy.system.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SysUserView {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> roles;
    private List<Long> roleIds;

    public static SysUserView from(SysUser u, List<String> roles, List<Long> roleIds) {
        return new SysUserView(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                u.getEmail(),
                u.getPhone(),
                u.getStatus(),
                u.getLastLoginAt(),
                u.getCreatedAt(),
                u.getUpdatedAt(),
                roles,
                roleIds);
    }
}

