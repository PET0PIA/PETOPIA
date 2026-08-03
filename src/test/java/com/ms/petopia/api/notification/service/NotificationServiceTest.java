package com.ms.petopia.api.notification.service;

import com.ms.petopia.api.notification.dto.*;
import com.ms.petopia.api.notification.mapper.NotificationDeliveryMapper;
import com.ms.petopia.api.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationMapper notificationMapper;
    @Mock NotificationDeliveryMapper notificationDeliveryMapper;
    @Mock EmailSenderService emailSenderService;
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

        SaveNotificationRequest request = new SaveNotificationRequest(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "결제 완료", "결제가 완료됐습니다.", null,
                List.of(DeliveryChannel.IN_APP),
                null
        );

        SaveNotificationResponse response = notificationService.save(request);

        assertThat(response.notificationId()).isEqualTo(42L);
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    @DisplayName("채널 수만큼 delivery 행이 삽입된다")
    void save_insertsDeliveryPerChannel() {
        doAnswer(inv -> { ((Notification) inv.getArgument(0)).setNotificationId(1L); return null; })
                .when(notificationMapper).insert(any());

        SaveNotificationRequest request = new SaveNotificationRequest(
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

        SaveNotificationRequest request = new SaveNotificationRequest(
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

        SaveNotificationRequest request = new SaveNotificationRequest(
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

        SaveNotificationRequest request = new SaveNotificationRequest(
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

        SaveNotificationRequest request = new SaveNotificationRequest(
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

        SaveNotificationRequest request = new SaveNotificationRequest(
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

        SaveNotificationRequest request = new SaveNotificationRequest(
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
}
