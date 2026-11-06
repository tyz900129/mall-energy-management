package com.wanyuecity.energy.complaint.service.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** Excel 导入结果。 */
@Data
public class ComplaintImportResult {

    private int successCount;
    private int failCount;
    /** 错误明细文件标识，可用于下载错误行。 */
    private String errorToken;
    private List<Map<String, Object>> errors;
}
