package com.wanyuecity.energy.complaint.service;

import com.wanyuecity.energy.complaint.entity.CsStaff;
import com.wanyuecity.energy.complaint.service.dto.ComplaintImportResult;
import com.wanyuecity.energy.complaint.service.dto.ComplaintQuery;
import com.wanyuecity.energy.complaint.service.dto.ComplaintRequest;
import com.wanyuecity.energy.complaint.service.dto.ComplaintStatsQuery;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.SysUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 客诉模板、历史导入、明细导出与报表导出。 */
@Service
@RequiredArgsConstructor
public class ComplaintExcelService {

    private static final String[] DETAIL_HEADERS = {"客诉编号", "发生日期", "品牌", "业态", "细分品类", "楼层", "客诉类别", "渠道",
            "案例内容", "解决方案", "公司承担费用", "商户承担费用", "无需承担费用", "现场解决", "处理天数", "满意度", "状态",
            "经办人", "责任商管", "创建时间"};
    private static final String[] IMPORT_HEADERS = {"客诉编号(可选)", "发生日期(YYYY-MM-DD)", "品牌/铺位名称", "业态", "楼层", "客诉类别",
            "渠道", "案例内容", "解决方案", "公司承担费用", "商户承担费用", "无需承担费用(是/否)", "现场解决(是/否)",
            "经办人", "责任商管", "满意度", "状态"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbc;
    private final ComplaintService complaintService;
    private final ComplaintStatsService statsService;
    private final ComplaintStaffService staffService;
    /** 导入错误明细缓存，token -> 错误行。 */
    private final Map<String, List<Map<String, Object>>> errorCache = new ConcurrentHashMap<>();

    // ================= 模板 =================

    public void template(HttpServletResponse response) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("客诉导入模板");
            Row header = sheet.createRow(0);
            CellStyle style = headerStyle(wb);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(IMPORT_HEADERS[i]);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, i == 7 ? 12000 : 5200);
            }
            writeRow(sheet, 1, "CS-202601-001", "2026-01-05", "星巴克", "餐饮", "L1", "服务", "服务台现场",
                    "顾客反映出餐慢", "已致歉并加急", "0", "200", "否", "是", "张三", "李四", "满意", "已关闭");
            writeNote(wb);
            download(wb, response, "客诉导入模板.xlsx");
        }
    }

    private void writeNote(Workbook wb) {
        Sheet note = wb.createSheet("填写说明");
        String[][] notes = {
                {"字段", "说明"},
                {"客诉编号", "留空则系统自动生成 CS-YYYYMM-NNN"},
                {"品牌/铺位名称", "优先匹配铺位的商户名称/铺位名称/铺位编码；匹配不到则作为非铺位品牌新增"},
                {"业态", "填写基础字典中的业态名称，如 餐饮、零售、超市、物业、自营主力"},
                {"楼层", "填写楼层字典名称，如 L1、B1；品牌匹配到铺位时自动带出"},
                {"客诉类别", "服务 / 员工技能 / 不满意退换货 / 会员退费 / 价格 / 品质 / 安全 / 异物 / 标示 / 舆论 / 员工工资纠纷 / 其他"},
                {"渠道", "服务台现场 / 电话 / 小程序后台 / 市场监管转办 / 其他"},
                {"经办人/责任商管", "填写人员名单中的姓名，多人用顿号、逗号或空格分隔；名单中没有的姓名会自动新增"},
                {"满意度", "满意 / 一般 / 不满意"},
                {"状态", "待处理 / 处理中 / 已解决 / 已关闭（留空默认待处理）"}
        };
        CellStyle style = headerStyle(wb);
        for (int i = 0; i < notes.length; i++) {
            Row row = note.createRow(i);
            for (int j = 0; j < notes[i].length; j++) {
                Cell cell = row.createCell(j);
                cell.setCellValue(notes[i][j]);
                if (i == 0) cell.setCellStyle(style);
            }
        }
        note.setColumnWidth(0, 4200);
        note.setColumnWidth(1, 16000);
    }

    // ================= 导入 =================

    public ComplaintImportResult importFile(MultipartFile file, SysUser operator) {
        if (file == null || file.isEmpty()) throw BizException.badRequest("请选择 Excel 文件");
        List<Map<String, Object>> errors = new ArrayList<>();
        int success = 0;
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String brand = text(row, 2, fmt);
                if (brand.isBlank() && text(row, 7, fmt).isBlank()) continue;
                try {
                    importRow(row, fmt, operator);
                    success++;
                } catch (Exception ex) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("row", i + 1);
                    error.put("brand", brand);
                    error.put("reason", rootMessage(ex));
                    errors.add(error);
                }
            }
        } catch (IOException ex) {
            throw BizException.badRequest("Excel 读取失败：" + rootMessage(ex));
        }
        ComplaintImportResult result = new ComplaintImportResult();
        result.setSuccessCount(success);
        result.setFailCount(errors.size());
        List<Map<String, Object>> capped = errors.size() > 100 ? errors.subList(0, 100) : errors;
        result.setErrors(capped);
        if (!errors.isEmpty()) {
            String token = UUID.randomUUID().toString().replace("-", "");
            errorCache.put(token, errors);
            if (errorCache.size() > 50) {
                Iterator<String> keys = errorCache.keySet().iterator();
                if (keys.hasNext()) errorCache.remove(keys.next());
            }
            result.setErrorToken(token);
        }
        return result;
    }

    private void importRow(Row row, DataFormatter fmt, SysUser operator) {
        String complaintNo = text(row, 0, fmt);
        LocalDate occurDate = date(row.getCell(1), fmt);
        if (occurDate == null) throw BizException.badRequest("发生日期不能为空");
        String brandName = text(row, 2, fmt);
        if (brandName.isBlank()) throw BizException.badRequest("品牌/铺位名称不能为空");
        String category = dictValue("CATEGORY", text(row, 5, fmt));
        if (category == null) throw BizException.badRequest("客诉类别不能为空或无效");
        String businessType = businessTypeValue(text(row, 3, fmt));
        String floor = floorValue(text(row, 4, fmt));
        String channel = dictValue("CHANNEL", text(row, 6, fmt));
        String content = text(row, 7, fmt);
        if (content.isBlank()) throw BizException.badRequest("案例内容不能为空");
        String solution = text(row, 8, fmt);

        ComplaintRequest req = new ComplaintRequest();
        req.setOccurDate(occurDate);
        req.setCategory(category);
        req.setChannel(channel);
        req.setContent(content);
        req.setSolution(solution.isBlank() ? null : solution);
        req.setCompanyCost(money(row, 9, fmt));
        req.setMerchantCost(money(row, 10, fmt));
        req.setNoCost(flag(text(row, 11, fmt)));
        req.setOnSiteHandle(flag(text(row, 12, fmt)));
        req.setHandlerStaffIds(staffIds(text(row, 13, fmt), CsStaff.TYPE_HANDLER));
        req.setManagerStaffIds(staffIds(text(row, 14, fmt), CsStaff.TYPE_MANAGER));
        String satisfaction = dictValue("SATISFACTION", text(row, 15, fmt));
        req.setSatisfaction(satisfaction);
        String status = text(row, 16, fmt);

        Map<String, Object> stall = findStall(brandName);
        if (stall != null) {
            req.setStallId(((Number) stall.get("stallId")).longValue());
            req.setBrandName(brandName);
            if (businessType == null) businessType = (String) stall.get("businessType");
            if (floor == null) floor = (String) stall.get("floorCode");
            req.setBusinessCategory((String) stall.get("businessCategory"));
            Object merchantId = stall.get("merchantId");
            if (merchantId instanceof Number n) req.setMerchantId(n.longValue());
            req.setMerchantName((String) stall.get("merchantName"));
        } else {
            Map<String, Object> manual = findBrand(brandName);
            if (manual != null) {
                req.setBrandId(((Number) manual.get("brandId")).longValue());
                req.setBrandName(brandName);
                if (businessType == null) businessType = (String) manual.get("businessType");
                if (floor == null) floor = (String) manual.get("floorCode");
            } else {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("name", brandName);
                body.put("businessType", businessType);
                body.put("floorCode", floor);
                body.put("remark", "Excel 导入自动创建");
                Map<String, Object> created = createManualBrand(body);
                req.setBrandId(((Number) created.get("brandId")).longValue());
                req.setBrandName(brandName);
            }
        }
        req.setBusinessType(businessType);
        req.setFloorCode(floor);
        if (req.getBusinessType() == null) throw BizException.badRequest("业态不能为空（铺位未匹配时需手工填写）");
        complaintService.createImported(req, complaintNo, status, satisfaction, operator);
    }

    public void exportErrors(String token, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> errors = token == null ? null : errorCache.get(token);
        if (errors == null) throw BizException.notFound("错误明细已失效，请重新导入");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("导入错误明细");
            String[] headers = {"Excel 行号", "品牌/铺位", "错误原因"};
            Row header = sheet.createRow(0);
            CellStyle style = headerStyle(wb);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, i == 2 ? 12000 : 5200);
            }
            int index = 1;
            for (Map<String, Object> error : errors) {
                Row row = sheet.createRow(index++);
                set(row, 0, String.valueOf(error.get("row")));
                set(row, 1, String.valueOf(error.get("brand")));
                set(row, 2, String.valueOf(error.get("reason")));
            }
            download(wb, response, "客诉导入错误明细.xlsx");
        }
    }

    // ================= 导出 =================

    public void export(ComplaintQuery q, Long scopeUserId, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = complaintService.listAll(q, scopeUserId);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("客诉明细");
            CellStyle style = headerStyle(wb);
            Row header = sheet.createRow(0);
            for (int i = 0; i < DETAIL_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(DETAIL_HEADERS[i]);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, i == 8 || i == 9 ? 12000 : 4600);
            }
            int index = 1;
            for (Map<String, Object> row : rows) {
                Row excelRow = sheet.createRow(index++);
                set(excelRow, 0, row.get("complaintNo"));
                set(excelRow, 1, row.get("occurDate"));
                set(excelRow, 2, row.get("brandName"));
                set(excelRow, 3, row.get("businessTypeLabel"));
                set(excelRow, 4, row.get("businessCategory"));
                set(excelRow, 5, row.get("floorLabel"));
                set(excelRow, 6, row.get("categoryLabel"));
                set(excelRow, 7, row.get("channelLabel"));
                set(excelRow, 8, row.get("content"));
                set(excelRow, 9, row.get("solution"));
                number(excelRow, 10, row.get("companyCost"));
                number(excelRow, 11, row.get("merchantCost"));
                set(excelRow, 12, bool(row.get("noCost")) ? "是" : "否");
                set(excelRow, 13, bool(row.get("onSiteHandle")) ? "是" : "否");
                set(excelRow, 14, row.get("handleDays"));
                set(excelRow, 15, row.get("satisfactionLabel"));
                set(excelRow, 16, row.get("statusLabel"));
                set(excelRow, 17, row.get("handlerNames"));
                set(excelRow, 18, row.get("managerNames"));
                set(excelRow, 19, row.get("createdAt"));
            }
            sheet.createFreezePane(0, 1);
            download(wb, response, "客诉明细.xlsx");
        }
    }

    public void exportReport(ComplaintStatsQuery q, HttpServletResponse response) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            writeTable(wb.createSheet("总览"), new String[]{"指标", "数值"},
                    overviewRows(statsService.overview(q)), new String[]{"key", "value"});

            String[] distHeaders = {"维度", "数量", "总费用", "公司费用", "商户费用", "平均处理天数"};
            String[] distKeys = {"key", "value", "amount", "companyCost", "merchantCost", "avgHandleDays"};
            for (String[] dim : new String[][]{{"category", "类别"}, {"businessType", "业态"}, {"floor", "楼层"},
                    {"status", "状态"}, {"channel", "渠道"}, {"satisfaction", "满意度"}, {"brand", "品牌"}}) {
                q.setDimension(dim[0]);
                writeTable(wb.createSheet(dim[1] + "分布"), distHeaders, statsService.distribution(q), distKeys);
            }
            q.setGranularity("month");
            writeTable(wb.createSheet("月度趋势"), new String[]{"月份", "数量", "总费用"},
                    statsService.trend(q), new String[]{"key", "value", "amount"});
            Map<String, Object> efficiency = statsService.efficiency(q);
            writeTable(wb.createSheet("时效分布"), new String[]{"处理天数", "数量"},
                    list(efficiency.get("buckets")), new String[]{"bucket", "value"});
            Map<String, Object> cost = statsService.cost(q);
            writeTable(wb.createSheet("费用分布"), new String[]{"费用区间", "数量"},
                    list(cost.get("buckets")), new String[]{"bucket", "value"});
            q.setRankingDimension("brand");
            writeTable(wb.createSheet("品牌排名"), new String[]{"品牌", "数量", "总费用"},
                    statsService.ranking(q), new String[]{"key", "count", "cost"});
            q.setRankingDimension("manager");
            writeTable(wb.createSheet("责任商管排名"), new String[]{"责任商管", "数量", "总费用"},
                    statsService.ranking(q), new String[]{"key", "count", "cost"});
            q.setRankingDimension("handler");
            writeTable(wb.createSheet("经办人排名"), new String[]{"经办人", "数量", "总费用"},
                    statsService.ranking(q), new String[]{"key", "count", "cost"});
            download(wb, response, "客诉数据报表.xlsx");
        }
    }

    private List<Map<String, Object>> overviewRows(Map<String, Object> data) {
        String[][] labels = {{"total", "客诉总数"}, {"brandCount", "涉及品牌数"}, {"totalCost", "总费用"},
                {"companyCost", "公司承担"}, {"merchantCost", "商户承担"}, {"onSiteRate", "现场解决率(%)"},
                {"satisfactionRate", "满意率(%)"}, {"avgHandleDays", "平均处理天数"}, {"closedCount", "已关闭"},
                {"openingCount", "未结案"}, {"satisfiedCount", "满意数"}};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String[] item : labels) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", item[1]);
            row.put("value", data.get(item[0]) == null ? "-" : data.get(item[0]));
            rows.add(row);
        }
        return rows;
    }

    // ================= 工具 =================

    private void writeTable(Sheet sheet, String[] headers, List<Map<String, Object>> rows, String[] keys) {
        CellStyle style = headerStyle(sheet.getWorkbook());
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
            sheet.setColumnWidth(i, 6000);
        }
        int index = 1;
        for (Map<String, Object> row : rows) {
            Row excelRow = sheet.createRow(index++);
            for (int i = 0; i < keys.length; i++) {
                Object value = row.get(keys[i]);
                if (value instanceof Number n) {
                    excelRow.createCell(i).setCellValue(n.doubleValue());
                } else {
                    set(excelRow, i, value);
                }
            }
        }
        sheet.createFreezePane(0, 1);
    }

    private void download(Workbook wb, HttpServletResponse response, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        wb.write(response.getOutputStream());
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeRow(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) set(row, i, values[i]);
    }

    private Map<String, Object> findStall(String brandName) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id stallId, s.current_merchant_id merchantId, s.business_type businessType,
                       s.business_category businessCategory, s.floor_code floorCode, m.merchant_name merchantName
                FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id
                WHERE s.status<>'DECOMMISSIONED' AND (s.stall_name=? OR m.merchant_name=? OR s.stall_code=?)
                LIMIT 1""", brandName, brandName, brandName);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findBrand(String name) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id brandId,business_type businessType,floor_code floorCode "
                + "FROM cs_brand WHERE name=? AND active=1 LIMIT 1", name);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> createManualBrand(Map<String, Object> body) {
        String name = (String) body.get("name");
        String businessType = (String) body.get("businessType");
        String floorCode = (String) body.get("floorCode");
        String remark = (String) body.get("remark");
        jdbc.update("INSERT INTO cs_brand(name,business_type,floor_code,source,active,remark) VALUES(?,?,?,'MANUAL',1,?)",
                name, businessType, floorCode, remark);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brandId", jdbc.queryForObject("SELECT id FROM cs_brand WHERE name=? ORDER BY id DESC LIMIT 1", Long.class, name));
        result.put("brandName", name);
        result.put("businessType", businessType);
        result.put("floorCode", floorCode);
        return result;
    }

    private String dictValue(String type, String raw) {
        if (raw == null || raw.isBlank()) return null;
        List<String> values = jdbc.queryForList("SELECT item_value FROM cs_complaint_dict WHERE dict_type=? AND enabled=1 "
                + "AND (item_value=? OR item_label=?) LIMIT 1", String.class, type, raw.trim(), raw.trim());
        return values.isEmpty() ? null : values.get(0);
    }

    private String businessTypeValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        List<String> values = jdbc.queryForList("SELECT item_value FROM business_dictionary WHERE dict_type='BUSINESS_TYPE' "
                + "AND status=1 AND (item_value=? OR item_label=?) LIMIT 1", String.class, raw.trim(), raw.trim());
        return values.isEmpty() ? null : values.get(0);
    }

    private String floorValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        List<String> values = jdbc.queryForList("SELECT item_value FROM business_dictionary WHERE dict_type='FLOOR' "
                + "AND status=1 AND (item_value=? OR item_label=?) LIMIT 1", String.class, raw.trim(), raw.trim());
        return values.isEmpty() ? null : values.get(0);
    }

    /** 姓名直接落名单，名单中不存在则自动新增，历史数据无需先建账号。 */
    private List<Long> staffIds(String raw, String type) {
        if (raw == null || raw.isBlank()) return null;
        List<Long> ids = new ArrayList<>();
        for (String part : raw.split("[,，、;；/]")) {
            if (part.isBlank()) continue;
            Long id = staffService.ensureByName(part.trim(), type);
            if (id != null && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private BigDecimal money(Row row, int index, DataFormatter fmt) {
        String value = text(row, index, fmt);
        if (value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.replace(",", "").replace("￥", "").replace("元", ""));
        } catch (NumberFormatException ex) {
            throw BizException.badRequest("费用格式不正确: " + value);
        }
    }

    private Boolean flag(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.equals("是") || value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("Y");
    }

    private LocalDate date(Cell cell, DataFormatter fmt) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = fmt.formatCellValue(cell).trim();
        if (value.isBlank()) return null;
        for (String pattern : new String[]{"yyyy-MM-dd", "yyyy/M/d", "yyyy.MM.dd", "yyyy年M月d日", "yyyyMMdd"}) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw BizException.badRequest("发生日期格式不正确: " + value);
        }
    }

    private static void set(Row row, int index, Object value) {
        String text;
        if (value == null) text = "";
        else if (value instanceof LocalDate d) text = DATE.format(d);
        else if (value instanceof LocalDateTime t) text = t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        else text = String.valueOf(value);
        row.createCell(index).setCellValue(text);
    }

    private static void number(Row row, int index, Object value) {
        if (value instanceof Number n) row.createCell(index).setCellValue(n.doubleValue());
        else row.createCell(index).setCellValue(0);
    }

    private static String text(Row row, int index, DataFormatter fmt) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : fmt.formatCellValue(cell).trim();
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() == 1;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    private static String rootMessage(Throwable e) {
        Throwable x = e;
        while (x.getCause() != null) x = x.getCause();
        return x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage();
    }
}
