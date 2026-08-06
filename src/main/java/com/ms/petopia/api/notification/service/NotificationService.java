package com.ms.petopia.api.notification.service;

import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.DeliveryStatus;
import com.ms.petopia.api.notification.entity.Notification;
import com.ms.petopia.api.notification.entity.NotificationDelivery;
import com.ms.petopia.api.notification.mapper.NotificationDeliveryMapper;
import com.ms.petopia.api.notification.mapper.NotificationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationDeliveryMapper notificationDeliveryMapper;
    private final EmailSenderService emailSenderService;

    @Transactional
    public SaveNotificationDto.Response save(SaveNotificationDto.Request request) {
        if (request.channels().size() != new HashSet<>(request.channels()).size()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "channels에 중복된 값이 있습니다");
        }
        if (request.channels().contains(DeliveryChannel.EMAIL) &&
                (request.recipientContact() == null || request.recipientContact().isBlank())) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "EMAIL 채널 사용 시 recipientContact는 필수입니다");
        }

        Notification notification = Notification.builder()
                .userId(request.userId())
                .recipientType(request.recipientType())
                .type(request.type())
                .title(request.title())
                .body(request.body())
                .linkUrl(request.linkUrl())
                .build();
        notificationMapper.insert(notification);

        for (DeliveryChannel channel : request.channels()) {
            NotificationDelivery delivery = NotificationDelivery.builder()
                    .notificationId(notification.getNotificationId())
                    .channel(channel)
                    .status(DeliveryStatus.PENDING)
                    .recipientContact(channel == DeliveryChannel.IN_APP ? null : request.recipientContact())
                    .build();
            notificationDeliveryMapper.insert(delivery);

            // email 채널이면 발송
            if (channel == DeliveryChannel.EMAIL){
                sendEmail(delivery, request);
            }
        }
        return new SaveNotificationDto.Response(notification.getNotificationId());
    }

    private void sendEmail(NotificationDelivery delivery, SaveNotificationDto.Request request){
        try{
            emailSenderService.send(
                    delivery.getRecipientContact(),
                    request.title(),
                    request.body()
            );
            notificationDeliveryMapper.updateStatus(
                    delivery.getDeliveryId(),
                    DeliveryStatus.SENT,
                    LocalDateTime.now(),
                    null
            );
        } catch (Exception e) {
            notificationDeliveryMapper.updateStatus(
                    delivery.getDeliveryId(),
                    DeliveryStatus.FAILED,
                    null,
                    e.getMessage()
            );
        }
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId){
        Notification notification = notificationMapper.selectById(notificationId);

        if(notification == null){
            throw new CommonException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        if(!notification.getUserId().equals(userId)){
            throw new CommonException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
        NotificationDelivery delivery = notificationDeliveryMapper
                .selectByNotificationIdAndChannel(notificationId, DeliveryChannel.IN_APP);
        if (delivery == null) {
            throw new CommonException(ErrorCode.NOTIFICATION_NOT_FOUND); // IN_APP 알림이 아님
        }
        if (delivery.getReadAt() != null) {
            return; // 이미 읽음
        }
        notificationDeliveryMapper.updateReadAt(delivery.getDeliveryId(), LocalDateTime.now());
    }

    @Transactional
    public void markAllAsRead(Long userId){
        notificationDeliveryMapper.updateReadAtAllInApp(userId, LocalDateTime.now());
    }
}