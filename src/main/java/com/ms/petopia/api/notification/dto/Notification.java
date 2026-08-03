package com.ms.petopia.api.notification.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Notification {
    private Long notificationId;
    private Long userId; // 수신자
    private RecipientType recipientType;
    private NotificationType type;
    private String title;
    private String body;
    private String linkUrl;
    private LocalDateTime createdAt;
}
