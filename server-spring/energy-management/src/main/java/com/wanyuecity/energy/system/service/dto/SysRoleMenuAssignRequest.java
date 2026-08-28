package com.wanyuecity.energy.system.service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SysRoleMenuAssignRequest {

    @NotNull
    private List<Long> menuIds;
}

