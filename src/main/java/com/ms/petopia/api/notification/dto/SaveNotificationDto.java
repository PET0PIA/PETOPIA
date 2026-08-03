package com.ms.petopia.api.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SaveNotificationDto {

    public record Request(
            @NotNull Long userId,
            @NotNull RecipientType recipientType,
            @NotNull NotificationType type,
            @NotBlank String title,
            @NotBlank String body,
            String linkUrl,
            @NotEmpty List<DeliveryChannel> channels,
            String recipientContact
    ) {}

    public record Response(Long notificationId) {}
}
