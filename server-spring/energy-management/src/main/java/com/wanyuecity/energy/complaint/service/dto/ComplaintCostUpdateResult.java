package com.wanyuecity.energy.complaint.service.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 客诉费用更新导入结果，干跑与正式执行共用。 */
@Data
public class ComplaintCostUpdateResult {

    /** true 表示仅预演，未写入任何数据。 */
    private boolean dryRun;
    /** true 表示本次放开已解决/已关闭单据的费用修正（仅费用三列）。 */
    private boolean allowClosed;
    /** 有效数据行数（已跳过整行空白）。 */
    private int totalRows;
    /** 命中单据的行数，含被状态保护的失败行。 */
    private int matchedCount;
    /** 未命中单据的行数。 */
    private int unmatchedCount;
    /** 费用确有变化的行数。 */
    private int changedCount;
    /** 命中但费用无变化的行数。 */
    private int unchangedCount;
    /** 正式执行成功行数；干跑恒为 0。 */
    private int successCount;
    /** 失败行数（未命中、状态保护、格式错误等）。 */
    private int failCount;
    /** 错误明细文件标识，用于复用现有错误下载接口。 */
    private String errorToken;
    private List<Map<String, Object>> errors;
    /** 变更预览：行号、编号、品牌、改前改后与归零说明。 */
    private List<Map<String, Object>> preview;
}
