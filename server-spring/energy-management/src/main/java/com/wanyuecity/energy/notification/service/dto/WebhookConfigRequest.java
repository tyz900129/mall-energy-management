package com.wanyuecity.energy.notification.service.dto; import jakarta.validation.constraints.*; import lombok.Data;
@Data public class WebhookConfigRequest {@NotBlank private String name;@NotBlank private String provider;private String webhookUrl;private Boolean enabled;private String eventTypes;}
