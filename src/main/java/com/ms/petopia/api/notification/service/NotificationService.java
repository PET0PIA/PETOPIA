package com.ms.petopia.api.notification.service;

import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.DeliveryStatus;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.entity.Notification;
import com.ms.petopia.api.notification.entity.NotificationDelivery;
import com.ms.petopia.api.notification.mapper.NotificationDeliveryMapper;
import com.ms.petopia.api.notification.mapper.NotificationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationDeliveryMapper notificationDeliveryMapper;
    private final AuthMapper authMapper;
    private final PlatformTransactionManager transactionManager;

    /**
     * IN_APP만 실제로 배달한다. 예전엔 EMAIL 채널이 오면 EmailSenderService로 일반 텍스트
     * 메일을 직접 보냈는데, PETOPIA 스타일 HTML 메일(MailService)과 디자인이 달라 문제였고
     * (2026-08-23), 실제로 그 경로를 쓰던 호출부도 전부 MailService 템플릿으로 옮기거나
     * 아예 없앴다 - 이메일이 필요하면 호출자가 MailService를 직접 쓴다. channels에 IN_APP
     * 외의 값이 와도 예외 없이 조용히 무시한다(과거 EMAIL 요청과의 하위 호환).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SaveNotificationDto.Response save(SaveNotificationDto.Request request) {
        if (request.channels().size() != new HashSet<>(request.channels()).size()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "channels에 중복된 값이 있습니다");
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
            if (channel != DeliveryChannel.IN_APP) {
                continue;
            }
            NotificationDelivery delivery = NotificationDelivery.builder()
                    .notificationId(notification.getNotificationId())
                    .channel(channel)
                    .status(DeliveryStatus.PENDING)
                    .recipientContact(null)
                    .build();
            notificationDeliveryMapper.insert(delivery);
        }
        return new SaveNotificationDto.Response(notification.getNotificationId());
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

    /**
     * 활성 SUPER_ADMIN 전원에게 같은 알림을 개별 저장한다(인앱 전용).
     * 한 명 저장에 실패해도 나머지 관리자 발송은 계속 진행한다.
     */
    public void notifySuperAdmins(NotificationType type, String title, String body, String linkUrl) {
        // save()는 @Transactional(REQUIRES_NEW)이지만 여기서는 같은 인스턴스를 통해 직접 호출(self-invocation)하므로
        // 프록시를 거치지 않아 그 어노테이션이 적용되지 않는다. TransactionTemplate으로 트랜잭션 경계를 직접 만들어
        // 호출자(정산 확정 등)의 트랜잭션과 무관하게 관리자별로 독립적으로 커밋되도록 한다.
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (Long adminUserId : authMapper.selectSuperAdminUserIds()) {
            try {
                txTemplate.executeWithoutResult(status -> save(new SaveNotificationDto.Request(
                        adminUserId,
                        RecipientType.SUPER_ADMIN,
                        type,
                        title,
                        body,
                        linkUrl,
                        List.of(DeliveryChannel.IN_APP),
                        null
                )));
            } catch (Exception e) {
                log.error("SUPER_ADMIN 알림 저장 실패. adminUserId={}, type={}", adminUserId, type, e);
            }
        }
    }
}