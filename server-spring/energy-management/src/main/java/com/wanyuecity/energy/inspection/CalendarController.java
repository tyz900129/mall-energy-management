package com.wanyuecity.energy.inspection;

import com.wanyuecity.energy.core.ApiResponse;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<Map<String,Object>> tasks(@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) Long deptId,@RequestParam(required=false) String category,@RequestParam(required=false) String status,@RequestParam(required=false) String keyword,Authentication auth){
        LocalDate f=(from==null||from.isBlank())?null:LocalDate.parse(from);
        LocalDate t=(to==null||to.isBlank())?null:LocalDate.parse(to);
        return ApiResponse.ok(service.calendarTasks(f,t,deptId,category,status,keyword,canReadOpsPatrol(auth),auth.getName()));
    }

    /** 外部来源按只读权限展示：管理员或具备营运巡场查看权限者可见。 */
    private boolean canReadOpsPatrol(Authentication auth){
        if(auth==null) return false;
        return auth.getAuthorities().stream().map(x->x.getAuthority()).anyMatch("ops.patrol.report"::equals);
    }

    @GetMapping("/generation-logs")
    public ApiResponse<List<Map<String,Object>>> logs(@RequestParam(required=false) Long planId,@RequestParam(required=false) String from,@RequestParam(required=false) String to){
        LocalDate f=(from==null||from.isBlank())?null:LocalDate.parse(from);
        LocalDate t=(to==null||to.isBlank())?null:LocalDate.parse(to);
        return ApiResponse.ok(service.generationLogs(planId,f,t));
    }

    /** 日历计划责任对象选项：按当前用户数据范围收敛，避免越权选择责任对象。 */
    @GetMapping("/owner-options")
    public ApiResponse<Map<String,Object>> ownerOptions(Authentication auth){return ApiResponse.ok(service.ownerOptions(auth.getName()));}

    @GetMapping("/plans")
    public ApiResponse<List<Map<String,Object>>> plans(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String category,Authentication auth){
        return ApiResponse.ok(service.calendarPlans(keyword,status,category,auth.getName()));
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<Map<String,Object>> plan(@PathVariable Long id,Authentication auth){return ApiResponse.ok(service.calendarPlan(id,auth.getName()));}

    @PostMapping("/plans")
    public ApiResponse<Map<String,Object>> createPlan(@RequestBody Map<String,Object> body,Authentication auth){return ApiResponse.ok(service.saveCalendarPlan(null,body,auth.getName()));}

    @PutMapping("/plans/{id}")
    public ApiResponse<Map<String,Object>> updatePlan(@PathVariable Long id,@RequestBody Map<String,Object> body,Authentication auth){return ApiResponse.ok(service.saveCalendarPlan(id,body,auth.getName()));}

    @PostMapping("/plans/{id}/toggle")
    public ApiResponse<Map<String,Object>> toggle(@PathVariable Long id,@RequestParam(defaultValue="true") boolean enabled,Authentication auth){
        return ApiResponse.ok(service.toggleCalendarPlan(id,enabled,auth.getName()));
    }

    /** 立即生成当天任务；重复调用由 period_key 幂等去重。 */
    @PostMapping("/plans/{id}/generate")
    public ApiResponse<Map<String,Object>> generate(@PathVariable Long id,@RequestParam(required=false) String date,Authentication auth){
        service.calendarPlan(id,auth.getName());
        service.generate(id,(date==null||date.isBlank())?LocalDate.now():LocalDate.parse(date));
        return ApiResponse.ok(service.calendarPlan(id,auth.getName()));
    }
}
