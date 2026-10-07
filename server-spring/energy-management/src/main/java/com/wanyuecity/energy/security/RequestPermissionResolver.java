package com.wanyuecity.energy.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RequestPermissionResolver {
    public record Rule(String permissionCode, String bridgeRole, Set<String> acceptedRoles) {}

    public Rule resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean read = HttpMethod.GET.matches(method);
        if (!path.startsWith("/api/v1/")) return null;

        if (path.startsWith("/api/v1/dashboard")) return view("dashboard.view");
        if (path.startsWith("/api/v1/access-control")) {
            if (path.contains("/person-scans") || path.contains("/card-scans")) return manage("access.sync.import");
            if (path.endsWith("/write-mode")) return admin("access.sync.write");
            if (path.endsWith("/dashboard")) return view("access.dashboard.view");
            if (path.endsWith("/self/claim")) return new Rule("access.self.manage", "APPLICANT", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","VIEWER"));
            if (path.endsWith("/self")) return read
                    ? new Rule("access.self.view", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","VIEWER"))
                    : new Rule("access.self.manage", "APPLICANT", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","VIEWER"));
            if (path.contains("/events/mine")) return new Rule("access.event.view.self", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","VIEWER"));
            if (path.endsWith("/events/sync") || path.endsWith("/events/sync-status")) return manage("access.event.sync");
            if (path.contains("/events")) return view("access.event.view.all");
            if (path.contains("/visitors/mine")) return new Rule("visitor.view.own", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","VIEWER"));
            if (path.contains("/renewals/mine")) return read ? new Rule("access.self.view", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","VIEWER")) : new Rule("access.self.manage", "APPLICANT", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","VIEWER"));
            if (path.contains("/renewals")) return manage("visitor.approve");
            if (path.contains("/visitors/") && path.contains("/attachments")) return read ? new Rule("visitor.view.own", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","VIEWER")) : new Rule("visitor.create", "APPLICANT", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT"));
            if (path.matches(".*/visitors/[^/]+/decision$")) return manage("visitor.approve");
            if (path.matches(".*/visitors/[^/]+/revoke$")) return admin("access.person.revoke");
            if (path.matches(".*/visitors/[^/]+/permanent$")) return admin("access.test-data.delete");
            if (path.endsWith("/visitors") && !read) return new Rule("visitor.create", "APPLICANT", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT"));
            if (path.contains("/visitors")) return view("visitor.view.all");
            if (path.contains("/reviews")) return read ? manage("access.person.review") : manage("access.person.review");
            if (path.contains("/persons")) return read ? view("access.person.view") : manage("access.person.manage");
            if (path.contains("/devices") || path.contains("/points")) return read ? view("access.device.view") : manage("access.device.manage");
        }
        if (path.startsWith("/api/v1/construction-inspection-templates"))
            return read ? new Rule("construction.inspection.config.view", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER"))
                    : manage("construction.inspection.config.manage");
        if (path.startsWith("/api/v1/construction-workflows")) {
            if (path.endsWith("/permanent") || path.endsWith("/reset")) return admin("construction.delete");
            if (path.endsWith("/acceptance-request")) return new Rule("construction.acceptance.request", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","VIEWER"));
            if (path.endsWith("/acceptance-result") || path.endsWith("/close")) return new Rule("construction.acceptance.manage", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER"));
            if (path.contains("/attachments")) return read ? new Rule("construction.view", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","AUDITOR","VIEWER")) : new Rule("construction.attachment.upload", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","VIEWER"));
            if (path.contains("/inspections/security")) return new Rule("construction.inspect.security", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","VIEWER"));
            if (path.contains("/inspections/engineering")) return new Rule("construction.inspect.engineering", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER"));
            if (path.contains("/inspections/cleaning")) return new Rule("construction.inspect.cleaning", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","VIEWER"));
            if (path.endsWith("/complete")) return manage("construction.manage");
            if (!read) return applicant("construction.create");
            return new Rule("construction.view", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","AUDITOR","VIEWER"));
        }
        if (path.startsWith("/api/v1/workflows")) {
            if (path.endsWith("/permanent") || path.endsWith("/reset")) return admin("workflow.delete");
            if (path.contains("/match-resolution") || path.contains("/oa-process-no") || path.contains("/completion-remark")) return manage("workflow.manage");
            if (!read) return applicant("workflow.create");
            return new Rule("workflow.view", "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","PLATFORM_OPERATOR","AUDITOR","VIEWER"));
        }
        if (path.startsWith("/api/v1/verification/tasks/")) return null;
        if (path.startsWith("/api/v1/verification")) return new Rule(read ? "verification.view" : "verification.execute", "DEVICE_VERIFIER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","AUDITOR","VIEWER"));
        if (path.startsWith("/api/v1/merchants/code/") || path.endsWith("/code-preview")) return new Rule("merchant.query", "APPLICANT", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","AUDITOR","VIEWER"));
        if (path.startsWith("/api/v1/merchants")) return read ? new Rule("merchant.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","VIEWER")) : manage("merchant.manage");
        if (path.matches("/api/v1/stalls/[^/]+/engineering.*")) return read ? view("stall.engineering.view") : manage("stall.engineering.manage");
        if (path.matches("/api/v1/stalls/[^/]+/devices.*") || path.matches("/api/v1/devices/[^/]+/(stall|history).*")) return read ? new Rule("metering.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","AUDITOR","VIEWER")) : manage("metering.manage");
        if (path.startsWith("/api/v1/stalls")) {
            if (path.contains("/excel/")) return manage("stall.import_export");
            if (HttpMethod.DELETE.matches(method) && path.endsWith("/permanent")) return admin("stall.delete");
            return read ? new Rule("stall.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","AUDITOR","VIEWER")) : manage("stall.manage");
        }
        if (path.startsWith("/api/v1/accounts/merchant-code/")) return new Rule("merchant.query","APPLICANT",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","VIEWER"));
        if (path.startsWith("/api/v1/accounts")) return read
                ? new Rule("merchant.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","PLATFORM_OPERATOR","AUDITOR","VIEWER"))
                : manage("merchant.manage");
        if (path.startsWith("/api/v1/devices")) return new Rule("metering.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT","DEVICE_VERIFIER","AUDITOR","VIEWER"));
        if (path.startsWith("/api/v1/power-topology")) return read ? new Rule("facility.power.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER")) : manage("facility.power.manage");
        if (path.startsWith("/api/v1/facility-topologies/")) {
            String code = path.contains("/WATER_SUPPLY/") ? "water" : path.contains("/DRAINAGE/") ? "drainage" : path.contains("/OIL_EXHAUST/") ? "exhaust" : "fresh_air";
            return read ? new Rule("facility."+code+".view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER")) : manage("facility."+code+".manage");
        }
        if (path.startsWith("/api/v1/energy")) return energy(path, method, read);
        if (path.startsWith("/api/v1/data-exceptions")) return read ? view("exception.view") : manage("exception.manage");
        if (path.startsWith("/api/v1/inspections/public-qr/")) return null;
        if (path.startsWith("/api/v1/inspection-equipment/public-qr/")) return null;
        if (path.startsWith("/api/v1/inspection-equipment/categories")) return read ? view("inspection.equipment.view") : manage("inspection.category.manage");
        if (path.startsWith("/api/v1/inspection-equipment")) return read ? new Rule("inspection.equipment.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER")) : manage("inspection.equipment.manage");
        if (path.startsWith("/api/v1/inspections/dashboard")) return view("inspection.dashboard.view");
        if (path.startsWith("/api/v1/inspections/types")) return read ? view("inspection.task.view") : manage("inspection.type.manage");
        if (path.startsWith("/api/v1/inspections/templates")) return read ? view("inspection.task.view") : manage("inspection.template.manage");
        if (path.startsWith("/api/v1/inspections/plans") || path.endsWith("/tasks/generate")) return read ? view("inspection.task.view") : manage("inspection.plan.manage");
        if (path.startsWith("/api/v1/inspections/issues")) return read ? view("inspection.task.view") : new Rule(path.endsWith("/review") ? "inspection.issue.review" : "inspection.issue.manage","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER"));
        if (path.startsWith("/api/v1/inspections/tasks")) return read ? new Rule("inspection.task.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER")) : new Rule("inspection.task.execute","DEVICE_VERIFIER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER"));
        if (path.startsWith("/api/v1/inspections/targets")) return view("inspection.task.view");
        if (path.startsWith("/api/v1/platform/config")) return read ? view("integration.config.view") : manage("integration.config.manage");
        if (path.startsWith("/api/v1/platform/sync-logs")) return view("integration.sync.view");
        if (path.startsWith("/api/v1/platform/sync")) return read ? view("integration.sync.view") : manage("integration.sync.execute");
        if (path.startsWith("/api/v1/users") || path.startsWith("/api/v1/roles") || path.startsWith("/api/v1/menus")) {
            if (path.contains("/permissions")) return admin("system.user.permission.manage");
            return admin(read ? "system.user.view" : "system.user.manage");
        }
        if (path.startsWith("/api/v1/dictionaries") || path.startsWith("/api/v1/cable-specifications") || path.startsWith("/api/v1/power-switch-specifications") || path.startsWith("/api/v1/air-conditioner-specifications"))
            return read ? view("system.dictionary.view") : manage("system.dictionary.manage");
        if (path.startsWith("/api/v1/webhooks/config")) return read ? view("system.webhook.view") : manage("system.webhook.manage");
        if (path.startsWith("/api/v1/audit-logs")) return view("system.audit.view");
        if (path.matches("/api/v1/system/migration/exports/[^/]+/download")) return null;
        if (path.startsWith("/api/v1/system/migration")) return admin("system.migration.manage");
        if (path.startsWith("/api/v1/configs")) return manage("integration.config.manage");
        return null;
    }

    private Rule energy(String path, String method, boolean read) {
        if (path.contains("/bills")) return read ? new Rule("energy.bill.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER")) : manage("energy.bill.manage");
        if (path.contains("/usage/sync")) return read ? view("energy.sync.view") : manage("energy.sync.execute");
        if (path.endsWith("/analysis")) return new Rule("energy.analysis.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER"));
        if (path.contains("/dimensions") || path.contains("/dimension-values") || path.contains("/groups") || path.contains("/devices") || path.contains("/metrics"))
            return read ? new Rule("energy.dimension.view","VIEWER",Set.of("SUPER_ADMIN","SYSTEM_ADMIN","DEVICE_VERIFIER","VIEWER")) : manage("energy.dimension.manage");
        return view("energy.dashboard.view");
    }

    private Rule view(String code) { return new Rule(code, "VIEWER", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","VIEWER")); }
    private Rule manage(String code) { return new Rule(code, "SYSTEM_ADMIN", Set.of("SUPER_ADMIN","SYSTEM_ADMIN")); }
    private Rule admin(String code) { return new Rule(code, "SUPER_ADMIN", Set.of("SUPER_ADMIN")); }
    private Rule applicant(String code) { return new Rule(code, "APPLICANT", Set.of("SUPER_ADMIN","SYSTEM_ADMIN","APPLICANT")); }
}
