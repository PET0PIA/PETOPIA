package com.ms.petopia.api.notification.service;

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
    @DisplayName("IN_APP 채널의 recipientContact는 null이다")
    void save_inAppChannel_recipientContactIsNull() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.IN_APP),
                null
        );

        notificationService.save(request);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryMapper).insert(captor.capture());

        assertThat(captor.getValue().getRecipientContact()).isNull();
    }

    @Test
    @DisplayName("channels에 EMAIL이 섞여 있어도 무시되고 IN_APP delivery만 생성된다 (EmailSenderService 제거 후 하위 호환)")
    void save_ignoresEmailChannel_onlyCreatesInAppDelivery() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                "user@example.com"
        );

        notificationService.save(request);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(DeliveryChannel.IN_APP);
        verifyNoInteractions(authMapper);
    }

    @Test
    @DisplayName("EMAIL 채널만 요청하면 delivery가 아예 생성되지 않는다")
    void save_emailOnly_createsNoDelivery() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "제목", "내용", null,
                List.of(DeliveryChannel.EMAIL),
                "user@example.com"
        );

        notificationService.save(request);

        verify(notificationDeliveryMapper, never()).insert(any());
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

        notificationService.notifySuperAdmins(NotificationType.SETTLEMENT_COMPLETED, "제목", "내용", "/admin/settlements");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .allMatch(n -> "/admin/settlements".equals(n.getLinkUrl()));
    }

    @Test
    @DisplayName("SUPER_ADMIN 저장은 REQUIRES_NEW로 별도 트랜잭션을 연다 (호출자 트랜잭션 롤백과 무관하게 커밋되도록)")
    void notifySuperAdmins_usesRequiresNewPropagation() {
        given(authMapper.selectSuperAdminUserIds()).willReturn(List.of(1L));
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        notificationService.notifySuperAdmins(NotificationType.SETTLEMENT_COMPLETED, "제목", "내용", "/admin/settlements");

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
