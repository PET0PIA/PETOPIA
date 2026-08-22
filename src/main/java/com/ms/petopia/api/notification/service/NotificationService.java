package com.ms.petopia.api.notification.service;

import com.ms.petopia.api.auth.domain.User;
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
    private final EmailSenderService emailSenderService;
    private final AuthMapper authMapper;
    private final PlatformTransactionManager transactionManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SaveNotificationDto.Response save(SaveNotificationDto.Request request) {
        if (request.channels().size() != new HashSet<>(request.channels()).size()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "channels에 중복된 값이 있습니다");
        }

        // EMAIL 채널 요청 시 recipientContact가 없으면 userId로 이메일을 자동 조회한다.
        // 명시적으로 전달된 값이 있으면 그것을 우선 사용한다.
        String resolvedContact = resolveRecipientContact(request);

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
            // 이메일 주소를 끝내 구하지 못한 경우 EMAIL 채널은 건너뜀
            if (channel == DeliveryChannel.EMAIL && (resolvedContact == null || resolvedContact.isBlank())) {
                continue;
            }
            NotificationDelivery delivery = NotificationDelivery.builder()
                    .notificationId(notification.getNotificationId())
                    .channel(channel)
                    .status(DeliveryStatus.PENDING)
                    .recipientContact(channel == DeliveryChannel.IN_APP ? null : resolvedContact)
                    .build();
            notificationDeliveryMapper.insert(delivery);

            if (channel == DeliveryChannel.EMAIL) {
                sendEmail(delivery, request);
            }
        }
        return new SaveNotificationDto.Response(notification.getNotificationId());
    }

    /**
     * EMAIL 채널이 없으면 요청에 담긴 값을 그대로 반환한다.
     * EMAIL 채널이 있고 recipientContact가 명시됐으면 그 값을 쓴다.
     * EMAIL 채널이 있고 recipientContact가 없으면 userId로 회원 이메일을 조회한다.
     * 조회 결과도 없으면 EMAIL 채널은 건너뛰도록 예외 대신 null을 반환한다.
     */
    private String resolveRecipientContact(SaveNotificationDto.Request request) {
        if (!request.channels().contains(DeliveryChannel.EMAIL)) {
            return request.recipientContact();
        }
        if (request.recipientContact() != null && !request.recipientContact().isBlank()) {
            return request.recipientContact();
        }
        User user = authMapper.selectUserById(request.userId());
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("EMAIL 채널 요청이나 이메일 주소를 찾을 수 없음. userId={}", request.userId());
            return null;
        }
        return user.getEmail();
    }

    private void sendEmail(NotificationDelivery delivery, SaveNotificationDto.Request request) {
        try {
            emailSenderService.send(delivery.getRecipientContact(), request.title(), request.body());
        } catch (Exception e) {
            notificationDeliveryMapper.updateStatus(delivery.getDeliveryId(), DeliveryStatus.FAILED, null, e.getMessage());
            return;
        }
        // 발송 성공 — 상태 기록 실패 시 FAILED로 덮어쓰지 않고 PENDING으로 남긴다.
        try {
            notificationDeliveryMapper.updateStatus(delivery.getDeliveryId(), DeliveryStatus.SENT, LocalDateTime.now(), null);
        } catch (Exception e) {
            log.warn("이메일 발송 성공했으나 상태 갱신 실패. deliveryId={}", delivery.getDeliveryId(), e);
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

    /**
     * 활성 SUPER_ADMIN 전원에게 같은 알림을 개별 저장한다(인앱 전용).
     * 한 명 저장에 실패해도 나머지 관리자 발송은 계속 진행한다.
     */
    public void notifySuperAdmins(NotificationType type, String title, String body) {
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
                        null,
                        List.of(DeliveryChannel.IN_APP),
                        null
                )));
            } catch (Exception e) {
                log.error("SUPER_ADMIN 알림 저장 실패. adminUserId={}, type={}", adminUserId, type, e);
            }
        }
    }
}