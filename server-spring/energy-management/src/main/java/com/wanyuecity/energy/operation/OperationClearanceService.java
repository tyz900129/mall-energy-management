package com.wanyuecity.energy.operation;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OperationClearanceService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbc;
    private final OperationClearanceTemplateService templates;

    public OperationClearanceService(JdbcTemplate jdbc, OperationClearanceTemplateService templates) {
        this.jdbc = jdbc;
        this.templates = templates;
    }

    public Map<String, Object> currentPeriod() {
        Schedule schedule = schedule();
        PeriodWindow window = openWindow(LocalDateTime.now(), schedule);
        Map<String, Object> result = scheduleMap(schedule);
        if (window == null) {
            result.put("open", false);
            result.put("message", "日清提交时间为" + scheduleText(schedule));
            return result;
        }
        result.put("open", true);
        result.put("periodDate", window.periodDate().toString());
        result.put("periodStart", window.start());
        result.put("periodEnd", window.end());
        return result;
    }

    public Map<String, Object> settings() {
        return scheduleMap(schedule());
    }

    @Transactional
    public Map<String, Object> updateSettings(Map<String, Object> body, String username) {
        LocalTime start = parseTime(body.get("startTime"), "开始时间");
        LocalTime end = parseTime(body.get("endTime"), "结束时间");
        if (start.equals(end)) throw bad("开始时间和结束时间不能相同");
        jdbc.update("UPDATE operation_clearance_setting SET submit_start_time=?,submit_end_time=?,updated_by=? WHERE id=1",
                Time.valueOf(start), Time.valueOf(end), username);
        return settings();
    }

    @Transactional
    public Map<String, Object> submit(String token, Map<String, Object> body, String username) {
        Map<String, Object> period = currentPeriod();
        if (!Boolean.TRUE.equals(period.get("open"))) throw bad(Objects.toString(period.get("message")));
        Map<String, Object> stall = one("SELECT s.id,s.business_type businessType,s.status," +
                "COALESCE(NULLIF(m.merchant_name,''),NULLIF(s.stall_name,''),s.stall_code) merchantName " +
                "FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id WHERE s.operation_qr_token=?", token);
        if (!"OCCUPIED".equals(stall.get("status"))) throw bad("当前铺位不是在用状态，不计入日清");
        if ("CK".equalsIgnoreCase(Objects.toString(stall.get("businessType"), ""))) throw bad("仓库类型铺位不需要提交日清");

        var evaluation = templates.evaluate(token, body.get("answers"));
        Map<String, Object> answers = evaluation.answers();
        boolean electricity = flag(answers, "electricityOff", true);
        boolean water = flag(answers, "waterOff", true);
        boolean gas = flag(answers, "gasOff", true);
        boolean door = flag(answers, "doorClosed", true);
        String description = Objects.toString(answers.getOrDefault("exceptionDescription", body.get("exceptionDescription")), "").trim();
        String status = evaluation.normal() ? "NORMAL" : "ABNORMAL";
        if (!evaluation.normal() && description.isBlank()) throw bad("存在异常项目时请填写异常说明");

        Map<String, Object> user = one("SELECT id,COALESCE(NULLIF(display_name,''),username) displayName FROM sys_user WHERE username=?", username);
        List<Map<String, Object>> responsibilities = jdbc.queryForList("SELECT r.responsible_user_id managerId," +
                "COALESCE(NULLIF(u.display_name,''),u.username) managerName FROM operation_merchant_responsibility r " +
                "JOIN sys_user u ON u.id=r.responsible_user_id WHERE r.stall_id=? AND r.status='ACTIVE'", stall.get("id"));
        Object managerId = responsibilities.isEmpty() ? null : responsibilities.get(0).get("managerId");
        Object managerName = responsibilities.isEmpty() ? null : responsibilities.get(0).get("managerName");
        LocalDate periodDate = LocalDate.parse(Objects.toString(period.get("periodDate")));
        String recordNo = "QC" + periodDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + stall.get("id");
        jdbc.update("INSERT INTO operation_clearance_record(record_no,stall_id,period_date,period_start,period_end," +
                        "submitter_user_id,submitter_name,merchant_name_snapshot,manager_user_id,manager_name_snapshot," +
                        "template_id,template_version,template_name_snapshot,answers_json,electricity_off,water_off,gas_off," +
                        "door_closed,exception_description,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE submitter_user_id=VALUES(submitter_user_id),submitter_name=VALUES(submitter_name)," +
                        "merchant_name_snapshot=VALUES(merchant_name_snapshot),manager_user_id=VALUES(manager_user_id)," +
                        "manager_name_snapshot=VALUES(manager_name_snapshot),template_id=VALUES(template_id)," +
                        "template_version=VALUES(template_version),template_name_snapshot=VALUES(template_name_snapshot)," +
                        "answers_json=VALUES(answers_json),electricity_off=VALUES(electricity_off),water_off=VALUES(water_off)," +
                        "gas_off=VALUES(gas_off),door_closed=VALUES(door_closed),exception_description=VALUES(exception_description)," +
                        "status=VALUES(status),submitted_at=CURRENT_TIMESTAMP(3)",
                recordNo, stall.get("id"), Date.valueOf(periodDate), Timestamp.valueOf((LocalDateTime) period.get("periodStart")),
                Timestamp.valueOf((LocalDateTime) period.get("periodEnd")), user.get("id"), user.get("displayName"),
                stall.get("merchantName"), managerId, managerName, evaluation.templateId(), evaluation.version(), evaluation.name(),
                evaluation.answersJson(), electricity, water, gas, door, description, status);
        return one("SELECT id,record_no recordNo,status,submitted_at submittedAt FROM operation_clearance_record " +
                "WHERE stall_id=? AND period_date=?", stall.get("id"), Date.valueOf(periodDate));
    }

    public Map<String, Object> todayByToken(String token, String username, boolean unrestricted) {
        Map<String, Object> stall = one("SELECT s.id stallId,s.stall_code stallCode,s.floor_name floorName," +
                "COALESCE(NULLIF(m.merchant_name,''),NULLIF(s.stall_name,''),s.stall_code) merchantName " +
                "FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id " +
                "WHERE s.operation_qr_token=? AND s.status<>'DISABLED'", token);
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        if (!unrestricted) {
            Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM operation_merchant_responsibility " +
                    "WHERE stall_id=? AND responsible_user_id=? AND status='ACTIVE'", Integer.class, stall.get("stallId"), userId);
            if (owned == null || owned == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前铺位不属于您的责任商户");
        }
        Schedule schedule = schedule();
        LocalDate periodDate = periodDateForView(LocalDateTime.now(), schedule);
        List<Map<String, Object>> records = jdbc.queryForList("SELECT c.id,c.record_no recordNo,c.period_date periodDate," +
                        "c.submitter_name submitterName,c.template_name_snapshot templateName,c.answers_json answersJson," +
                        "t.fields_json templateFieldsJson,c.electricity_off electricityOff,c.water_off waterOff," +
                        "c.gas_off gasOff,c.door_closed doorClosed,c.exception_description exceptionDescription," +
                        "c.status,c.submitted_at submittedAt FROM operation_clearance_record c " +
                        "LEFT JOIN operation_clearance_template t ON t.id=c.template_id " +
                        "WHERE c.stall_id=? AND c.period_date=? LIMIT 1", stall.get("stallId"), Date.valueOf(periodDate));
        Map<String, Object> result = new LinkedHashMap<>(stall);
        result.put("periodDate", periodDate.toString());
        result.put("scheduleText", scheduleText(schedule));
        result.put("record", records.isEmpty() ? null : records.get(0));
        return result;
    }

    public Map<String, Object> page(String username, boolean all, String keyword, String date, int pageNum, int pageSize) {
        Map<String, Object> user = one("SELECT id FROM sys_user WHERE username=?", username);
        int page = Math.max(1, pageNum), size = Math.min(100, Math.max(10, pageSize));
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (!all) {
            where.append(" AND (c.submitter_user_id=? OR c.manager_user_id=?)");
            args.add(user.get("id"));
            args.add(user.get("id"));
        }
        if (date != null && !date.isBlank()) {
            where.append(" AND c.period_date=?");
            args.add(Date.valueOf(date));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.stall_code LIKE ? OR c.merchant_name_snapshot LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value);
            args.add(value);
        }
        String from = " FROM operation_clearance_record c JOIN stall s ON s.id=c.stall_id";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT c.id,c.record_no recordNo,c.period_date periodDate," +
                        "s.stall_code stallCode,c.merchant_name_snapshot merchantName,c.submitter_name submitterName," +
                        "c.manager_name_snapshot managerName,c.template_name_snapshot templateName,c.answers_json answersJson," +
                        "c.electricity_off electricityOff,c.water_off waterOff,c.gas_off gasOff,c.door_closed doorClosed," +
                        "c.exception_description exceptionDescription,c.status,c.submitted_at submittedAt" + from + where +
                        " ORDER BY c.period_date DESC,c.submitted_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Map.of("records", rows, "total", total == null ? 0 : total, "current", page, "size", size);
    }

    private Map<String, Object> scheduleMap(Schedule schedule) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startTime", schedule.start().format(TIME_FORMAT));
        result.put("endTime", schedule.end().format(TIME_FORMAT));
        result.put("overnight", schedule.overnight());
        result.put("scheduleText", scheduleText(schedule));
        return result;
    }

    private Schedule schedule() {
        Map<String, Object> row = one("SELECT TIME_FORMAT(submit_start_time,'%H:%i') startTime," +
                "TIME_FORMAT(submit_end_time,'%H:%i') endTime FROM operation_clearance_setting WHERE id=1");
        LocalTime start = LocalTime.parse(Objects.toString(row.get("startTime")), TIME_FORMAT);
        LocalTime end = LocalTime.parse(Objects.toString(row.get("endTime")), TIME_FORMAT);
        return new Schedule(start, end);
    }

    private PeriodWindow openWindow(LocalDateTime now, Schedule schedule) {
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        if (schedule.overnight()) {
            if (!time.isBefore(schedule.start())) return new PeriodWindow(today, today.atTime(schedule.start()), today.plusDays(1).atTime(schedule.end()));
            if (time.isBefore(schedule.end())) return new PeriodWindow(today.minusDays(1), today.minusDays(1).atTime(schedule.start()), today.atTime(schedule.end()));
            return null;
        }
        if (!time.isBefore(schedule.start()) && time.isBefore(schedule.end()))
            return new PeriodWindow(today, today.atTime(schedule.start()), today.atTime(schedule.end()));
        return null;
    }

    private LocalDate periodDateForView(LocalDateTime now, Schedule schedule) {
        PeriodWindow open = openWindow(now, schedule);
        if (open != null) return open.periodDate();
        if (schedule.overnight()) return now.toLocalDate().minusDays(1);
        return now.toLocalTime().isBefore(schedule.start()) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
    }

    private String scheduleText(Schedule schedule) {
        return "每日" + schedule.start().format(TIME_FORMAT) + (schedule.overnight() ? "至次日" : "至当日") + schedule.end().format(TIME_FORMAT);
    }

    private LocalTime parseTime(Object value, String label) {
        try {
            return LocalTime.parse(Objects.toString(value, ""), TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            throw bad(label + "格式不正确");
        }
    }

    private boolean flag(Map<String, Object> answers, String key, boolean defaultValue) {
        return answers.containsKey(key) ? Boolean.parseBoolean(Objects.toString(answers.get(key))) : defaultValue;
    }

    private Map<String, Object> one(String query, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(query, args);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record Schedule(LocalTime start, LocalTime end) {
        boolean overnight() { return start.isAfter(end); }
    }

    private record PeriodWindow(LocalDate periodDate, LocalDateTime start, LocalDateTime end) {}
}
