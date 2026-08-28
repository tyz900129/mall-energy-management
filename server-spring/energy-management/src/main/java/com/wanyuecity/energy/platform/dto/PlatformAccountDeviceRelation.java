package com.wanyuecity.energy.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Binding between a platform account and a platform device. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccountDeviceRelation {
    private String accountNumber;
    private String platformDeviceId;
}

