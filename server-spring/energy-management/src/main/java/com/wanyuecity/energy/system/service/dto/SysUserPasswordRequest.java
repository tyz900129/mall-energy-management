package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SysUserPasswordRequest {

    /** 编辑他人时使用 */
    @Size(min = 6, max = 64)
    private String newPassword;

    /** 修改自己密码时使用 */
    private String oldPassword;
}
