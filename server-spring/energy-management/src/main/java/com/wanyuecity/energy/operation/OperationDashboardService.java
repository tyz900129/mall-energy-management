package com.wanyuecity.energy.operation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperationDashboardService {
    private final JdbcTemplate jdbc;
    public OperationDashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String,Object> dashboard(LocalDate periodDate, String username, boolean global) {
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        String scope = global ? "" : " AND r.responsible_user_id=?";
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(periodDate));
        if (!global) args.add(userId);
        String base = " FROM stall s LEFT JOIN merchant m ON m.id=s.current_merchant_id " +
                "LEFT JOIN operation_merchant_responsibility r ON r.stall_id=s.id AND r.status='ACTIVE' " +
                "LEFT JOIN sys_user u ON u.id=r.responsible_user_id " +
                "LEFT JOIN operation_clearance_record c ON c.stall_id=s.id AND c.period_date=? " +
                "WHERE s.status='OCCUPIED' AND COALESCE(s.business_type,'')<>'CK'" + scope;
        Map<String,Object> summary = jdbc.queryForMap("SELECT COUNT(*) expectedCount," +
                "SUM(CASE WHEN c.id IS NOT NULL THEN 1 ELSE 0 END) submittedCount," +
                "SUM(CASE WHEN c.id IS NULL THEN 1 ELSE 0 END) missingCount," +
                "SUM(CASE WHEN c.status='ABNORMAL' THEN 1 ELSE 0 END) abnormalCount," +
                "SUM(CASE WHEN r.id IS NULL THEN 1 ELSE 0 END) unassignedCount" + base, args.toArray());
        List<Map<String,Object>> details = jdbc.queryForList("SELECT s.id stallId,s.stall_code stallCode,s.floor_name floorName," +
                "COALESCE(NULLIF(m.merchant_name,''),NULLIF(s.stall_name,''),s.stall_code) merchantName,s.business_type businessType," +
                "u.display_name managerName,u.phone managerPhone,c.id recordId,c.status,c.submitter_name submitterName," +
                "c.submitted_at submittedAt,c.exception_description exceptionDescription " + base +
                " ORDER BY CASE WHEN c.id IS NULL THEN 0 WHEN c.status='ABNORMAL' THEN 1 ELSE 2 END,s.floor_code,s.stall_code", args.toArray());
        List<Map<String,Object>> managerSummary;
        if (global) {
            managerSummary = jdbc.queryForList("SELECT COALESCE(NULLIF(u.display_name,''),u.username,'未分配') managerName," +
                    "COUNT(*) expectedCount,SUM(CASE WHEN c.id IS NOT NULL THEN 1 ELSE 0 END) submittedCount," +
                    "SUM(CASE WHEN c.id IS NULL THEN 1 ELSE 0 END) missingCount,SUM(CASE WHEN c.status='ABNORMAL' THEN 1 ELSE 0 END) abnormalCount" +
                    base + " GROUP BY r.responsible_user_id,u.display_name,u.username ORDER BY missingCount DESC,abnormalCount DESC", Date.valueOf(periodDate));
        } else managerSummary = List.of();
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("periodDate", periodDate.toString());result.put("summary",summary);result.put("details",details);result.put("managerSummary",managerSummary);
        return result;
    }
}
