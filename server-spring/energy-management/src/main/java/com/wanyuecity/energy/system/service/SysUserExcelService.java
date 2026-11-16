package com.wanyuecity.energy.system.service;

import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.service.dto.SysUserImportResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** 用户批量导入 / 导出、错误明细与初始密码清单。 */
@Service
@RequiredArgsConstructor
public class SysUserExcelService {

    private static final int MAX_ROWS = 1000;
    private static final int MAX_RESPONSE_ERRORS = 100;
    private static final int MAX_CACHE_ENTRIES = 50;
    private static final String DEFAULT_PASSWORD = "Wy@123456";

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,64}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> REQUIRED_COLUMNS = List.of("username", "displayName", "roleCode");
    private static final Map<String, String> REQUIRED_LABELS = Map.of(
            "username", "用户名", "displayName", "姓名", "roleCode", "角色编码");
    private static final Map<String, String> HEADER_ALIASES = headerAliases();

    private final JdbcTemplate jdbc;
    private final SysUserService userService;
    /** 导入错误明细缓存：token -> 错误行。 */
    private final Map<String, List<Map<String, Object>>> errorCache = new ConcurrentHashMap<>();
    /** 初始密码清单缓存：token -> 新建用户初始密码，一次性下载，不落库、不写日志。 */
    private final Map<String, List<Map<String, Object>>> credentialCache = new ConcurrentHashMap<>();

    // ================= 模板 =================

    public void template(HttpServletResponse response) throws IOException {
        String[] headers = {"用户名*", "姓名*", "初始密码", "手机号", "邮箱", "部门名称", "角色编码*", "状态", "是否更新已有用户"};
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用户导入模板");
            CellStyle style = headerStyle(wb);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, 5200);
            }
            writeRow(sheet, 1, "zhangsan", "张三", "", "13800001234", "zhangsan@example.com", "商管一处", "VIEWER", "启用", "否");
            sheet.createFreezePane(0, 1);

            Sheet note = wb.createSheet("填写说明");
            String[][] notes = {
                    {"字段", "说明"},
                    {"用户名", "必填，3-64 位字母、数字或 _ . -；已存在的用户名默认跳过"},
                    {"姓名", "必填，最长 64 字"},
                    {"初始密码", "选填，6-64 位；留空时优先使用手机号末 6 位数字，手机号不可用则使用统一默认密码"},
                    {"手机号", "选填；初始密码留空时取其末 6 位数字作为密码"},
                    {"邮箱", "选填，需符合邮箱格式"},
                    {"部门名称", "选填，需与系统部门名称完全一致；未匹配时置空并提示，不中断导入"},
                    {"角色编码", "必填，须为「允许分配」的角色编码；不支持 SUPER_ADMIN"},
                    {"状态", "选填，启用/禁用，留空默认启用"},
                    {"是否更新已有用户", "选填，是/否，默认否；为否时已存在的用户整行跳过（含角色）"},
                    {"限制", "单次最多 1000 行；同一文件内用户名不能重复；内置 admin 不允许通过导入修改"}
            };
            CellStyle noteHeader = headerStyle(wb);
            for (int r = 0; r < notes.length; r++) {
                Row row = note.createRow(r);
                for (int c = 0; c < notes[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(notes[r][c]);
                    if (r == 0) cell.setCellStyle(noteHeader);
                }
            }
            note.setColumnWidth(0, 4200);
            note.setColumnWidth(1, 16000);
            download(wb, response, "用户导入模板.xlsx");
        }
    }

    // ================= 导入 =================

    public SysUserImportResult importUsers(MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) throw BizException.badRequest("请选择 Excel 文件");
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        List<Map<String, Object>> preview = new ArrayList<>();
        List<Map<String, Object>> credentials = new ArrayList<>();
        int total = 0, create = 0, update = 0, skip = 0;
        Set<String> seenUsernames = new HashSet<>();
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            HeaderLayout layout = locateHeader(sheet, fmt);
            int dataRows = 0;
            for (int i = layout.headerRow() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                if (cellText(row, layout.column("username"), fmt).isBlank()
                        && cellText(row, layout.column("displayName"), fmt).isBlank()) continue;
                dataRows++;
            }
            if (dataRows > MAX_ROWS) {
                throw BizException.badRequest("单次导入最多 " + MAX_ROWS + " 行，请拆分文件后重试");
            }
            for (int i = layout.headerRow() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String username = cellText(row, layout.column("username"), fmt);
                String displayName = cellText(row, layout.column("displayName"), fmt);
                if (username.isBlank() && displayName.isBlank()) continue;
                total++;
                int excelRow = i + 1;
                try {
                    if (username.isBlank()) throw BizException.badRequest("用户名不能为空");
                    if (!USERNAME_PATTERN.matcher(username).matches()) {
                        throw BizException.badRequest("用户名需为 3-64 位字母、数字或 _ . -");
                    }
                    if (!seenUsernames.add(username)) throw BizException.badRequest("文件内用户名重复");
                    if (displayName.isBlank()) throw BizException.badRequest("姓名不能为空");
                    if (displayName.length() > 64) throw BizException.badRequest("姓名长度不能超过 64");
                    String email = cellText(row, layout.column("email"), fmt);
                    if (!email.isBlank()
                            && (email.length() > 128 || !EMAIL_PATTERN.matcher(email).matches())) {
                        throw BizException.badRequest("邮箱格式不正确: " + email);
                    }
                    String phone = cellText(row, layout.column("phone"), fmt);
                    if (phone.length() > 32) throw BizException.badRequest("手机号长度不能超过 32");

                    String roleCode = cellText(row, layout.column("roleCode"), fmt);
                    if (roleCode.isBlank()) throw BizException.badRequest("角色编码不能为空");
                    if ("SUPER_ADMIN".equalsIgnoreCase(roleCode)) {
                        throw BizException.badRequest("不允许通过导入分配超级管理员角色");
                    }
                    RoleMatch role = findAssignableRole(roleCode);

                    Integer status = parseStatus(cellText(row, layout.column("status"), fmt));
                    boolean updateExisting = parseUpdateFlag(
                            cellText(row, layout.column("updateExisting"), fmt));
                    String departmentName = cellText(row, layout.column("department"), fmt);

                    SysUser existing = userService.findByUsername(username);
                    if (existing != null) {
                        if ("admin".equals(existing.getUsername())) {
                            throw BizException.badRequest("内置管理员不允许通过 Excel 修改");
                        }
                        if (!updateExisting) {
                            skip++;
                            preview.add(previewRow(excelRow, username, displayName, "跳过", role.code(),
                                    departmentName, "已存在该用户；如需更新请将「是否更新已有用户」填为是"));
                            continue;
                        }
                        Long departmentId;
                        String deptNote = "";
                        if (departmentName.isBlank()) {
                            departmentId = existing.getDepartmentId();
                        } else {
                            departmentId = matchDepartment(departmentName, warnings, excelRow, username);
                            if (departmentId == null) deptNote = "部门未匹配，已置空";
                        }
                        if (!dryRun) {
                            userService.updateImported(existing.getId(), blankToNull(displayName),
                                    blankToNull(email), blankToNull(phone), status, departmentId, role.id());
                        }
                        update++;
                        preview.add(previewRow(excelRow, username, displayName, "更新", role.code(),
                                departmentName, deptNote));
                    } else {
                        String password = resolvePassword(
                                cellText(row, layout.column("password"), fmt), phone);
                        Long departmentId = null;
                        String deptNote = "";
                        if (!departmentName.isBlank()) {
                            departmentId = matchDepartment(departmentName, warnings, excelRow, username);
                            if (departmentId == null) deptNote = "部门未匹配，已置空";
                        }
                        String passwordSource = passwordSource(
                                cellText(row, layout.column("password"), fmt), phone);
                        if (!dryRun) {
                            userService.createImported(username, password, displayName,
                                    blankToNull(email), blankToNull(phone),
                                    status == null ? 1 : status, departmentId, role.id());
                            Map<String, Object> credential = new LinkedHashMap<>();
                            credential.put("row", excelRow);
                            credential.put("username", username);
                            credential.put("displayName", displayName);
                            credential.put("password", password);
                            credentials.add(credential);
                        }
                        create++;
                        String note = "初始密码来源：" + passwordSource;
                        if (!deptNote.isEmpty()) note = deptNote + "；" + note;
                        preview.add(previewRow(excelRow, username, displayName, "新建", role.code(),
                                departmentName, note));
                    }
                } catch (Exception ex) {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("row", excelRow);
                    error.put("username", username);
                    error.put("reason", rootMessage(ex));
                    errors.add(error);
                }
            }
        } catch (IOException ex) {
            throw BizException.badRequest("Excel 读取失败：" + rootMessage(ex));
        }
        SysUserImportResult result = new SysUserImportResult();
        result.setDryRun(dryRun);
        result.setTotalRows(total);
        result.setCreateCount(create);
        result.setUpdateCount(update);
        result.setSkipCount(skip);
        result.setFailCount(errors.size());
        result.setPreview(preview);
        result.setWarnings(warnings);
        result.setErrors(errors.size() > MAX_RESPONSE_ERRORS
                ? new ArrayList<>(errors.subList(0, MAX_RESPONSE_ERRORS)) : errors);
        if (!errors.isEmpty()) result.setErrorToken(cache(errorCache, errors));
        if (!credentials.isEmpty()) result.setCredentialsToken(cache(credentialCache, credentials));
        return result;
    }

    private Map<String, Object> previewRow(int row, String username, String displayName, String action,
                                           String roleCode, String departmentName, String note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("row", row);
        item.put("username", username);
        item.put("displayName", displayName);
        item.put("action", action);
        item.put("roleCode", roleCode);
        item.put("departmentName", departmentName == null ? "" : departmentName);
        item.put("note", note);
        return item;
    }

    private RoleMatch findAssignableRole(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,code,name FROM sys_role WHERE code=? AND is_assignable=1 LIMIT 2", code);
        if (rows.isEmpty()) {
            throw BizException.badRequest("角色编码不存在或不允许分配: " + code);
        }
        Map<String, Object> row = rows.get(0);
        return new RoleMatch(((Number) row.get("id")).longValue(), (String) row.get("code"));
    }

    private Long matchDepartment(String name, List<Map<String, Object>> warnings, int excelRow, String username) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM sys_department WHERE name=? AND deleted_at IS NULL", Long.class, name);
        if (ids.isEmpty()) {
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("row", excelRow);
            warning.put("username", username);
            warning.put("reason", "部门「" + name + "」未匹配，已置空");
            warnings.add(warning);
            return null;
        }
        if (ids.size() > 1) {
            throw BizException.badRequest("部门名称「" + name + "」存在多条匹配，请先整理部门数据");
        }
        return ids.get(0);
    }

    /** 初始密码优先级：Excel 初始密码列 > 手机号末 6 位数字 > 统一默认密码。 */
    private String resolvePassword(String raw, String phone) {
        if (raw != null && !raw.isBlank()) {
            String password = raw.trim();
            if (password.length() < 6 || password.length() > 64) {
                throw BizException.badRequest("初始密码长度必须为 6-64 位");
            }
            return password;
        }
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() >= 6) return digits.substring(digits.length() - 6);
        return DEFAULT_PASSWORD;
    }

    private String passwordSource(String raw, String phone) {
        if (raw != null && !raw.isBlank()) return "Excel 初始密码列";
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() >= 6) return "手机号末 6 位";
        return "统一默认密码";
    }

    private Integer parseStatus(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return null;
        if (List.of("启用", "1", "是", "true", "y", "yes").contains(value)) return 1;
        if (List.of("禁用", "0", "否", "false", "n", "no").contains(value)) return 0;
        throw BizException.badRequest("状态仅支持填写 启用/禁用: " + raw);
    }

    private boolean parseUpdateFlag(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return false;
        if (List.of("是", "1", "true", "y", "yes").contains(value)) return true;
        if (List.of("否", "0", "false", "n", "no").contains(value)) return false;
        throw BizException.badRequest("是否更新已有用户仅支持填写 是/否: " + raw);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ================= 错误明细 / 初始密码清单 =================

    public void exportErrors(String token, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> errors = token == null ? null : errorCache.get(token);
        if (errors == null) throw BizException.notFound("错误明细已失效，请重新导入");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("导入错误明细");
            String[] headers = {"Excel 行号", "用户名", "错误原因"};
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
                set(row, 1, String.valueOf(error.get("username")));
                set(row, 2, String.valueOf(error.get("reason")));
            }
            download(wb, response, "用户导入错误明细.xlsx");
        }
    }

    public void exportCredentials(String token, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> credentials = token == null ? null : credentialCache.get(token);
        if (credentials == null) throw BizException.notFound("初始密码清单已失效或已被下载");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("初始密码清单");
            String[] headers = {"Excel 行号", "用户名", "姓名", "初始密码"};
            Row header = sheet.createRow(0);
            CellStyle style = headerStyle(wb);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, 5200);
            }
            int index = 1;
            for (Map<String, Object> item : credentials) {
                Row row = sheet.createRow(index++);
                set(row, 0, String.valueOf(item.get("row")));
                set(row, 1, String.valueOf(item.get("username")));
                set(row, 2, String.valueOf(item.get("displayName")));
                set(row, 3, String.valueOf(item.get("password")));
            }
            Sheet note = wb.createSheet("使用说明");
            String[][] notes = {
                    {"说明"},
                    {"本文件仅包含本次导入新建用户的初始密码，下载后缓存即失效，请妥善保管"},
                    {"请通知用户登录后及时修改密码（右上角「修改密码」）"},
                    {"系统不会将明文密码写入数据库或日志"}
            };
            CellStyle noteHeader = headerStyle(wb);
            for (int r = 0; r < notes.length; r++) {
                Row row = note.createRow(r);
                Cell cell = row.createCell(0);
                cell.setCellValue(notes[r][0]);
                if (r == 0) cell.setCellStyle(noteHeader);
            }
            note.setColumnWidth(0, 14000);
            download(wb, response, "用户初始密码清单.xlsx");
        } finally {
            credentialCache.remove(token);
        }
    }

    // ================= 导出 =================

    public void export(String keyword, Integer status, Long departmentId, HttpServletResponse response)
            throws IOException {
        StringBuilder sql = new StringBuilder("""
                SELECT u.username, u.display_name, u.phone, u.email, d.name AS department_name,
                       GROUP_CONCAT(DISTINCT r.code ORDER BY r.code SEPARATOR ',') AS role_codes,
                       GROUP_CONCAT(DISTINCT r.name ORDER BY r.name SEPARATOR ',') AS role_names,
                       u.status, u.last_login_at, u.created_at, u.updated_at
                FROM sys_user u
                LEFT JOIN sys_department d ON d.id = u.department_id
                LEFT JOIN sys_user_role ur ON ur.user_id = u.id
                LEFT JOIN sys_role r ON r.id = ur.role_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" AND (u.username LIKE ? OR u.display_name LIKE ? OR u.email LIKE ? OR u.phone LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null) {
            sql.append(" AND u.status = ?");
            args.add(status);
        }
        if (departmentId != null) {
            sql.append(" AND u.department_id = ?");
            args.add(departmentId);
        }
        sql.append(" GROUP BY u.id ORDER BY u.id DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        String[] headers = {"用户名", "姓名", "手机号", "邮箱", "部门名称", "角色编码", "角色名称", "状态",
                "最后登录", "创建时间", "更新时间"};
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用户清单");
            CellStyle style = headerStyle(wb);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, i == 3 ? 8000 : 5200);
            }
            int index = 1;
            for (Map<String, Object> item : rows) {
                Row row = sheet.createRow(index++);
                set(row, 0, item.get("username"));
                set(row, 1, item.get("display_name"));
                set(row, 2, item.get("phone"));
                set(row, 3, item.get("email"));
                set(row, 4, item.get("department_name"));
                set(row, 5, item.get("role_codes"));
                set(row, 6, item.get("role_names"));
                set(row, 7, Integer.valueOf(1).equals(asInteger(item.get("status"))) ? "启用" : "禁用");
                set(row, 8, formatValue(item.get("last_login_at")));
                set(row, 9, formatValue(item.get("created_at")));
                set(row, 10, formatValue(item.get("updated_at")));
            }
            sheet.createFreezePane(0, 1);
            download(wb, response, "用户清单.xlsx");
        }
    }

    // ================= 表头识别 =================

    private HeaderLayout locateHeader(Sheet sheet, DataFormatter fmt) {
        int last = Math.min(sheet.getLastRowNum(), 9);
        HeaderLayout best = null;
        int bestScore = 0;
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> columns = new LinkedHashMap<>();
            int score = 0;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String canonical = HEADER_ALIASES.get(headerKey(cellText(row, c, fmt)));
                if (canonical == null || columns.containsKey(canonical)) continue;
                columns.put(canonical, c);
                if (REQUIRED_COLUMNS.contains(canonical)) score++;
            }
            if (score > bestScore) {
                best = new HeaderLayout(r, columns);
                bestScore = score;
                if (score == REQUIRED_COLUMNS.size()) break;
            }
        }
        if (best == null || bestScore == 0) {
            throw BizException.badRequest("未能识别表头，请使用「下载模板」生成的文件填写");
        }
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_COLUMNS) {
            if (best.column(key) < 0) missing.add(REQUIRED_LABELS.get(key));
        }
        if (!missing.isEmpty()) {
            throw BizException.badRequest("表头缺少必需列：" + String.join("、", missing));
        }
        return best;
    }

    private static Map<String, String> headerAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        putAliases(aliases, "username", "用户名", "账号", "用户名*", "username");
        putAliases(aliases, "displayName", "姓名", "姓名*", "名称", "displayName");
        putAliases(aliases, "password", "初始密码", "密码", "password");
        putAliases(aliases, "phone", "手机号", "手机", "电话", "phone");
        putAliases(aliases, "email", "邮箱", "电子邮箱", "email");
        putAliases(aliases, "department", "部门名称", "部门", "department");
        putAliases(aliases, "roleCode", "角色编码", "角色编码*", "角色", "角色代码", "roleCode");
        putAliases(aliases, "status", "状态", "status");
        putAliases(aliases, "updateExisting", "是否更新已有用户", "更新已有用户", "updateExisting");
        return Collections.unmodifiableMap(aliases);
    }

    private static void putAliases(Map<String, String> aliases, String canonical, String... names) {
        for (String name : names) aliases.put(headerKey(name), canonical);
    }

    /** 表头规范化：去空格、去括号说明、忽略大小写与分隔符。 */
    private static String headerKey(String raw) {
        if (raw == null) return "";
        String value = raw.replace('\u3000', ' ').trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[（(][^）)]*[)）]", "");
        return value.replaceAll("[\\s_\\-:：/、]", "");
    }

    private static String cellText(Row row, int index, DataFormatter fmt) {
        if (row == null || index < 0 || index >= row.getLastCellNum()) return "";
        Cell cell = row.getCell(index);
        return cell == null ? "" : fmt.formatCellValue(cell).trim();
    }

    // ================= 缓存与工具 =================

    private String cache(Map<String, List<Map<String, Object>>> cache, List<Map<String, Object>> data) {
        String token = UUID.randomUUID().toString().replace("-", "");
        cache.put(token, data);
        if (cache.size() > MAX_CACHE_ENTRIES) {
            Iterator<String> keys = cache.keySet().iterator();
            if (keys.hasNext()) cache.remove(keys.next());
        }
        return token;
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private Integer asInteger(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime time) return DATETIME.format(time);
        if (value instanceof java.sql.Timestamp ts) return DATETIME.format(ts.toLocalDateTime());
        return String.valueOf(value);
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

    private void set(Row row, int index, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty()) row.createCell(index).setCellValue(text);
    }

    private record HeaderLayout(int headerRow, Map<String, Integer> columns) {
        int column(String key) {
            Integer index = columns.get(key);
            return index == null ? -1 : index;
        }
    }

    private record RoleMatch(Long id, String code) {
    }
}
