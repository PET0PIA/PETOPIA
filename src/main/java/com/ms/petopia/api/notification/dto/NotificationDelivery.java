package com.ms.petopia.api.notification.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class NotificationDelivery {
    private Long deliveryId;
    private Long notificationId;
    private DeliveryChannel channel;
    private DeliveryStatus status;
    @ToString.Exclude
    private String recipientContact; // IN_APP은 NULL
    private LocalDateTime sentAt;
    private String failReason;
    private LocalDateTime readAt; // IN_APP 전용
    private LocalDateTime createdAt;
}
