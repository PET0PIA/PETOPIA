package com.ms.petopia.api.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationListRow {
    private Long notificationId;
    private String type;
    private String title;
    private String body;
    private String linkUrl;
    private LocalDateTime createdAt;
    private boolean read;
}
