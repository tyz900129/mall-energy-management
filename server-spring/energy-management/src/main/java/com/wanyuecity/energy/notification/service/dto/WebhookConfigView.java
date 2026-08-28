package com.wanyuecity.energy.notification.service.dto; import lombok.Data; import java.time.LocalDateTime;
@Data public class WebhookConfigView {private Long id;private String name;private String provider;private Boolean webhookUrlConfigured;private Integer enabled;private String eventTypes;private LocalDateTime updatedAt;}

