package com.wanyuecity.energy.complaint.service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 客诉登记/编辑请求。 */
@Data
public class ComplaintRequest {

    private LocalDate occurDate;

    private Long stallId;
    private Long brandId;
    private String brandName;

    private Long merchantId;
    private String merchantName;

    private String businessType;
    private String businessCategory;
    private String floorCode;

    private String category;
    private String channel;
    private String content;
    private String solution;

    private BigDecimal companyCost;
    private BigDecimal merchantCost;
    private Boolean noCost;
    private Boolean onSiteHandle;

    private String satisfaction;
    private String status;

    /** 经办人名单 ID（可多选）。为 null 表示编辑时不改动。 */
    private List<Long> handlerStaffIds;
    /** 责任商管名单 ID（可多选）。为 null 表示编辑时不改动，空数组表示清空。 */
    private List<Long> managerStaffIds;
}
