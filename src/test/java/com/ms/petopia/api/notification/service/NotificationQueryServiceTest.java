package com.ms.petopia.api.notification.service;

import com.ms.petopia.api.notification.dto.NotificationListResponse;
import com.ms.petopia.api.notification.dto.NotificationListRow;
import com.ms.petopia.api.notification.mapper.NotificationDeliveryMapper;
import com.ms.petopia.api.notification.mapper.NotificationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private NotificationDeliveryMapper notificationDeliveryMapper;
    @InjectMocks
    private NotificationQueryService service;

    @Test
    void 알림_목록_페이지네이션_메타데이터_반환() {
        given(notificationMapper.countByUserId(1L)).willReturn(3L);
        given(notificationMapper.selectByUserId(1L, 0L, 20)).willReturn(List.of(
                row(1L, false),
                row(2L, true),
                row(3L, false)
        ));

        NotificationListResponse result = service.getMyNotifications(1L, 0, 20);

        assertThat(result.items()).hasSize(3);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.items().get(1).isRead()).isTrue();
    }

    @Test
    void 다음_페이지_존재_시_hasNext_true() {
        given(notificationMapper.countByUserId(1L)).willReturn(21L);
        given(notificationMapper.selectByUserId(1L, 0L, 20)).willReturn(List.of(row(1L, false)));

        NotificationListResponse result = service.getMyNotifications(1L, 0, 20);

        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void 알림_없을_때_빈_목록_반환() {
        given(notificationMapper.countByUserId(1L)).willReturn(0L);
        given(notificationMapper.selectByUserId(1L, 0L, 20)).willReturn(List.of());

        NotificationListResponse result = service.getMyNotifications(1L, 0, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
        assertThat(result.totalPages()).isEqualTo(0);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void page_음수이면_예외() {
        assertThatThrownBy(() -> service.getMyNotifications(1L, -1, 20))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void size_0이면_예외() {
        assertThatThrownBy(() -> service.getMyNotifications(1L, 0, 0))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void size_최대값_초과하면_예외() {
        assertThatThrownBy(() -> service.getMyNotifications(1L, 0, 51))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    // ===== getUnreadCount =====

    @Test
    void 미읽음_알림이_있으면_개수를_반환한다() {
        given(notificationDeliveryMapper.countUnreadInApp(1L)).willReturn(5);

        assertThat(service.getUnreadCount(1L)).isEqualTo(5);
    }

    @Test
    void 미읽음_알림이_없으면_0을_반환한다() {
        given(notificationDeliveryMapper.countUnreadInApp(1L)).willReturn(0);

        assertThat(service.getUnreadCount(1L)).isZero();
    }

    private NotificationListRow row(Long id, boolean read) {
        NotificationListRow row = new NotificationListRow();
        row.setNotificationId(id);
        row.setType("PAYMENT_COMPLETED");
        row.setTitle("결제 완료");
        row.setBody("결제가 완료됐습니다.");
        row.setLinkUrl(null);
        row.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        row.setRead(read);
        return row;
    }
}
