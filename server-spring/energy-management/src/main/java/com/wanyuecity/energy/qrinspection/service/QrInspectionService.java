package com.wanyuecity.energy.qrinspection.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.notification.entity.WebhookConfig;
import com.wanyuecity.energy.notification.mapper.WebhookConfigMapper;
import com.wanyuecity.energy.notification.service.NotificationService;
import com.wanyuecity.energy.qrinspection.entity.*;
import com.wanyuecity.energy.qrinspection.mapper.*;
import com.wanyuecity.energy.stall.entity.Stall;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.system.entity.SysDepartment;
import com.wanyuecity.energy.system.entity.SysUser;
import com.wanyuecity.energy.system.mapper.SysDepartmentMapper;
import com.wanyuecity.energy.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrInspectionService {

    private final QrInspectionTemplateMapper templateMapper;
    private final QrInspectionPlanMapper    planMapper;
    private final QrInspectionPlanRoundMapper roundMapper;
    private final QrInspectionTaskMapper     taskMapper;
    private final QrInspectionTaskTargetMapper taskTargetMapper;
    private final QrInspectionRecordMapper   recordMapper;
    private final QrInspectionIssueMapper    issueMapper;
    private final SysDepartmentWebhookMapper deptWebhookMapper;
    private final StallMapper stallMapper;
    private final SysUserMapper userMapper;
    private final SysDepartmentMapper deptMapper;
    private final WebhookConfigMapper webhookConfigMapper;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${energy.public-base-url:https://auto.apperload.com}")
    private String publicBaseUrl;

    private static final SecureRandom RAND = new SecureRandom();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MSG_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static final String SCENE_WH_COMM = "WAREHOUSE_COMMERCIAL";
    public static final String SCENE_WH_PROP = "WAREHOUSE_PROPERTY";
    public static final String SCENE_JOINT  = "JOINT";

    private static final Set<String> COMMERCIAL_ROLES = Set.of("APPLICANT", "OPS_PATROL_MANAGER");
    private static final Set<String> PROPERTY_ROLES   = Set.of("PROPERTY_MANAGER", "DEVICE_VERIFIER");
    private static final Set<String> ADMIN_ROLES      = Set.of("SUPER_ADMIN", "SYSTEM_ADMIN");

    private UserInfo userInfo(Authentication auth) {
        UserInfo u = new UserInfo();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            u.username = "anonymous";
            u.roles = List.of();
            return u;
        }
        u.username = auth.getName();
        if (auth.getAuthorities() != null) {
            u.roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a != null && a.startsWith("ROLE_"))
                    .map(a -> a.substring(5))
                    .collect(Collectors.toList());
        }
        try {
            SysUser su = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", auth.getName()));
            if (su != null) {
                u.displayName = su.getDisplayName();
                u.phone = su.getPhone();
                if (su.getDepartmentId() != null) {
                    u.departmentId = su.getDepartmentId();
                    SysDepartment d = deptMapper.selectById(su.getDepartmentId());
                    if (d != null) {
                        u.deptCode = d.getCode();
                        u.deptName = d.getName();
                        u.deptGroup = d.getDeptGroup();
                        u.opsDeptId = d.getOpsDeptId();
                    }
                }
            }
        } catch (Exception e) { log.warn("load user info failed for {}", auth.getName(), e); }
        return u;
    }

    private static class UserInfo {
        String username;
        String displayName;
        String phone;
        String deptCode;
        String deptName;
        String deptGroup;
        Long departmentId;
        Long opsDeptId;
        List<String> roles = new ArrayList<>();
    }

    private boolean isAdmin(UserInfo u) {
        return u.roles != null && u.roles.stream().anyMatch(ADMIN_ROLES::contains);
    }

    public String roleGroupOf(List<String> roles) {
        if (roles == null) return null;
        if (roles.stream().anyMatch(COMMERCIAL_ROLES::contains)) return "COMMERCIAL";
        if (roles.stream().anyMatch(PROPERTY_ROLES::contains))   return "PROPERTY";
        return null;
    }

    /**
     * 岗位即角色：WY、QD、RD、NT 前缀的岗位角色没有历史角色别名，
     * 按所属部门分组兜底，保证这些岗位可以提交对应场景的巡检。
     */
    private String roleGroupOfUser(UserInfo u) {
        String byRole = roleGroupOf(u.roles);
        if (byRole != null) return byRole;
        if ("PROPERTY".equals(u.deptGroup)) return "PROPERTY";
        if ("OPERATION".equals(u.deptGroup)) return "COMMERCIAL";
        return null;
    }

    /** 商管按处室负责楼层过滤；物业/管理员返回 null 表示全量 */
    private List<String> scopeFloors(UserInfo u) {
        if (u.opsDeptId == null) return null;
        return jdbc.queryForList("SELECT floor_code FROM ops_patrol_dept_floor WHERE dept_id=?", String.class, u.opsDeptId);
    }

    private boolean inScope(Stall stall, UserInfo u) {
        List<String> floors = scopeFloors(u);
        if (floors == null) return true;
        return stall.getFloorCode() != null && floors.contains(stall.getFloorCode());
    }

    private String normalizeScene(String scene, UserInfo u) {
        if (scene != null && !scene.isBlank()) return scene;
        String rg = roleGroupOfUser(u);
        if ("COMMERCIAL".equals(rg)) return SCENE_WH_COMM;
        if ("PROPERTY".equals(rg))   return SCENE_WH_PROP;
        return null;
    }

    private String sceneLabel(String scene) {
        if (scene == null) return "";
        return switch (scene) {
            case SCENE_WH_COMM -> "仓库商管巡检";
            case SCENE_WH_PROP -> "仓库物业巡检";
            case SCENE_JOINT   -> "联合巡检";
            default -> scene;
        };
    }

    private String labelOf(String bt) {
        if (bt == null) return "";
        return switch (bt) {
            case "CK" -> "仓库";
            case "CYCY" -> "餐饮业态";
            case "BHLS" -> "百货零售";
            case "ZB" -> "总部";
            case "BG" -> "办公";
            default -> bt;
        };
    }

    private String baseUrl() { return publicBaseUrl.replaceAll("/+$", ""); }

    // ======================== 铺位检索（扫码外的模糊搜索） ========================

    public List<Map<String, Object>> searchStalls(String keyword, String scene, Authentication auth) {
        UserInfo u = userInfo(auth);
        if (u.departmentId == null && !isAdmin(u)) throw BizException.forbidden("当前账号未绑定部门");
        String rg = roleGroupOfUser(u);
        if (rg == null && !isAdmin(u)) throw BizException.forbidden("当前账号无巡检权限");

        List<String> floors = scopeFloors(u);
        if (floors != null && floors.isEmpty()) return List.of();

        String kw = keyword == null ? "" : keyword.trim();
        QueryWrapper<Stall> q = new QueryWrapper<>();
        if (!kw.isEmpty()) {
            String v = kw;
            q.and(w -> w.like("stall_code", v).or().like("stall_name", v).or().like("floor_name", v));
        }
        if (floors != null) q.in("floor_code", floors);
        q.ne("status", "DECOMMISSIONED");
        q.orderByAsc("floor_code").orderByAsc("stall_code");
        q.last("LIMIT 30");
        List<Stall> list = stallMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Stall s : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("token", s.getOperationQrToken());
            m.put("stallCode", s.getStallCode());
            m.put("stallName", s.getStallName());
            m.put("floorCode", s.getFloorCode());
            m.put("floorName", s.getFloorName());
            m.put("businessType", s.getBusinessType());
            m.put("businessTypeLabel", labelOf(s.getBusinessType()));
            out.add(m);
        }
        return out;
    }

    // ======================== 页面上下文 ========================

    public Map<String, Object> context(String scene, Long planId, Long taskId, Authentication auth) {
        UserInfo u = userInfo(auth);
        String sc = normalizeScene(scene, u);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deptCode", u.deptCode);
        out.put("deptName", u.deptName);
        out.put("roleGroup", roleGroupOfUser(u));

        QrInspectionPlan plan = null;
        QrInspectionTask task = null;
        QrInspectionTemplate tpl;

        if (taskId != null) {
            task = taskMapper.selectById(taskId);
            if (task == null) throw BizException.badRequest("联合巡检任务不存在");
            sc = SCENE_JOINT;
            tpl = task.getTemplateId() == null ? pickTemplate(SCENE_JOINT, null, null)
                    : templateMapper.selectById(task.getTemplateId());
        } else if (SCENE_JOINT.equals(sc)) {
            task = taskMapper.active();
            if (task == null) throw BizException.badRequest("当前没有进行中的联合巡检任务");
            tpl = task.getTemplateId() == null ? pickTemplate(SCENE_JOINT, null, null)
                    : templateMapper.selectById(task.getTemplateId());
        } else {
            plan = planId != null ? planMapper.selectById(planId) : planMapper.activeOfDept(sc, u.deptCode);
            if (plan != null) {
                if (!isAdmin(u) && plan.getDeptCode() != null && !plan.getDeptCode().equals(u.deptCode))
                    throw BizException.forbidden("该巡检计划不属于您所在部门");
                sc = plan.getScene();
            }
            tpl = plan != null && plan.getTemplateId() != null ? templateMapper.selectById(plan.getTemplateId())
                    : pickTemplate(sc, roleGroupOfUser(u), u.deptCode);
        }

        if (tpl == null) throw BizException.badRequest("未配置巡检模板");
        out.put("scene", sc);
        out.put("sceneLabel", sceneLabel(sc));
        out.put("templateId", tpl.getId());
        out.put("templateName", tpl.getName());
        out.put("fieldsJson", tpl.getFieldsJson());
        if (plan != null) {
            out.put("planId", plan.getId());
            out.put("planName", sceneLabel(plan.getScene()) + "月度计划");
            QrInspectionPlanRound round = roundMapper.latest(plan.getId());
            out.put("roundStatus", round == null ? "未开始" : round.getStatus());
            out.put("roundDueDate", round == null ? null : round.getDueDate().toString());
        }
        if (task != null) {
            out.put("taskId", task.getId());
            out.put("taskNo", task.getTaskNo());
            out.put("taskName", task.getName());
            out.put("taskEndAt", task.getEndAt() == null ? null : task.getEndAt().format(MSG_TIME_FMT));
            out.put("participatingDeptCodes", task.getParticipatingDeptCodes());
        }
        return out;
    }

    // ======================== 扫码入口 ========================

    public Map<String, Object> availableScenes(String token, Authentication auth) {
        UserInfo u = userInfo(auth);
        Stall stall = stallMapper.selectOne(new QueryWrapper<Stall>().eq("operation_qr_token", token));
        if (stall == null) throw BizException.badRequest("二维码无效");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stallId", stall.getId());
        out.put("token", stall.getOperationQrToken());
        out.put("businessType", stall.getBusinessType());
        out.put("businessTypeLabel", labelOf(stall.getBusinessType()));
        out.put("stallCode", stall.getStallCode());
        out.put("stallName", stall.getStallName());
        out.put("floorCode", stall.getFloorCode());

        String rg = roleGroupOfUser(u);
        boolean admin = isAdmin(u);
        boolean scoped = inScope(stall, u);
        List<Map<String, Object>> scenes = new ArrayList<>();
        if (scoped && (admin || "COMMERCIAL".equals(rg))) {
            scenes.add(buildScene(SCENE_WH_COMM, "COMMERCIAL", u));
        }
        if (scoped && (admin || "PROPERTY".equals(rg))) {
            scenes.add(buildScene(SCENE_WH_PROP, "PROPERTY", u));
        }
        QrInspectionTask active = taskMapper.active();
        if (active != null && scoped && participates(u, active)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scene", SCENE_JOINT);
            m.put("roleGroup", null);
            m.put("templateId", active.getTemplateId());
            m.put("templateName", templateNameOf(active.getTemplateId()));
            m.put("taskId", active.getId());
            m.put("name", active.getName());
            m.put("endAt", active.getEndAt() == null ? null : active.getEndAt().format(MSG_TIME_FMT));
            scenes.add(m);
        }
        out.put("scenes", scenes);
        return out;
    }

    private Map<String, Object> buildScene(String scene, String roleGroup, UserInfo u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scene", scene);
        m.put("roleGroup", roleGroup);
        QrInspectionTemplate t = pickTemplate(scene, roleGroup, u.deptCode);
        if (t != null) {
            m.put("templateId", t.getId());
            m.put("templateName", t.getName());
        }
        QrInspectionPlan plan = planMapper.activeOfDept(scene, u.deptCode);
        if (plan != null) m.put("planId", plan.getId());
        return m;
    }

    private String templateNameOf(Long id) {
        if (id == null) return null;
        QrInspectionTemplate t = templateMapper.selectById(id);
        return t == null ? null : t.getName();
    }

    public QrInspectionTemplate pickTemplate(String scene, String roleGroup, String deptCode) {
        List<QrInspectionTemplate> list = templateMapper.selectList(
                new QueryWrapper<QrInspectionTemplate>().eq("scene", scene).eq("enabled", 1)
                        .orderByAsc("id"));
        if (list.isEmpty()) return null;
        for (QrInspectionTemplate t : list)
            if (Objects.equals(roleGroup, t.getRoleGroup()) && Objects.equals(deptCode, t.getDeptCode())) return t;
        for (QrInspectionTemplate t : list)
            if (Objects.equals(roleGroup, t.getRoleGroup()) && (t.getDeptCode() == null || t.getDeptCode().isBlank())) return t;
        for (QrInspectionTemplate t : list)
            if (t.getRoleGroup() == null || t.getRoleGroup().isBlank()) return t;
        return list.get(0);
    }

    // ======================== 提交巡检 ========================

    @Transactional
    public QrInspectionRecord submitByToken(QrInspectionRecord record, String token, Authentication auth) {
        Stall stall = stallMapper.selectOne(new QueryWrapper<Stall>().eq("operation_qr_token", token));
        if (stall == null) throw BizException.badRequest("二维码无效");
        record.setStallId(stall.getId());
        return submit(record, auth);
    }

    @Transactional
    public QrInspectionRecord submit(QrInspectionRecord record, Authentication auth) {
        UserInfo u = userInfo(auth);
        if (u.departmentId == null && !isAdmin(u)) throw BizException.forbidden("当前账号未绑定部门");
        if (record.getStallId() == null) throw BizException.badRequest("请先选择巡检铺位");
        Stall stall = stallMapper.selectById(record.getStallId());
        if (stall == null) throw BizException.badRequest("铺位不存在");

        String scene = normalizeScene(record.getScene(), u);
        if (scene == null) throw BizException.badRequest("无法识别巡检场景");
        String rg = roleGroupOfUser(u);

        QrInspectionTemplate tpl;
        QrInspectionPlan plan = null;
        QrInspectionTask task = null;

        if (SCENE_JOINT.equals(scene)) {
            task = record.getTaskId() != null ? taskMapper.selectById(record.getTaskId()) : taskMapper.active();
            if (task == null || !"ACTIVE".equals(task.getStatus())) throw BizException.badRequest("联合巡检任务已截止");
            if (!participates(u, task)) throw BizException.forbidden("您所在部门未参与本次联合巡检");
            tpl = task.getTemplateId() == null ? pickTemplate(SCENE_JOINT, null, null)
                    : templateMapper.selectById(task.getTemplateId());
        } else if (SCENE_WH_COMM.equals(scene)) {
            if (!isAdmin(u) && !"COMMERCIAL".equals(rg)) throw BizException.forbidden("当前账号无权提交商管巡检");
            if (!inScope(stall, u)) throw BizException.forbidden("该铺位不在您所在部门负责区域");
            plan = resolvePlan(scene, u, record.getPlanId());
            tpl = plan != null && plan.getTemplateId() != null ? templateMapper.selectById(plan.getTemplateId())
                    : pickTemplate(scene, "COMMERCIAL", u.deptCode);
        } else if (SCENE_WH_PROP.equals(scene)) {
            if (!isAdmin(u) && !"PROPERTY".equals(rg)) throw BizException.forbidden("当前账号无权提交物业巡检");
            plan = resolvePlan(scene, u, record.getPlanId());
            tpl = plan != null && plan.getTemplateId() != null ? templateMapper.selectById(plan.getTemplateId())
                    : pickTemplate(scene, "PROPERTY", u.deptCode);
        } else {
            throw BizException.badRequest("不支持的巡检场景");
        }
        if (tpl == null) throw BizException.badRequest("未配置巡检模板");

        record.setScene(scene);
        record.setTaskId(task == null ? null : task.getId());
        record.setStallId(stall.getId());
        record.setStallCode(stall.getStallCode());
        record.setStallName(stall.getStallName());
        record.setTemplateId(tpl.getId());
        record.setTemplateSnapshot(tpl.getFieldsJson());
        record.setRoleGroup(task == null ? rg : null);
        record.setSubmittedAt(LocalDateTime.now());
        record.setSubmitterName(u.displayName == null ? u.username : u.displayName);
        record.setSubmitterPhone(u.phone);
        record.setSubmitterDept(u.deptCode);
        record.setDeptCode(u.deptCode);
        record.setStatus(judgeStatus(tpl.getFieldsJson(), record.getAnswersJson()));

        QrInspectionPlanRound round = null;
        if (plan != null) {
            if (!isAdmin(u) && plan.getDeptCode() != null && !plan.getDeptCode().equals(u.deptCode))
                throw BizException.forbidden("该巡检计划不属于您所在部门");
            record.setPlanId(plan.getId());
            round = resolveRound(plan);
            record.setPlanRoundId(round == null ? null : round.getId());
        }
        recordMapper.insert(record);

        if (round != null) {
            round.setRecordCount((round.getRecordCount() == null ? 0 : round.getRecordCount()) + 1);
            if ("PENDING".equals(round.getStatus())) {
                round.setStatus("COMPLETED");
                round.setCompletedAt(LocalDateTime.now());
            }
            roundMapper.updateById(round);
        }
        if (task != null) {
            QrInspectionTaskTarget tgt = taskTargetMapper.selectOne(new QueryWrapper<QrInspectionTaskTarget>()
                    .eq("task_id", task.getId()).eq("stall_id", stall.getId()).last("LIMIT 1"));
            if (tgt == null) {
                tgt = new QrInspectionTaskTarget();
                tgt.setTaskId(task.getId());
                tgt.setStallId(stall.getId());
                tgt.setSelectedDeptCode(u.deptCode);
                tgt.setStatus("SUBMITTED");
                tgt.setSubmittedAt(LocalDateTime.now());
                tgt.setSubmittedBy(u.username);
                tgt.setRecordId(record.getId());
                taskTargetMapper.insert(tgt);
            } else {
                tgt.setRecordId(record.getId());
                tgt.setSubmittedAt(LocalDateTime.now());
                tgt.setSubmittedBy(u.username);
                tgt.setSelectedDeptCode(u.deptCode);
                tgt.setStatus("SUBMITTED");
                taskTargetMapper.updateById(tgt);
            }
        }
        aggregateIssuesAndNotify(record, stall);
        return record;
    }

    private QrInspectionPlan resolvePlan(String scene, UserInfo u, Long planId) {
        if (planId != null) {
            QrInspectionPlan p = planMapper.selectById(planId);
            if (p != null) return p;
        }
        return planMapper.activeOfDept(scene, u.deptCode);
    }

    private QrInspectionPlanRound resolveRound(QrInspectionPlan plan) {
        QrInspectionPlanRound round = roundMapper.firstPending(plan.getId());
        if (round != null) return round;
        LocalDate today = LocalDate.now();
        round = roundMapper.byPlanAndDate(plan.getId(), today);
        if (round == null) {
            round = new QrInspectionPlanRound();
            round.setPlanId(plan.getId());
            round.setDueDate(today);
            round.setStatus("PENDING");
            round.setRecordCount(0);
            roundMapper.insert(round);
        }
        return round;
    }

    private String judgeStatus(String fieldsJson, String answersJson) {
        List<Map<String, Object>> fields = parseList(fieldsJson);
        Map<String, Object> answers = parseMap(answersJson);
        for (Map<String, Object> f : fields) {
            String code = str(f.get("code"));
            if (code == null || code.isBlank()) continue;
            if (isAbnormal(f, answers.get(code))) return "ABNORMAL";
        }
        return "NORMAL";
    }

    /**
     * 解析 answers → 按责任部门分组异常项 → 生成部门级整改任务并推送。
     * 责任部门优先级：检查项上指定的 responsibleDeptCode → 铺位所属商管处室（按楼层映射）。
     */
    private void aggregateIssuesAndNotify(QrInspectionRecord record, Stall stall) {
        try {
            List<Map<String, Object>> fields = parseList(record.getTemplateSnapshot());
            if (fields.isEmpty() && record.getTemplateId() != null) {
                QrInspectionTemplate tpl = templateMapper.selectById(record.getTemplateId());
                if (tpl != null) fields = parseList(tpl.getFieldsJson());
            }
            Map<String, Object> answers = parseMap(record.getAnswersJson());
            String defaultDeptCode = defaultResponsibleDeptCode(stall, record);
            Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
            for (Map<String, Object> f : fields) {
                String code = str(f.get("code"));
                if (code == null || code.isBlank()) continue;
                String deptCode = str(f.get("responsibleDeptCode"));
                if (deptCode == null || deptCode.isBlank()) deptCode = defaultDeptCode;
                if (deptCode == null || deptCode.isBlank()) continue;
                Object answer = answers.get(code);
                if (!isAbnormal(f, answer)) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fieldCode", code);
                item.put("fieldLabel", f.get("label"));
                item.put("category", str(f.get("category")));
                item.put("answer", answer);
                grouped.computeIfAbsent(deptCode, k -> new ArrayList<>()).add(item);
            }
            if (grouped.isEmpty()) return;
            for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
                String dCode = e.getKey();
                List<Map<String, Object>> items = e.getValue();
                Long deptId = deptIdOf(dCode);
                String deptName = deptNameOf(dCode);
                QrInspectionIssue issue = new QrInspectionIssue();
                issue.setRecordId(record.getId());
                issue.setTaskId(record.getTaskId());
                issue.setDepartmentId(deptId);
                issue.setDeptCode(dCode);
                issue.setDeptName(deptName);
                issue.setStallId(stall.getId());
                String cats = items.stream().map(i -> str(i.get("category"))).distinct().filter(c -> c != null).collect(Collectors.joining("、"));
                issue.setSummaryText("巡检异常 " + items.size() + " 项（" + cats + "）");
                issue.setItemsJson(json.writeValueAsString(items));
                issue.setAccessToken(randomToken());
                issue.setDeadlineAt(LocalDateTime.now().plusHours(24));
                issue.setStatus("PENDING");
                issueMapper.insert(issue);
                List<Long> configIds = filterByEvent(deptWebhookMapper.primaryConfigIdsOfDept(deptId), "QR_INSPECTION_ISSUE");
                if (!configIds.isEmpty()) {
                    String content = buildIssueMessage(issue, stall, record.getSubmitterName());
                    notificationService.sendToConfigs(configIds, "QR_INSPECTION_ISSUE", content);
                }
            }
        } catch (Exception ex) {
            log.error("qr inspection issue aggregate failed recordId={}", record.getId(), ex);
        }
    }

    /**
     * 默认整改责任部门：铺位所在楼层对应的商管处室（OPERATION 部门）。
     * 楼层未配置映射时，回退到提交人所属处室（仅当其属于营运条线）。
     */
    private String defaultResponsibleDeptCode(Stall stall, QrInspectionRecord record) {
        try {
            if (stall != null && stall.getFloorCode() != null) {
                List<String> codes = jdbc.queryForList(
                        "SELECT d.code FROM sys_department d JOIN ops_patrol_dept_floor f ON f.dept_id=d.ops_dept_id "
                                + "WHERE f.floor_code=? AND d.dept_group='OPERATION' AND d.enabled=1 ORDER BY d.id LIMIT 1",
                        String.class, stall.getFloorCode());
                if (!codes.isEmpty() && codes.get(0) != null && !codes.get(0).isBlank()) return codes.get(0);
            }
            if (record != null && record.getDeptCode() != null) {
                Integer n = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_department WHERE code=? AND dept_group='OPERATION' AND enabled=1",
                        Integer.class, record.getDeptCode());
                if (n != null && n > 0) return record.getDeptCode();
            }
        } catch (Exception ex) {
            log.warn("resolve default responsible dept failed stall={}", stall == null ? null : stall.getId());
        }
        return null;
    }

    private List<Long> filterByEvent(List<Long> configIds, String event) {
        if (configIds == null || configIds.isEmpty()) return new ArrayList<>();
        Set<Long> idSet = new HashSet<>(configIds);
        List<WebhookConfig> cfgs = webhookConfigMapper.selectList(
                new QueryWrapper<WebhookConfig>().eq("enabled", 1).in("id", idSet));
        List<Long> result = new ArrayList<>();
        for (WebhookConfig c : cfgs) {
            if (c.getEventTypes() != null && c.getEventTypes().contains(event)) result.add(c.getId());
        }
        return result;
    }

    private boolean isAbnormal(Map<String, Object> f, Object answer) {
        if (answer == null) return false;
        String ct = str(f.get("controlType"));
        Object normal = f.get("normalValue");
        if ("NORMAL_ABNORMAL".equals(ct)) return "ABNORMAL".equals(answer);
        if ("BOOLEAN".equals(ct)) {
            if (normal == null) return false;
            return !Objects.equals(Boolean.parseBoolean(str(normal)), Boolean.parseBoolean(str(answer)));
        }
        return false;
    }

    private String buildIssueMessage(QrInspectionIssue issue, Stall stall, String submitterName) {
        StringBuilder b = new StringBuilder("### 🚨 巡检整改任务\n\n");
        b.append("**责任部门**：").append(issue.getDeptName() == null ? issue.getDeptCode() : issue.getDeptName()).append("\n");
        b.append("**铺位**：").append(stall.getStallCode() == null ? stall.getId() : stall.getStallCode())
                .append(" ").append(stall.getStallName() == null ? "" : stall.getStallName()).append("\n");
        b.append("**提交人**：").append(submitterName == null ? "-" : submitterName).append("\n");
        b.append("**截止**：").append(issue.getDeadlineAt().format(MSG_TIME_FMT)).append("\n\n");
        b.append("**异常项**：\n");
        for (Map<String, Object> it : parseList(issue.getItemsJson())) {
            b.append("- ").append(str(it.get("category"))).append("｜").append(str(it.get("fieldLabel"))).append("\n");
        }
        b.append("\n[👉 点击提交整改](").append(baseUrl()).append("/warehouse/rectify/").append(issue.getAccessToken()).append(")");
        return b.toString();
    }

    // ======================== 联合巡检 ========================

    @Transactional
    public QrInspectionTask createJoint(QrInspectionTask task, List<String> deptCodes) {
        if (taskMapper.active() != null) throw BizException.badRequest("已有进行中的联合巡检任务，请先截止");
        QrInspectionTemplate tpl = pickTemplate(SCENE_JOINT, null, null);
        if (tpl == null) throw BizException.badRequest("未找到联合巡检模板");
        task.setTaskNo(genTaskNo("JQ"));
        task.setStatus("ACTIVE");
        task.setTemplateId(tpl.getId());
        task.setTemplateVersion(null);
        task.setParticipatingDeptCodes(deptCodes == null ? null
                : deptCodes.stream().filter(c -> c != null && !c.isBlank()).distinct().collect(Collectors.joining(",")));
        taskMapper.insert(task);

        List<Long> configIds = new ArrayList<>();
        if (deptCodes != null) {
            for (String code : deptCodes) {
                Long deptId = deptIdOf(code);
                if (deptId == null) continue;
                configIds.addAll(deptWebhookMapper.primaryConfigIdsOfDept(deptId));
            }
        }
        if (configIds.isEmpty()) {
            configIds = deptWebhookMapper.allActivePrimaryBindings().stream()
                    .map(m -> (Long) m.get("configId")).distinct().collect(Collectors.toList());
        }
        List<Long> filtered = filterByEvent(configIds, "QR_INSPECTION_DUE");
        if (!filtered.isEmpty()) {
            String link = baseUrl() + "/warehouse/inspect?scene=" + SCENE_JOINT + "&taskId=" + task.getId();
            StringBuilder b = new StringBuilder("### 🔍 联合巡检任务已发起\n\n");
            b.append("**任务**：").append(task.getName()).append("\n");
            b.append("**编号**：").append(task.getTaskNo()).append("\n");
            b.append("**截止**：").append(task.getEndAt() == null ? "-" : task.getEndAt().format(MSG_TIME_FMT)).append("\n");
            b.append("**参与部门**：").append(deptNamesOf(task.getParticipatingDeptCodes())).append("\n\n");
            b.append("> 现场随机巡检，走到任意商户/仓库后扫码或搜索铺位填写表单即可。\n\n");
            b.append("[👉 打开联合巡检页面](").append(link).append(")");
            notificationService.sendToConfigs(filtered, "QR_INSPECTION_DUE", b.toString());
        }
        return task;
    }

    @Transactional
    public void closeJoint(Long taskId, String closedBy) {
        QrInspectionTask t = taskMapper.selectById(taskId);
        if (t == null || !"ACTIVE".equals(t.getStatus())) throw BizException.badRequest("任务不存在或已截止");
        t.setStatus("CLOSED");
        t.setClosedAt(LocalDateTime.now());
        t.setClosedBy(closedBy);
        taskMapper.updateById(t);
        // 兼容历史预选铺位数据：未提交的标记为未巡检
        taskTargetMapper.update(null, new UpdateWrapper<QrInspectionTaskTarget>()
                .eq("task_id", taskId).eq("status", "PENDING").set("status", "MISSED"));

        List<QrInspectionRecord> records = recordMapper.selectList(
                new QueryWrapper<QrInspectionRecord>().eq("task_id", taskId).eq("status", "ABNORMAL"));
        for (QrInspectionRecord r : records) {
            if (issueMapper.selectCount(new QueryWrapper<QrInspectionIssue>().eq("record_id", r.getId())).intValue() == 0) {
                Stall stall = stallMapper.selectById(r.getStallId());
                if (stall != null) aggregateIssuesAndNotify(r, stall);
            }
        }
        List<Long> configIds = new ArrayList<>();
        if (t.getParticipatingDeptCodes() != null) {
            for (String code : t.getParticipatingDeptCodes().split(",")) {
                Long deptId = deptIdOf(code.trim());
                if (deptId != null) configIds.addAll(deptWebhookMapper.primaryConfigIdsOfDept(deptId));
            }
        }
        if (configIds.isEmpty()) {
            configIds = deptWebhookMapper.allActivePrimaryBindings().stream()
                    .map(m -> (Long) m.get("configId")).distinct().collect(Collectors.toList());
        }
        List<Long> filtered = filterByEvent(configIds, "QR_JOINT_CLOSED");
        if (!filtered.isEmpty()) {
            notificationService.sendToConfigs(filtered, "QR_JOINT_CLOSED",
                    "### 📢 联合巡检已截止\n\n**任务**：" + t.getName()
                            + "\n**截止时间**：" + t.getClosedAt().format(MSG_TIME_FMT)
                            + "\n\n各部门整改任务已按异常责任部门自动生成并推送。");
        }
    }

    // ======================== 整改提交 ========================

    public QrInspectionIssue feedback(String token, String text, List<String> photoUrls, String byName, String byId) {
        QrInspectionIssue issue = issueMapper.byToken(token);
        if (issue == null) throw BizException.badRequest("整改链接无效或已过期");
        if ("CLOSED".equals(issue.getStatus())) throw BizException.badRequest("该整改任务已关闭，不能重复提交");
        if (text == null || text.isBlank()) throw BizException.badRequest("整改说明不能为空");
        issue.setFeedbackText(text);
        issue.setFeedbackPhotosJson(jsonWrite(photoUrls));
        issue.setFeedbackBy(byId);
        issue.setFeedbackByName(byName);
        issue.setFeedbackAt(LocalDateTime.now());
        issue.setClosedAt(LocalDateTime.now());
        issue.setStatus("CLOSED");
        issueMapper.updateById(issue);
        return issue;
    }

    // ======================== 周期计划到期推送 ========================

    @Scheduled(cron = "0 * * * * ?")
    public void fireDuePlans() {
        try {
            List<QrInspectionPlan> due = planMapper.duePlans(LocalDateTime.now());
            for (QrInspectionPlan p : due) {
                try {
                    LocalDate dueDate = p.getNextDueAt().toLocalDate();
                    QrInspectionPlanRound round = ensureRound(p, dueDate);
                    if (round.getPushedAt() == null) {
                        pushPlanCard(p, round);
                        round.setPushedAt(LocalDateTime.now());
                        roundMapper.updateById(round);
                    }
                    p.setLastRunDate(dueDate);
                    p.setNextDueAt(nextAfter(dueDate, p.getDayOfMonth(), p.getPushTime()));
                    planMapper.updateById(p);
                } catch (Exception ex) {
                    log.error("qr inspection plan fire failed planId={}", p.getId(), ex);
                }
            }
        } catch (Exception e) { log.error("qr inspection due scan failed", e); }
    }

    private QrInspectionPlanRound ensureRound(QrInspectionPlan plan, LocalDate dueDate) {
        QrInspectionPlanRound round = roundMapper.byPlanAndDate(plan.getId(), dueDate);
        if (round != null) return round;
        round = new QrInspectionPlanRound();
        round.setPlanId(plan.getId());
        round.setDueDate(dueDate);
        round.setStatus("PENDING");
        round.setRecordCount(0);
        roundMapper.insert(round);
        return round;
    }

    private void pushPlanCard(QrInspectionPlan plan, QrInspectionPlanRound round) {
        String link = baseUrl() + "/warehouse/inspect?scene=" + plan.getScene() + "&planId=" + plan.getId();
        StringBuilder b = new StringBuilder("### 📋 巡检任务提醒\n\n");
        b.append("**类型**：").append(sceneLabel(plan.getScene())).append("\n");
        b.append("**部门**：").append(deptNameOf(plan.getDeptCode())).append("\n");
        b.append("**巡检日期**：").append(round.getDueDate().toString()).append("\n");
        b.append("**要求**：请今日完成巡检，扫码或搜索铺位后填写表单提交\n\n");
        b.append("[👉 打开巡检页面](").append(link).append(")");
        List<Long> cfgs = filterByEvent(deptWebhookMapper.primaryConfigIdsOfDept(plan.getDeptId()), "QR_INSPECTION_DUE");
        if (!cfgs.isEmpty()) notificationService.sendToConfigs(cfgs, "QR_INSPECTION_DUE", b.toString());
        else log.warn("qr inspection plan {} dept {} has no enabled webhook", plan.getId(), plan.getDeptCode());
    }

    // ======================== 工具 ========================

    private boolean participates(UserInfo u, QrInspectionTask task) {
        String codes = task.getParticipatingDeptCodes();
        if (codes == null || codes.isBlank()) return true;
        if (u.deptCode == null) return false;
        return Arrays.stream(codes.split(",")).map(String::trim).anyMatch(c -> c.equals(u.deptCode));
    }

    private String deptNamesOf(String codes) {
        if (codes == null || codes.isBlank()) return "全体部门";
        return Arrays.stream(codes.split(",")).map(String::trim)
                .filter(c -> !c.isBlank()).map(this::deptNameOf).collect(Collectors.joining("、"));
    }

    private LocalTime parseTime(String hhmm) {
        try { return LocalTime.parse(hhmm == null ? "09:00" : hhmm, TIME_FMT); }
        catch (Exception e) { return LocalTime.of(9, 0); }
    }

    private LocalDate clampDay(LocalDate firstOfMonth, Integer day) {
        int d = day == null || day < 1 ? 1 : Math.min(day, firstOfMonth.lengthOfMonth());
        return firstOfMonth.withDayOfMonth(d);
    }

    private LocalDateTime computeNextDue(Integer day, String time, LocalDateTime from) {
        LocalDate first = from.toLocalDate().withDayOfMonth(1);
        LocalDateTime cand = clampDay(first, day).atTime(parseTime(time));
        if (!cand.isAfter(from)) {
            cand = clampDay(first.plusMonths(1), day).atTime(parseTime(time));
        }
        return cand;
    }

    private LocalDateTime nextAfter(LocalDate dueDate, Integer day, String time) {
        LocalDate first = dueDate.plusMonths(1).withDayOfMonth(1);
        return clampDay(first, day).atTime(parseTime(time));
    }

    private String randomToken() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) sb.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(RAND.nextInt(36)));
        return sb.toString();
    }

    private String genTaskNo(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = taskMapper.selectCount(new QueryWrapper<QrInspectionTask>().likeRight("task_no", prefix + date)).intValue() + 1;
        return prefix + date + String.format("%03d", seq);
    }

    private String jsonWrite(Object o) { try { return json.writeValueAsString(o); } catch (Exception e) { return "[]"; } }

    private List<Map<String, Object>> parseList(String s) {
        if (s == null || s.isBlank()) return new ArrayList<>();
        try { return json.readValue(s, new TypeReference<List<Map<String, Object>>>() {}); } catch (Exception e) { return new ArrayList<>(); }
    }

    private Map<String, Object> parseMap(String s) {
        if (s == null || s.isBlank()) return new LinkedHashMap<>();
        try { return json.readValue(s, new TypeReference<Map<String, Object>>() {}); } catch (Exception e) { return new LinkedHashMap<>(); }
    }

    private String str(Object o) { return o == null ? null : o.toString(); }

    private Long deptIdOf(String code) {
        try { SysDepartment d = deptMapper.selectOne(new QueryWrapper<SysDepartment>().eq("code", code)); return d == null ? null : d.getId(); }
        catch (Exception e) { return null; }
    }

    private String deptNameOf(String code) {
        try { SysDepartment d = deptMapper.selectOne(new QueryWrapper<SysDepartment>().eq("code", code)); return d == null ? code : d.getName(); }
        catch (Exception e) { return code; }
    }

    // ======================== 部门下拉 ========================

    public List<Map<String, Object>> listDepartments() {
        List<SysDepartment> list = deptMapper.selectList(new QueryWrapper<SysDepartment>()
                .eq("enabled", 1).orderByAsc("sort_order", "id"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysDepartment d : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("code", d.getCode());
            m.put("name", d.getName());
            m.put("deptGroup", d.getDeptGroup());
            out.add(m);
        }
        return out;
    }

    // ======================== 模板 CRUD（不再版本化） ========================

    public List<QrInspectionTemplate> listTemplates(String scene) {
        QueryWrapper<QrInspectionTemplate> q = new QueryWrapper<>();
        if (scene != null && !scene.isBlank()) q.eq("scene", scene);
        return templateMapper.selectList(q.orderByAsc("scene").orderByAsc("id"));
    }

    @Transactional
    public QrInspectionTemplate saveTemplate(QrInspectionTemplate t, String updatedBy) {
        t.setUpdatedBy(updatedBy);
        if (t.getVersion() == null) t.setVersion(1);
        if (t.getEnabled() == null) t.setEnabled(1);
        if (t.getId() == null) { templateMapper.insert(t); }
        else {
            QrInspectionTemplate prev = templateMapper.selectById(t.getId());
            if (prev == null) throw BizException.badRequest("模板不存在");
            templateMapper.updateById(t);
        }
        return t;
    }

    // ======================== 计划 CRUD ========================

    public List<QrInspectionPlan> listPlans(String scene) {
        QueryWrapper<QrInspectionPlan> q = new QueryWrapper<>();
        if (scene != null && !scene.isBlank()) q.eq("scene", scene);
        return planMapper.selectList(q.orderByAsc("scene").orderByAsc("dept_code"));
    }

    @Transactional
    public QrInspectionPlan savePlan(QrInspectionPlan p) {
        if (p.getScene() == null || p.getScene().isBlank()) throw BizException.badRequest("请选择巡检场景");
        if (p.getDayOfMonth() == null || p.getDayOfMonth() < 1 || p.getDayOfMonth() > 31)
            throw BizException.badRequest("每月巡检日期需在 1-31 之间");
        if (p.getPushTime() == null || p.getPushTime().isBlank()) p.setPushTime("09:00");
        try { LocalTime.parse(p.getPushTime(), TIME_FMT); }
        catch (Exception e) { throw BizException.badRequest("推送时间格式应为 HH:mm"); }

        if (p.getDeptId() == null && p.getDeptCode() != null) {
            SysDepartment d = deptMapper.selectOne(new QueryWrapper<SysDepartment>().eq("code", p.getDeptCode()));
            if (d == null) throw BizException.badRequest("部门不存在");
            p.setDeptId(d.getId());
        }
        if (p.getDeptCode() == null && p.getDeptId() != null) {
            SysDepartment d = deptMapper.selectById(p.getDeptId());
            if (d == null) throw BizException.badRequest("部门不存在");
            p.setDeptCode(d.getCode());
        }
        if (p.getDeptCode() == null) throw BizException.badRequest("请选择责任部门");
        if (p.getEnabled() == null) p.setEnabled(1);

        if (p.getId() == null) {
            p.setNextDueAt(computeNextDue(p.getDayOfMonth(), p.getPushTime(), LocalDateTime.now()));
            planMapper.insert(p);
        } else {
            QrInspectionPlan prev = planMapper.selectById(p.getId());
            if (prev == null) throw BizException.badRequest("计划不存在");
            boolean scheduleChanged = !Objects.equals(prev.getDayOfMonth(), p.getDayOfMonth())
                    || !Objects.equals(prev.getPushTime(), p.getPushTime());
            if (scheduleChanged || p.getNextDueAt() == null) {
                p.setNextDueAt(computeNextDue(p.getDayOfMonth(), p.getPushTime(), LocalDateTime.now()));
            }
            planMapper.updateById(p);
        }
        return p;
    }

    public List<QrInspectionPlanRound> planRounds(Long planId) {
        return roundMapper.listByPlan(planId);
    }

    // ======================== 任务查询 ========================

    public List<QrInspectionTask> listTasks() {
        return taskMapper.selectList(new QueryWrapper<QrInspectionTask>().orderByDesc("created_at"));
    }

    public Map<String, Object> taskDetail(Long id) {
        QrInspectionTask t = taskMapper.selectById(id);
        if (t == null) throw BizException.badRequest("任务不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("task", t);
        out.put("participatingDeptNames", deptNamesOf(t.getParticipatingDeptCodes()));

        List<QrInspectionTaskTarget> targets = taskTargetMapper.selectList(
                new QueryWrapper<QrInspectionTaskTarget>().eq("task_id", id).orderByDesc("id"));
        List<Map<String, Object>> targetRows = new ArrayList<>();
        for (QrInspectionTaskTarget tg : targets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", tg.getId());
            m.put("stallId", tg.getStallId());
            m.put("status", tg.getStatus());
            m.put("submittedAt", tg.getSubmittedAt());
            m.put("submittedBy", tg.getSubmittedBy());
            m.put("deptCode", tg.getSelectedDeptCode());
            Stall s = stallMapper.selectById(tg.getStallId());
            if (s != null) {
                m.put("stallCode", s.getStallCode());
                m.put("stallName", s.getStallName());
                m.put("floorCode", s.getFloorCode());
            }
            targetRows.add(m);
        }
        out.put("targets", targetRows);
        out.put("records", recordMapper.selectList(new QueryWrapper<QrInspectionRecord>().eq("task_id", id).orderByDesc("submitted_at")));
        out.put("issues", issueMapper.selectList(new QueryWrapper<QrInspectionIssue>().eq("task_id", id)));
        return out;
    }

    public QrInspectionIssue issueByToken(String token) {
        QrInspectionIssue i = issueMapper.byToken(token);
        if (i == null) throw BizException.badRequest("整改链接无效");
        return i;
    }

    // ======================== 巡检记录查询（仅管理员） ========================

    public Map<String, Object> listRecords(String scene, String keyword, LocalDate start, LocalDate end, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (scene != null && !scene.isBlank()) { where.append(" AND r.scene = ? "); args.add(scene); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (r.stall_code LIKE ? OR r.stall_name LIKE ? OR r.submitter_name LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw); args.add(kw); args.add(kw);
        }
        if (start != null) { where.append(" AND r.submitted_at >= ? "); args.add(start.atStartOfDay()); }
        if (end != null) { where.append(" AND r.submitted_at <= ? "); args.add(end.atTime(LocalTime.MAX)); }

        Object[] arr = args.toArray(new Object[0]);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM qr_inspection_record r" + where, Integer.class, arr);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.id, r.scene, r.plan_id AS planId, r.plan_round_id AS planRoundId, r.task_id AS taskId,"
                        + " r.stall_code AS stallCode, r.stall_name AS stallName, r.template_id AS templateId,"
                        + " t.name AS templateName, r.status, r.submitter_name AS submitterName,"
                        + " r.submitter_dept AS submitterDept, r.dept_code AS deptCode, r.submitted_at AS submittedAt,"
                        + " (SELECT COUNT(*) FROM qr_inspection_issue i WHERE i.record_id = r.id) AS issueCount"
                        + " FROM qr_inspection_record r LEFT JOIN qr_inspection_template t ON t.id = r.template_id"
                        + where + " ORDER BY r.submitted_at DESC, r.id DESC LIMIT ? OFFSET ?",
                appendArgs(arr, s, (p - 1) * s));
        for (Map<String, Object> row : rows) {
            String sc = str(row.get("scene"));
            row.put("sceneLabel", sceneLabel(sc));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total == null ? 0 : total);
        out.put("page", p);
        out.put("size", s);
        out.put("list", rows);
        return out;
    }

    public Map<String, Object> recordDetail(Long id) {
        QrInspectionRecord r = recordMapper.selectById(id);
        if (r == null) throw BizException.badRequest("巡检记录不存在");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("record", r);
        out.put("sceneLabel", sceneLabel(r.getScene()));
        out.put("templateName", r.getTemplateId() == null ? null : templateNameOf(r.getTemplateId()));
        out.put("fields", parseList(r.getTemplateSnapshot()));
        out.put("answers", parseMap(r.getAnswersJson()));
        out.put("photos", r.getPhotosJson());
        out.put("issues", issueMapper.selectList(new QueryWrapper<QrInspectionIssue>().eq("record_id", id)));
        return out;
    }

    private Object[] appendArgs(Object[] base, Object... tail) {
        Object[] all = new Object[base.length + tail.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(tail, 0, all, base.length, tail.length);
        return all;
    }

    // ======================== 报告 ========================
    public Map<String, Object> report(String scene, LocalDate start, LocalDate end, Long departmentId) {
        Map<String, Object> out = new LinkedHashMap<>();
        LocalDateTime s = start == null ? LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIN) : start.atStartOfDay();
        LocalDateTime e = end == null ? LocalDateTime.now() : end.atTime(LocalTime.MAX);
        QueryWrapper<QrInspectionRecord> rq = new QueryWrapper<>();
        if (scene != null && !scene.isBlank()) rq.eq("scene", scene);
        rq.between("submitted_at", s, e);
        List<QrInspectionRecord> records = recordMapper.selectList(rq);
        QueryWrapper<QrInspectionIssue> iq = new QueryWrapper<>();
        if (departmentId != null) iq.eq("department_id", departmentId);
        iq.between("created_at", s, e);
        List<QrInspectionIssue> issues = issueMapper.selectList(iq);
        long closedCount = issues.stream().filter(i -> "CLOSED".equals(i.getStatus())).count();
        out.put("periodStart", s);
        out.put("periodEnd", e);
        out.put("totalRecords", records.size());
        out.put("abnormalRecords", records.stream().filter(r -> "ABNORMAL".equals(r.getStatus())).count());
        out.put("totalIssues", issues.size());
        out.put("closedIssues", closedCount);
        out.put("pendingIssues", issues.stream().filter(i -> "PENDING".equals(i.getStatus())).count());
        out.put("remedyRate", issues.isEmpty() ? 1.0 : (double) closedCount / issues.size());
        Map<String, Map<String, Object>> byDept = new LinkedHashMap<>();
        for (QrInspectionIssue i : issues) {
            byDept.computeIfAbsent(i.getDeptCode(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("deptCode", i.getDeptCode());
                m.put("deptName", i.getDeptName());
                m.put("total", 0); m.put("closed", 0); m.put("pending", 0); return m;
            });
            Map<String, Object> d = byDept.get(i.getDeptCode());
            d.put("total", (Integer) d.get("total") + 1);
            if ("CLOSED".equals(i.getStatus())) d.put("closed", (Integer) d.get("closed") + 1);
            if ("PENDING".equals(i.getStatus())) d.put("pending", (Integer) d.get("pending") + 1);
        }
        out.put("byDepartment", new ArrayList<>(byDept.values()));
        Map<String, Integer> byCat = new LinkedHashMap<>();
        for (QrInspectionIssue i : issues) {
            for (Map<String, Object> it : parseList(i.getItemsJson())) {
                String c = str(it.get("category"));
                if (c == null || c.isBlank()) continue;
                byCat.merge(c, 1, Integer::sum);
            }
        }
        out.put("byCategory", byCat);
        return out;
    }
}
