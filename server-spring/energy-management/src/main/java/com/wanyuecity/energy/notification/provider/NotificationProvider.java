package com.wanyuecity.energy.notification.provider; public interface NotificationProvider {String provider();SendResult send(String webhookUrl,NotificationMessage message);}

