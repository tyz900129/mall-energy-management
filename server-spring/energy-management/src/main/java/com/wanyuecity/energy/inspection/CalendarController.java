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
        return ApiResponse.ok(service.calendarTasks(f,t,deptId,category,status,keyword,canReadOpsPatrol(auth)));
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

    /** 日历计划责任对象选项：避免日历管理岗依赖巡检配置权限。 */
    @GetMapping("/owner-options")
    public ApiResponse<Map<String,Object>> ownerOptions(){return ApiResponse.ok(service.ownerOptions());}

    @GetMapping("/plans")
    public ApiResponse<List<Map<String,Object>>> plans(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String category){
        return ApiResponse.ok(service.calendarPlans(keyword,status,category));
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<Map<String,Object>> plan(@PathVariable Long id){return ApiResponse.ok(service.calendarPlan(id));}

    @PostMapping("/plans")
    public ApiResponse<Map<String,Object>> createPlan(@RequestBody Map<String,Object> body){return ApiResponse.ok(service.saveCalendarPlan(null,body));}

    @PutMapping("/plans/{id}")
    public ApiResponse<Map<String,Object>> updatePlan(@PathVariable Long id,@RequestBody Map<String,Object> body){return ApiResponse.ok(service.saveCalendarPlan(id,body));}

    @PostMapping("/plans/{id}/toggle")
    public ApiResponse<Map<String,Object>> toggle(@PathVariable Long id,@RequestParam(defaultValue="true") boolean enabled){
        return ApiResponse.ok(service.toggleCalendarPlan(id,enabled));
    }

    /** 立即生成当天任务；重复调用由 period_key 幂等去重。 */
    @PostMapping("/plans/{id}/generate")
    public ApiResponse<Map<String,Object>> generate(@PathVariable Long id,@RequestParam(required=false) String date){
        service.generate(id,(date==null||date.isBlank())?LocalDate.now():LocalDate.parse(date));
        return ApiResponse.ok(service.calendarPlan(id));
    }
}
