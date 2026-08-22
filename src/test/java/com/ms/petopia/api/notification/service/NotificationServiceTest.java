package com.ms.petopia.api.notification.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.notification.dto.*;
import com.ms.petopia.api.notification.entity.Notification;
import com.ms.petopia.api.notification.entity.NotificationDelivery;
import com.ms.petopia.api.notification.mapper.NotificationDeliveryMapper;
import com.ms.petopia.api.notification.mapper.NotificationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationMapper notificationMapper;
    @Mock NotificationDeliveryMapper notificationDeliveryMapper;
    @Mock EmailSenderService emailSenderService;
    @Mock AuthMapper authMapper;
    @Mock PlatformTransactionManager transactionManager;
    @InjectMocks NotificationService notificationService;

    @Test
    @DisplayName("알림 저장 시 notificationId를 반환한다")
    void save_returnsNotificationId() {
        // MyBatis insert는 @Param 객체에 직접 PK를 세팅하므로 doAnswer로 흉내낸다
        doAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setNotificationId(42L);
            return null;
        }).when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "결제 완료", "결제가 완료됐습니다.", null,
                List.of(DeliveryChannel.IN_APP),
                null
        );

        SaveNotificationDto.Response response = notificationService.save(request);

        assertThat(response.notificationId()).isEqualTo(42L);
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    @DisplayName("채널 수만큼 delivery 행이 삽입된다")
    void save_insertsDeliveryPerChannel() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                "user@example.com"
        );

        notificationService.save(request);

        // 채널이 2개니까 delivery insert도 2번
        verify(notificationDeliveryMapper, times(2)).insert(any(NotificationDelivery.class));
    }

    @Test
    @DisplayName("IN_APP 채널의 recipientContact는 null이다")
    void save_inAppChannel_recipientContactIsNull() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.IN_APP),
                "user@example.com" // 요청엔 있어도
        );

        notificationService.save(request);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryMapper).insert(captor.capture());

        assertThat(captor.getValue().getRecipientContact()).isNull(); // IN_APP이면 무조건 null
    }

    @Test
    @DisplayName("EMAIL 채널은 recipientContact가 요청값 그대로 들어간다")
    void save_emailChannel_recipientContactIsSet() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.EMAIL),
                "user@example.com"
        );

        notificationService.save(request);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryMapper).insert(captor.capture());

        assertThat(captor.getValue().getRecipientContact()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("EMAIL 채널 발송 성공 시 delivery 상태가 SENT로 업데이트된다")
    void save_emailChannel_success_updatesStatusToSent() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.EMAIL),
                "user@example.com"
        );

        notificationService.save(request);

        verify(emailSenderService).send("user@example.com", "제목", "내용");
        verify(notificationDeliveryMapper).updateStatus(any(), eq(DeliveryStatus.SENT), any(), isNull());
    }

    @Test
    @DisplayName("EMAIL 발송 실패 시 delivery 상태가 FAILED로 업데이트되고 오류 메시지가 기록된다")
    void save_emailChannel_failure_updatesStatusToFailed() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());
        doThrow(new RuntimeException("SMTP 연결 실패")).when(emailSenderService).send(any(), any(), any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.EMAIL),
                "user@example.com"
        );

        notificationService.save(request);

        verify(notificationDeliveryMapper).updateStatus(any(), eq(DeliveryStatus.FAILED), isNull(), eq("SMTP 연결 실패"));
    }

    @Test
    @DisplayName("IN_APP 채널만 있으면 emailSenderService는 호출되지 않는다")
    void save_inAppOnly_doesNotCallEmailSender() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.IN_APP),
                null
        );

        notificationService.save(request);

        verifyNoInteractions(emailSenderService);
    }

    @Test
    @DisplayName("IN_APP + EMAIL 동시 요청 시 이메일은 정확히 1번만 발송되고 IN_APP delivery는 이메일 없이 처리된다")
    void save_inAppAndEmail_emailSentOnce() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "결제 완료", "결제가 완료됐습니다.", null,
                List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                "user@example.com"
        );

        notificationService.save(request);

        // delivery 행은 채널 수만큼 2개
        verify(notificationDeliveryMapper, times(2)).insert(any(NotificationDelivery.class));
        // 이메일은 EMAIL 채널에만 1번만 발송
        verify(emailSenderService, times(1)).send("user@example.com", "결제 완료", "결제가 완료됐습니다.");
        // 이메일 발송 성공 후 SENT 업데이트도 1번
        verify(notificationDeliveryMapper, times(1)).updateStatus(any(), eq(DeliveryStatus.SENT), any(), isNull());
    }

    @Test
    @DisplayName("EMAIL 채널이고 recipientContact가 null이면 authMapper로 이메일을 자동 조회해 발송한다")
    void save_emailChannel_nullContact_autoResolvesEmailFromUser() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());
        User user = User.builder().email("auto@example.com").build();
        given(authMapper.selectUserById(1L)).willReturn(user);

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.EMAIL),
                null  // recipientContact 없음
        );

        notificationService.save(request);

        verify(authMapper).selectUserById(1L);
        verify(emailSenderService).send("auto@example.com", "제목", "내용");
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryMapper).insert(captor.capture());
        assertThat(captor.getValue().getRecipientContact()).isEqualTo("auto@example.com");
    }

    @Test
    @DisplayName("EMAIL 채널이고 authMapper가 이메일을 반환하지 못하면 EMAIL delivery를 생성하지 않는다")
    void save_emailChannel_nullContact_userEmailNotFound_skipsEmailDelivery() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());
        given(authMapper.selectUserById(1L)).willReturn(null);

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.EMAIL),
                null
        );

        notificationService.save(request);

        verify(notificationDeliveryMapper, never()).insert(any());
        verifyNoInteractions(emailSenderService);
    }

    @Test
    @DisplayName("IN_APP + EMAIL 동시 요청 시 이메일 조회 실패하면 IN_APP delivery만 생성된다")
    void save_inAppAndEmail_emailNotFound_onlyInAppDeliveryCreated() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());
        given(authMapper.selectUserById(1L)).willReturn(null);

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                null
        );

        notificationService.save(request);

        verify(notificationDeliveryMapper, times(1)).insert(any(NotificationDelivery.class));
        verifyNoInteractions(emailSenderService);
    }

    // ===== markAsRead =====

    @Test
    @DisplayName("읽지 않은 IN_APP 알림을 읽음 처리하면 updateReadAt이 호출된다")
    void markAsRead_success() {
        Notification notification = Notification.builder()
                .notificationId(1L).userId(10L).build();
        NotificationDelivery delivery = NotificationDelivery.builder()
                .deliveryId(100L).channel(DeliveryChannel.IN_APP).readAt(null).build();

        given(notificationMapper.selectById(1L)).willReturn(notification);
        given(notificationDeliveryMapper.selectByNotificationIdAndChannel(1L, DeliveryChannel.IN_APP))
                .willReturn(delivery);

        notificationService.markAsRead(1L, 10L);

        verify(notificationDeliveryMapper).updateReadAt(eq(100L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("존재하지 않는 알림 ID로 읽음 처리 시 NOTIFICATION_NOT_FOUND 예외가 발생한다")
    void markAsRead_notificationNotFound() {
        given(notificationMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> notificationService.markAsRead(999L, 10L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);

        verifyNoInteractions(notificationDeliveryMapper);
    }

    @Test
    @DisplayName("다른 사용자의 알림을 읽음 처리 시 NOTIFICATION_ACCESS_DENIED 예외가 발생한다")
    void markAsRead_accessDenied() {
        Notification notification = Notification.builder()
                .notificationId(1L).userId(10L).build();
        given(notificationMapper.selectById(1L)).willReturn(notification);

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_ACCESS_DENIED);

        verifyNoInteractions(notificationDeliveryMapper);
    }

    @Test
    @DisplayName("IN_APP delivery가 없는 알림(EMAIL 전용)은 NOTIFICATION_NOT_FOUND 예외가 발생한다")
    void markAsRead_noInAppDelivery_throwsNotFound() {
        Notification notification = Notification.builder()
                .notificationId(1L).userId(10L).build();
        given(notificationMapper.selectById(1L)).willReturn(notification);
        given(notificationDeliveryMapper.selectByNotificationIdAndChannel(1L, DeliveryChannel.IN_APP))
                .willReturn(null);

        assertThatThrownBy(() -> notificationService.markAsRead(1L, 10L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);

        verify(notificationDeliveryMapper, never()).updateReadAt(any(), any());
    }

    @Test
    @DisplayName("이미 읽은 알림은 updateReadAt을 호출하지 않고 정상 종료된다 (멱등성)")
    void markAsRead_alreadyRead_doesNothing() {
        Notification notification = Notification.builder()
                .notificationId(1L).userId(10L).build();
        NotificationDelivery delivery = NotificationDelivery.builder()
                .deliveryId(100L).channel(DeliveryChannel.IN_APP)
                .readAt(LocalDateTime.of(2026, 8, 1, 10, 0)).build();

        given(notificationMapper.selectById(1L)).willReturn(notification);
        given(notificationDeliveryMapper.selectByNotificationIdAndChannel(1L, DeliveryChannel.IN_APP))
                .willReturn(delivery);

        notificationService.markAsRead(1L, 10L);

        verify(notificationDeliveryMapper, never()).updateReadAt(any(), any());
    }

    // ===== notifySuperAdmins =====

    @Test
    @DisplayName("SUPER_ADMIN 중 한 명 저장에 실패해도 나머지 관리자는 계속 저장된다")
    void notifySuperAdmins_oneFailure_othersStillSaved() {
        given(authMapper.selectSuperAdminUserIds()).willReturn(List.of(1L, 2L, 3L));
        doAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getUserId().equals(2L)) {
                throw new RuntimeException("DB 오류");
            }
            n.setNotificationId(1L);
            return null;
        }).when(notificationMapper).insert(any());

        notificationService.notifySuperAdmins(NotificationType.SETTLEMENT_COMPLETED, "제목", "내용");

        verify(notificationMapper, times(3)).insert(any(Notification.class));
    }

    @Test
    @DisplayName("SUPER_ADMIN 저장은 REQUIRES_NEW로 별도 트랜잭션을 연다 (호출자 트랜잭션 롤백과 무관하게 커밋되도록)")
    void notifySuperAdmins_usesRequiresNewPropagation() {
        given(authMapper.selectSuperAdminUserIds()).willReturn(List.of(1L));
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        notificationService.notifySuperAdmins(NotificationType.SETTLEMENT_COMPLETED, "제목", "내용");

        ArgumentCaptor<TransactionDefinition> captor = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(captor.capture());
        assertThat(captor.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ===== markAllAsRead =====

    @Test
    @DisplayName("전체 읽음 처리 시 userId와 현재 시각으로 updateReadAtAllInApp이 호출된다")
    void markAllAsRead_callsMapperWithUserId() {
        notificationService.markAllAsRead(10L);

        verify(notificationDeliveryMapper).updateReadAtAllInApp(eq(10L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("읽지 않은 알림이 없어도 예외 없이 정상 종료된다 (멱등성)")
    void markAllAsRead_noUnread_doesNotThrow() {
        given(notificationDeliveryMapper.updateReadAtAllInApp(eq(10L), any())).willReturn(0);

        notificationService.markAllAsRead(10L);

        verify(notificationDeliveryMapper).updateReadAtAllInApp(eq(10L), any(LocalDateTime.class));
    }
}
