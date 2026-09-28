package com.wanyuecity.energy.workflow.service;
import com.wanyuecity.energy.core.BizException;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;
@Component public class WorkflowStateMachine {
    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("DRAFT", Set.of("FIELD_CHECK_PENDING", "CANCELLED")),
            Map.entry("FIELD_CHECK_PENDING", Set.of("FIELD_CHECKING", "CANCELLED")),
            Map.entry("FIELD_CHECKING", Set.of("FIELD_VERIFIED", "FIELD_EXCEPTION", "CANCELLED")),
            Map.entry("FIELD_VERIFIED", Set.of("WAITING_OA_SUBMISSION")),
            Map.entry("WAITING_OA_SUBMISSION", Set.of("WAITING_PLATFORM_CHANGE", "CANCELLED")),
            Map.entry("WAITING_PLATFORM_CHANGE", Set.of("PLATFORM_VERIFYING", "CANCELLED")),
            Map.entry("PLATFORM_VERIFYING", Set.of("VERIFIED", "PARTIAL_MATCH", "EXCEPTION")),
            Map.entry("VERIFIED", Set.of("COMPLETED")));
    public void requireTransition(String from, String to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to))
            throw BizException.badRequest("invalid workflow transition: " + from + " -> " + to);
    }
}
