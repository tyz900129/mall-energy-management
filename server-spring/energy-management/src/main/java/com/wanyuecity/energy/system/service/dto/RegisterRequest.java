package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank @Pattern(regexp="^[A-Za-z0-9_.-]{3,64}$") private String username;
    @NotBlank @Size(min=6,max=64) private String password;
    @Size(max=64) private String displayName;
    @Size(max=32) private String phone;
}
