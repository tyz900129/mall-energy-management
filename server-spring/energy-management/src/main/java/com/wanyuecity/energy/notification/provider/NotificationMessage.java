package com.wanyuecity.energy.notification.provider;

import java.util.List;

public record NotificationMessage(String eventType, Long workflowId, String content, String auditPayload, List<String> imageUrls) {
    public NotificationMessage(String eventType, Long workflowId, String content, String auditPayload) {
        this(eventType, workflowId, content, auditPayload, List.of());
    }
}
