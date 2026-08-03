package com.ms.petopia.api.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveNotificationRequest(
        @NotNull Long userId, // 수신자
        @NotNull RecipientType recipientType, // USER / VENDOR / EVENT_ADMIN / SUPER_ADMIN
        @NotNull NotificationType type, // 알림 유형
        @NotBlank String title,
        @NotBlank String body,
        String linkUrl, // 딥링크, 없으면 null
        @NotEmpty List<DeliveryChannel> channels, // 보낼 채널 목록 (IN_APP, EMAIL)
        String recipientContact // 이메일 주소 등, IN_APP만 보낼 경우 null
) {}