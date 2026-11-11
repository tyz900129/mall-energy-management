package com.wanyuecity.energy.inspection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.SimpleRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 免登录回执提交必须把每个占位符都绑定到对应列，否则整条回执无法落库。 */
class CalendarPublicServiceTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    /** 只接管本用例需要的两个 JdbcTemplate 方法，避免 Mockito 对 varargs 的展开差异。 */
    static class CapturingJdbcTemplate extends JdbcTemplate {
        String updateSql;
        Object[] updateArgs;
        final List<Map<String, Object>> taskRows;

        CapturingJdbcTemplate(List<Map<String, Object>> taskRows) {
            this.taskRows = taskRows;
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("UPDATE inspection_task SET status=?")) {
                updateSql = sql;
                updateArgs = args;
            }
            return 1;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return sql.contains("FROM attachment") ? List.of() : taskRows;
        }
    }

    @Test
    void submitBindsEveryPlaceholderIncludingReceiptName() {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", 7L);
        task.put("status", "PENDING");
        task.put("submitted_at", null);
        task.put("requirePhoto", 0);
        task.put("fieldsJson", "[]");
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate(List.of(task));
        CalendarPublicService service = new CalendarPublicService(jdbc, new ObjectMapper(), null, new SimpleRateLimiter());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "张三");
        body.put("phone", "13800000000");
        body.put("summary", "已完成");
        body.put("result", Map.of("check", "正常"));
        body.put("issues", List.of());

        service.submit(TOKEN, body, "1.2.3.4", "JUnit-UA");

        long placeholders = jdbc.updateSql.chars().filter(c -> c == '?').count();
        assertEquals(placeholders, jdbc.updateArgs.length, "占位符数量必须与绑定参数一致");
        assertEquals("张三", jdbc.updateArgs[6], "receipt_name 应绑定回执人姓名");
        assertEquals("13800000000", jdbc.updateArgs[7], "receipt_phone 应绑定手机号");
        assertEquals("1.2.3.4", jdbc.updateArgs[8], "receipt_ip 应绑定客户端 IP");
        assertEquals("JUnit-UA", jdbc.updateArgs[9], "receipt_user_agent 应绑定 UA");
        assertEquals(0, jdbc.updateArgs[10], "late_minutes 应绑定迟交分钟数");
        assertEquals(7L, jdbc.updateArgs[11], "WHERE id 应绑定任务 ID");
    }
}
