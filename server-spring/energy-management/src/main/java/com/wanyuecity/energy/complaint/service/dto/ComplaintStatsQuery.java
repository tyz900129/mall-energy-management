package com.wanyuecity.energy.complaint.service.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 报表筛选条件。 */
@Data
public class ComplaintStatsQuery {

    private String businessType;
    private String floor;
    private String category;
    private String status;
    private String satisfaction;
    private String channel;
    private String brandName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;

    private String dimension;
    private String dim1;
    private String dim2;
    private String metric;
    private String granularity;
    private String rankingDimension;
    private Integer limit;
}
