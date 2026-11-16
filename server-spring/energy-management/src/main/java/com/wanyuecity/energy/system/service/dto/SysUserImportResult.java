package com.wanyuecity.energy.system.service.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 用户 Excel 批量导入结果。dryRun=true 时仅预览，不写库。 */
@Data
public class SysUserImportResult {

    private boolean dryRun;
    private int totalRows;
    private int createCount;
    private int updateCount;
    private int skipCount;
    private int failCount;
    /** 逐行预览：row/username/displayName/action/roleCode/departmentName/note */
    private List<Map<String, Object>> preview;
    /** 失败明细（响应最多 100 条，完整明细可通过 errorToken 下载）。 */
    private List<Map<String, Object>> errors;
    /** 非阻断提示，例如部门未匹配已置空。 */
    private List<Map<String, Object>> warnings;
    /** 错误明细文件标识。 */
    private String errorToken;
    /** 初始密码清单标识，仅正式导入创建用户后返回。 */
    private String credentialsToken;
}
