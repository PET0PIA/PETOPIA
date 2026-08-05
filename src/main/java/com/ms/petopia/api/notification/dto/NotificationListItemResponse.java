package com.ms.petopia.api.notification.dto;

import java.time.LocalDateTime;

public record NotificationListItemResponse (
    Long notificationId,
    String type,
    String title,
    String body,
    String linkUrl,
    LocalDateTime createdAt,
    boolean isRead
) {}
