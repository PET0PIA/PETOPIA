package com.ms.petopia.api.notification.mapper;

import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.DeliveryStatus;
import com.ms.petopia.api.notification.dto.NotificationDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationDeliveryMapper {
    void insert(NotificationDelivery delivery);
    List<NotificationDelivery> selectByNotificationId(@Param("notificationId") Long notificationId);

    // 채널별 발송 기록 조회(중복 발송 방지)
    NotificationDelivery selectByNotificationIdAndChannel(
            @Param("notificationId") Long notificationId,
            @Param("channel")DeliveryChannel channel
    );

    // FAILED 상태 재처리 조회
    List<NotificationDelivery> selectByStatus(@Param("status")DeliveryStatus status);
    int updateStatus(
            @Param("deliveryId") Long deliveryId,
            @Param("status") DeliveryStatus status,
            @Param("sentAt") LocalDateTime sentAt,
            @Param("failReason") String failReason
    );

    // IN_APP 읽음 처리
    int updateReadAt(
            @Param("deliveryId") Long deliveryId,
            @Param("readAt") LocalDateTime readAt
    );

    // 인앱 미읽음 수
    int countUnreadInApp(@Param("userId") Long userId);
}
