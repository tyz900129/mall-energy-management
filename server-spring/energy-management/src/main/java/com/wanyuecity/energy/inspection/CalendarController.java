package com.wanyuecity.energy.inspection;

import com.wanyuecity.energy.core.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {
    private final InspectionService service;

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','PROPERTY_MANAGER','OPS_PATROL_MANAGER','DEVICE_VERIFIER','CS_AGENT','CS_MANAGER','VIEWER','APPLICANT')")
    public ApiResponse<Map<String,Object>> tasks(@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) Long deptId,@RequestParam(required=false) String category,@RequestParam(required=false) String status,@RequestParam(required=false) String keyword,Authentication auth){
        LocalDate f=(from==null||from.isBlank())?null:LocalDate.parse(from);
        LocalDate t=(to==null||to.isBlank())?null:LocalDate.parse(to);
        return ApiResponse.ok(service.calendarTasks(f,t,deptId,category,status,keyword,canReadOpsPatrol(auth)));
    }

    /** 外部来源按只读权限展示：管理员或具备营运巡场查看权限者可见。 */
    private boolean canReadOpsPatrol(Authentication auth){
        if(auth==null) return false;
        return auth.getAuthorities().stream().map(x->x.getAuthority()).anyMatch(a->
                "ROLE_SUPER_ADMIN".equals(a)||"ROLE_SYSTEM_ADMIN".equals(a)||"ops.patrol.report".equals(a)||"PERMISSION_BRIDGE:ops.patrol.report".equals(a));
    }

    @GetMapping("/generation-logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SYSTEM_ADMIN','PROPERTY_MANAGER','VIEWER')")
    public ApiResponse<List<Map<String,Object>>> logs(@RequestParam(required=false) Long planId,@RequestParam(required=false) String from,@RequestParam(required=false) String to){
        LocalDate f=(from==null||from.isBlank())?null:LocalDate.parse(from);
        LocalDate t=(to==null||to.isBlank())?null:LocalDate.parse(to);
        return ApiResponse.ok(service.generationLogs(planId,f,t));
    }
}
