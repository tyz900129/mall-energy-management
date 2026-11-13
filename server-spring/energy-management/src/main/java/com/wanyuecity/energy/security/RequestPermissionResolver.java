package com.wanyuecity.energy.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 路径 → 权限码映射。V108 起取消「角色桥」，返回的 Rule 只描述需要的权限码；
 * 未映射的 /api/v1 路径返回 {@link Rule#unmapped()}，由拦截器默认拒绝（403），
 * 公开路径必须显式返回 {@link Rule#PUBLIC}。
 */
@Component
public class RequestPermissionResolver {

    public record Rule(Set<String> permissions, boolean publicAccess) {
        public static final Rule PUBLIC = new Rule(Set.of(), true);
        public static final Rule UNMAPPED = new Rule(Set.of(), false);

        public static Rule of(String... codes) {
            return new Rule(Set.of(codes), false);
        }
    }

    public Rule resolve(HttpServletRequest request) {
        // getRequestURI() 带 context-path（生产为 /energy），这里统一剥离后再匹配 /api/v1
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String method = request.getMethod();
        boolean read = HttpMethod.GET.matches(method);
        if (!path.startsWith("/api/v1/")) return Rule.PUBLIC;
        // ---- 公开路径（与 SecurityConfig permitAll 一一对应）----
        if (path.startsWith("/api/v1/operation-public/")) return Rule.PUBLIC;
        if (path.startsWith("/api/v1/patrol-public/")) return Rule.PUBLIC;
        if (path.startsWith("/api/v1/ops-patrol-public/")) return Rule.PUBLIC;
        // 日历任务移动回执：token 校验、脱敏与限流都在 CalendarPublicService 内完成
        if (path.startsWith("/api/v1/calendar-public/")) return Rule.PUBLIC;
        if (path.startsWith("/api/v1/construction-public/")) return Rule.PUBLIC;
        if (path.startsWith("/api/v1/access-public/")) return Rule.PUBLIC;
        if (path.startsWith("/api/v1/inspections/public-qr/")) return Rule.PUBLIC;
        if (path.startsWith("/api/v1/inspection-equipment/public-qr/")) return Rule.PUBLIC;
        if (path.startsWith("/api/v1/verification/tasks/")) return Rule.PUBLIC;
        if (path.matches("/api/v1/system/migration/exports/[^/]+/download")) return Rule.PUBLIC;
        // 二维码巡检 H5：扫码入口与整改页（token 校验在 QrInspectionService 内）
        if (path.matches("/api/v1/qr-inspection/stalls/[^/]+/entry")) return Rule.PUBLIC;
        if (path.matches("/api/v1/qr-inspection/rectify/[^/]+")) return Rule.PUBLIC;
        // 旧巡检 H5 免登录入口：token 校验在业务层（roundByToken 校验当日有效性）
        if (path.equals("/api/v1/patrol/entry")) return Rule.PUBLIC;
        if (path.matches("/api/v1/patrol/points/\\d+/form")) return Rule.PUBLIC;
        if (path.equals("/api/v1/patrol/submissions") && !read) return Rule.PUBLIC;

        if (path.startsWith("/api/v1/calendar/")) {
            if (path.contains("/generation-logs")) return Rule.of("calendar.report");
            if (read) return Rule.of("calendar.view");
            return Rule.of("calendar.manage");
        }

        if (path.startsWith("/api/v1/ops-patrol/")) {
            if (path.contains("/depts") || path.contains("/webhooks") || path.contains("/floor-options"))
                return read ? opsView() : opsDept();
            if (path.contains("/slots") || path.contains("/check-items") || path.contains("/duties"))
                return read ? opsView() : opsManage("ops.patrol.config");
            if (path.contains("/issues")) {
                if (path.endsWith("/assign") || path.endsWith("/rectify")) return opsManage("ops.patrol.assign");
                if (path.endsWith("/review")) return opsManage("ops.patrol.review");
                return read ? opsView() : opsManage("ops.patrol.assign");
            }
            if (path.contains("/reports")) return opsReport();
            if (path.contains("/tasks") && !read) return opsManage("ops.patrol.config");
            return opsView();
        }

        if (path.startsWith("/api/v1/patrol/")) {
            if (path.endsWith("/entry") || path.contains("/form")) return Rule.of("patrol.submit");
            if (path.startsWith("/api/v1/patrol/submissions")) return read ? Rule.of("patrol.issue.view") : Rule.of("patrol.submit");
            if (path.contains("/issues")) {
                if (path.endsWith("/assign")) return Rule.of("patrol.issue.assign");
                if (path.endsWith("/review")) return Rule.of("patrol.issue.review");
                return Rule.of("patrol.issue.view");
            }
            return read ? Rule.of("patrol.point.view") : Rule.of("patrol.config");
        }

        if (path.startsWith("/api/v1/dashboard/stats")) return Rule.of("dashboard.stats.base.view");
        if (path.startsWith("/api/v1/dashboard")) return Rule.of("dashboard.view");

        // 客诉（Controller 内已统一使用 hasAuthority，这里补齐路径级映射以支持默认拒绝）
        if (path.startsWith("/api/v1/complaints")) return complaint(path, method, read);

        // 组织架构：原仅超管/系管，对应 system.dept.view / system.dept.manage
        if (path.startsWith("/api/v1/departments")) return read ? Rule.of("system.dept.view") : Rule.of("system.dept.manage");

        // 二维码巡检：提交类接口允许持任一巡检提交/整改权限的岗位（WY_*/QD_*/RD_* 等）
        if (path.startsWith("/api/v1/qr-inspection/")) {
            if (path.startsWith("/api/v1/qr-inspection/context")
                    || path.startsWith("/api/v1/qr-inspection/stalls/search")
                    || path.equals("/api/v1/qr-inspection/submit")
                    || path.matches("/api/v1/qr-inspection/stalls/[^/]+/submit"))
                return Rule.of("qr.inspection.view", "qr.inspection.submit.joint", "qr.inspection.submit.warehouse", "qr.rectify");
            if (path.equals("/api/v1/qr-inspection/departments"))
                return Rule.of("qr.inspection.config", "qr.inspection.plan.manage", "qr.inspection.view");
            if (path.startsWith("/api/v1/qr-inspection/templates")) return Rule.of("qr.inspection.config");
            if (path.startsWith("/api/v1/qr-inspection/plans")) return read
                    ? Rule.of("qr.inspection.view", "qr.inspection.plan.manage")
                    : Rule.of("qr.inspection.plan.manage");
            if (path.startsWith("/api/v1/qr-inspection/joint/")) return Rule.of("qr.inspection.task.create");
            if (path.matches("/api/v1/qr-inspection/tasks/[^/]+/close")) return Rule.of("qr.inspection.task.create");
            if (path.startsWith("/api/v1/qr-inspection/tasks")) return Rule.of("qr.inspection.view", "qr.inspection.task.create");
            if (path.startsWith("/api/v1/qr-inspection/report")) return Rule.of("qr.inspection.view");
            // 巡检记录台账仍保留 Controller 内的超管/系管角色校验，这里只做路径级兜底
            if (path.startsWith("/api/v1/qr-inspection/records")) return Rule.of("qr.inspection.view");
        }

        if (path.startsWith("/api/v1/operations/form-images")) return read
                ? Rule.of("operation.form.image.view") : Rule.of("operation.form.image.upload");
        if (path.startsWith("/api/v1/operations/merchant-responsibilities"))
            return read ? Rule.of("operation.merchant.view") : Rule.of("operation.merchant.manage");
        if (path.startsWith("/api/v1/operations/dashboard")) return Rule.of("operation.dashboard.view");
        if (path.startsWith("/api/v1/operations/accountability-orders"))
            return read ? Rule.of("operation.accountability.view") : Rule.of("operation.accountability.manage");
        if (path.startsWith("/api/v1/operations/violation-items"))
            return read ? Rule.of("operation.violation.view") : Rule.of("operation.violation.manage");
        if (path.startsWith("/api/v1/operations/property-inspections")) {
            if (path.contains("/templates")) return read ? Rule.of("operation.property.template.view") : Rule.of("operation.property.template.manage");
            if (!read) return Rule.of("operation.clearance.review");
            return Rule.of("operation.property.view");
        }
        if (path.startsWith("/api/v1/operations/clearances")) {
            if (path.endsWith("/export")) return Rule.of("operation.clearance.export");
            if (path.endsWith("/settings")) return Rule.of("operation.clearance.setting.manage");
            if (path.endsWith("/period")) return Rule.of("operation.clearance.submit");
            if (!read) return Rule.of("operation.clearance.submit");
            return Rule.of("operation.clearance.view");
        }
        if (path.startsWith("/api/v1/operations/clearance-templates")) {
            if (path.contains("/stall/")) return Rule.of("operation.clearance.submit");
            return read ? Rule.of("operation.clearance.template.view") : Rule.of("operation.clearance.template.manage");
        }
        if (path.startsWith("/api/v1/access-control")) {
            if (path.contains("/person-scans") || path.contains("/card-scans")) return Rule.of("access.sync.import");
            if (path.endsWith("/write-mode")) return Rule.of("access.sync.write");
            if (path.endsWith("/dashboard")) return Rule.of("access.dashboard.view");
            if (path.endsWith("/self/claim")) return Rule.of("access.self.manage");
            if (path.endsWith("/self")) return read ? Rule.of("access.self.view") : Rule.of("access.self.manage");
            if (path.contains("/events/mine")) return Rule.of("access.event.view.self");
            if (path.endsWith("/events/sync") || path.endsWith("/events/sync-status")) return Rule.of("access.event.sync");
            // 通行记录缩略图/原图：与「我的通行记录」用同一门槛；是否属于本人由 Controller 二次校验
            if (path.matches(".*/events/[0-9]+/(thumbnail|snapshot)$")) return Rule.of("access.event.view.self", "access.event.view.all");
            if (path.contains("/events")) return Rule.of("access.event.view.all");
            if (path.contains("/visitors/mine")) return Rule.of("visitor.view.own");
            if (path.contains("/renewals/mine")) return read ? Rule.of("access.self.view") : Rule.of("access.self.manage");
            if (path.contains("/renewals")) return Rule.of("visitor.approve");
            if (path.contains("/visitors/") && path.contains("/attachments")) return read
                    ? Rule.of("visitor.view.own", "visitor.view.all")
                    : Rule.of("visitor.create", "visitor.view.all");
            if (path.matches(".*/visitors/[^/]+/decision$")) return Rule.of("visitor.approve");
            if (path.matches(".*/visitors/[^/]+/revoke$")) return Rule.of("access.person.revoke");
            if (path.matches(".*/visitors/[^/]+/permanent$")) return Rule.of("access.test-data.delete");
            if (path.endsWith("/visitors") && !read) return Rule.of("visitor.create");
            if (path.contains("/visitors")) return Rule.of("visitor.view.all");
            if (path.contains("/reviews")) return Rule.of("access.person.review");
            if (path.contains("/persons")) {
                if (path.matches(".*/persons/[^/]+/face$")) return Rule.of("access.person.sensitive.view");
                if (path.matches(".*/persons/[^/]+/permanent$")) return Rule.of("access.test-data.delete");
                return read ? Rule.of("access.person.view") : Rule.of("access.person.manage");
            }
            if (path.contains("/devices") || path.contains("/points")) return read ? Rule.of("access.device.view") : Rule.of("access.device.manage");
        }
        if (path.startsWith("/api/v1/construction-inspection-templates"))
            // 扫码巡检页需要读取模板；有任一施工巡检权限即可读，配置权限仍可管理
            return read ? Rule.of("construction.inspection.config.view", "construction.inspect.security",
                    "construction.inspect.engineering", "construction.inspect.cleaning", "construction.view")
                    : Rule.of("construction.inspection.config.manage");
        if (path.startsWith("/api/v1/construction-workflows")) {
            if (path.endsWith("/permanent") || path.endsWith("/reset")) return Rule.of("construction.delete");
            if (path.endsWith("/acceptance-request")) return Rule.of("construction.acceptance.request");
            if (path.endsWith("/acceptance-result") || path.endsWith("/close")) return Rule.of("construction.acceptance.manage");
            if (path.contains("/attachments")) return read ? Rule.of("construction.view") : Rule.of("construction.attachment.upload");
            if (path.contains("/inspections/security")) return Rule.of("construction.inspect.security");
            if (path.contains("/inspections/engineering")) return Rule.of("construction.inspect.engineering");
            if (path.contains("/inspections/cleaning")) return Rule.of("construction.inspect.cleaning");
            if (path.contains("/inspections")) return Rule.of("construction.inspect.engineering", "construction.inspect.security", "construction.inspect.cleaning");
            if (path.endsWith("/complete")) return Rule.of("construction.manage");
            if (!read) return Rule.of("construction.create");
            return Rule.of("construction.view");
        }
        if (path.startsWith("/api/v1/workflows")) {
            if (path.endsWith("/permanent") || path.endsWith("/reset")) return Rule.of("workflow.delete");
            if (path.contains("/match-resolution") || path.contains("/oa-process-no") || path.contains("/completion-remark"))
                return Rule.of("workflow.manage");
            if (!read) return Rule.of("workflow.create");
            return Rule.of("workflow.view");
        }
        if (path.startsWith("/api/v1/verification")) return read ? Rule.of("verification.view") : Rule.of("verification.execute");
        if (path.startsWith("/api/v1/merchants/code/") || path.endsWith("/code-preview")) return Rule.of("merchant.query");
        if (path.startsWith("/api/v1/merchants")) return read ? Rule.of("merchant.view") : Rule.of("merchant.manage");
        if (path.matches("/api/v1/stalls/[^/]+/engineering.*")) return read ? Rule.of("stall.engineering.view") : Rule.of("stall.engineering.manage");
        if (path.matches("/api/v1/stalls/[^/]+/devices.*") || path.matches("/api/v1/devices/[^/]+/(stall|history).*"))
            return read ? Rule.of("metering.view") : Rule.of("metering.manage");
        if (path.startsWith("/api/v1/stalls")) {
            if (path.contains("/excel/")) return Rule.of("stall.import_export");
            if (HttpMethod.DELETE.matches(method) && path.endsWith("/permanent")) return Rule.of("stall.delete");
            return read ? Rule.of("stall.view") : Rule.of("stall.manage");
        }
        if (path.startsWith("/api/v1/accounts/merchant-code/")) return Rule.of("merchant.query");
        if (path.startsWith("/api/v1/accounts")) return read ? Rule.of("merchant.view") : Rule.of("merchant.manage");
        if (path.startsWith("/api/v1/devices")) return Rule.of("metering.view");
        if (path.startsWith("/api/v1/power-topology")) return read ? Rule.of("facility.power.view") : Rule.of("facility.power.manage");
        if (path.startsWith("/api/v1/facility-topologies/")) {
            String code = path.contains("/WATER_SUPPLY/") ? "water" : path.contains("/DRAINAGE/") ? "drainage" : path.contains("/OIL_EXHAUST/") ? "exhaust" : "fresh_air";
            return read ? Rule.of("facility." + code + ".view") : Rule.of("facility." + code + ".manage");
        }
        if (path.startsWith("/api/v1/energy")) return energy(path, method, read);
        if (path.startsWith("/api/v1/data-exceptions")) return read ? Rule.of("exception.view") : Rule.of("exception.manage");
        if (path.startsWith("/api/v1/inspection-equipment/categories")) return read ? Rule.of("inspection.equipment.view") : Rule.of("inspection.category.manage");
        if (path.startsWith("/api/v1/inspection-equipment")) return read ? Rule.of("inspection.equipment.view") : Rule.of("inspection.equipment.manage");
        if (path.startsWith("/api/v1/inspections/dashboard")) return Rule.of("inspection.dashboard.view");
        if (path.startsWith("/api/v1/inspections/types")) return read ? Rule.of("inspection.task.view") : Rule.of("inspection.type.manage");
        if (path.startsWith("/api/v1/inspections/templates")) return read ? Rule.of("inspection.task.view") : Rule.of("inspection.template.manage");
        if (path.startsWith("/api/v1/inspections/plans") || path.endsWith("/tasks/generate")) return read ? Rule.of("inspection.task.view") : Rule.of("inspection.plan.manage");
        if (path.endsWith("/tasks/reassign")) return Rule.of("inspection.plan.manage");
        if (path.startsWith("/api/v1/inspections/issues")) return read
                ? Rule.of("inspection.task.view")
                : Rule.of(path.endsWith("/review") ? "inspection.issue.review" : "inspection.issue.manage");
        if (path.startsWith("/api/v1/inspections/tasks")) return read ? Rule.of("inspection.task.view") : Rule.of("inspection.task.execute");
        if (path.startsWith("/api/v1/inspections/targets") || path.startsWith("/api/v1/inspections/owner-options")) return Rule.of("inspection.task.view");
        if (path.startsWith("/api/v1/platform/config")) return read ? Rule.of("integration.config.view") : Rule.of("integration.config.manage");
        if (path.startsWith("/api/v1/platform/sync-logs")) return Rule.of("integration.sync.view");
        if (path.startsWith("/api/v1/platform/sync")) return read ? Rule.of("integration.sync.view") : Rule.of("integration.sync.execute");
        if (path.startsWith("/api/v1/users") || path.startsWith("/api/v1/roles") || path.startsWith("/api/v1/menus")) {
            if (path.contains("/permissions")) return Rule.of("system.user.permission.manage");
            return Rule.of(read ? "system.user.view" : "system.user.manage");
        }
        if (path.startsWith("/api/v1/dictionaries") && read && "BUSINESS_TYPE".equalsIgnoreCase(request.getParameter("type")))
            return Rule.of("workflow.view");
        if (path.startsWith("/api/v1/dictionaries") || path.startsWith("/api/v1/cable-specifications") || path.startsWith("/api/v1/power-switch-specifications") || path.startsWith("/api/v1/air-conditioner-specifications"))
            return read ? Rule.of("system.dictionary.view") : Rule.of("system.dictionary.manage");
        if (path.startsWith("/api/v1/webhooks/config")) return read ? Rule.of("system.webhook.view") : Rule.of("system.webhook.manage");
        if (path.startsWith("/api/v1/audit-logs")) return Rule.of("system.audit.view");
        if (path.startsWith("/api/v1/system/migration")) return Rule.of("system.migration.manage");
        if (path.startsWith("/api/v1/configs")) return Rule.of("integration.config.manage");
        // 默认拒绝：任何未显式映射的 /api/v1 路径都返回 403（不因路径未命中而放行）
        return Rule.UNMAPPED;
    }

    private Rule complaint(String path, String method, boolean read) {
        if (path.endsWith("/stats/export")) return Rule.of("complaint.export", "complaint.stats.view");
        if (path.contains("/stats")) return Rule.of("complaint.stats.view");
        if (path.startsWith("/api/v1/complaints/import")) return Rule.of("complaint.import");
        if (path.startsWith("/api/v1/complaints/export")) return Rule.of("complaint.export");
        if (path.contains("/options")) return read ? Rule.of("complaint.view", "complaint.create") : Rule.of("complaint.create", "complaint.brand.manage");
        if (path.contains("/staff")) return read ? Rule.of("complaint.view", "complaint.staff.manage") : Rule.of("complaint.staff.manage");
        if (path.contains("/attachments")) return read ? Rule.of("complaint.view") : Rule.of("complaint.create", "complaint.update");
        if (path.endsWith("/close")) return Rule.of("complaint.close");
        if (path.endsWith("/status")) return Rule.of("complaint.update");
        if (HttpMethod.DELETE.matches(method)) return Rule.of("complaint.delete");
        if (HttpMethod.POST.matches(method)) return Rule.of("complaint.create");
        if (HttpMethod.PUT.matches(method)) return Rule.of("complaint.update");
        return Rule.of("complaint.view");
    }

    private Rule energy(String path, String method, boolean read) {
        if (path.contains("/account-anomalies")) return read ? Rule.of("energy.anomaly.view") : Rule.of("energy.anomaly.manage");
        if (path.contains("/balance-warnings")) return read ? Rule.of("energy.balance-warning.view") : Rule.of("energy.balance-warning.manage");
        if (path.startsWith("/api/v1/energy/arrears")) return read ? Rule.of("energy.arrears.view") : Rule.of("energy.arrears.manage");
        if (path.contains("/bills")) return read ? Rule.of("energy.bill.view") : Rule.of("energy.bill.manage");
        if (path.contains("/usage/sync")) return read ? Rule.of("energy.sync.view") : Rule.of("energy.sync.execute");
        if (path.endsWith("/analysis")) return Rule.of("energy.analysis.view");
        if (path.contains("/dimensions") || path.contains("/dimension-values") || path.contains("/groups") || path.contains("/devices") || path.contains("/metrics"))
            return read ? Rule.of("energy.dimension.view") : Rule.of("energy.dimension.manage");
        return Rule.of("energy.dashboard.view");
    }

    private Rule opsView() { return Rule.of("ops.patrol.report"); }
    private Rule opsDept() { return Rule.of("ops.patrol.dept"); }
    private Rule opsManage(String code) { return Rule.of(code); }
    private Rule opsReport() { return Rule.of("ops.patrol.report"); }
}
