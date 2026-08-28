package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SysMenuUpsertRequest {

    private Long parentId = 0L;

    @NotBlank
    @Size(max = 128)
    private String code;

    @NotBlank
    @Size(max = 64)
    private String name;

    @Size(max = 255)
    private String path;

    @Size(max = 64)
    private String icon;

    @NotBlank
    @Size(max = 16)
    /** MENU / BUTTON */
    private String type;

    private Integer sortOrder = 0;

    private Integer enabled = 1;
}
