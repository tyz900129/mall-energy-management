package com.wanyuecity.energy.complaint.service.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 客诉列表 / 导出 / 报表通用筛选条件。 */
@Data
public class ComplaintQuery {

    private String keyword;
    private String businessType;
    private String floor;
    private String category;
    private String status;
    private String satisfaction;
    private String channel;
    private Long stallId;
    private Long brandId;
    private Long managerStaffId;
    private Long handlerStaffId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate occurDateStart;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate occurDateEnd;

    private BigDecimal costMin;
    private BigDecimal costMax;

    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
