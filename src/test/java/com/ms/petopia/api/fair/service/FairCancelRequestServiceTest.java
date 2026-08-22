package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateFairCancelRequestRequest;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairCancelRequest;
import com.ms.petopia.api.fair.dto.FairCancelRequestQueueItemResponse;
import com.ms.petopia.api.fair.dto.FairCancelRequestQueueRow;
import com.ms.petopia.api.fair.dto.FairCancelRequestResponse;
import com.ms.petopia.api.fair.dto.FairCancelRequestStatus;
import com.ms.petopia.api.fair.dto.FairReviewDecision;
import com.ms.petopia.api.fair.dto.FairStatus;
import com.ms.petopia.api.fair.dto.ReviewFairCancelRequestRequest;
import com.ms.petopia.api.fair.dto.ReviewFairCancelRequestResponse;
import com.ms.petopia.api.fair.mapper.FairCancelRequestMapper;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FairCancelRequestServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long OTHER_FAIR_ID = 20L;
    private static final Long CANCEL_REQUEST_ID = 100L;
    private static final Long REQUESTED_BY = 1L;
    private static final Long REVIEWER_ID = 99L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 10, 0);

    @Mock
    private FairCancelRequestMapper cancelRequestMapper;

    @Mock
    private FairMapper fairMapper;

    @Mock
    private FairTimeProvider timeProvider;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private FairAdminAccessGuard fairAdminAccessGuard;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FairCancelRequestService cancelRequestService;

    @BeforeEach
    void setUpTime() {
        org.mockito.Mockito.lenient().when(timeProvider.now()).thenReturn(NOW);
        // review()가 승인/반려 심사 결과를 requestedBy에게 알리는 afterCommit 콜백을
        // 등록하므로(FairServiceTest와 동일한 이유), 활성 트랜잭션 동기화 컨텍스트가 있어야
        // registerSynchronization이 IllegalStateException 없이 통과한다.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ===== create =====

    @Test
    @DisplayName("진행 중인 행사에 사유를 채워 신청하면 저장한다")
    void create_정상신청이면_저장한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));
        given(cancelRequestMapper.selectPendingByFairId(FAIR_ID)).willReturn(null);

        FairCancelRequestResponse response =
                cancelRequestService.create(FAIR_ID, REQUESTED_BY, new CreateFairCancelRequestRequest("경영상 사유"));

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.reason()).isEqualTo("경영상 사유");
        assertThat(response.status()).isEqualTo(FairCancelRequestStatus.PENDING.name());

        ArgumentCaptor<FairCancelRequest> captor = ArgumentCaptor.forClass(FairCancelRequest.class);
        verify(cancelRequestMapper).insert(captor.capture());
        assertThat(captor.getValue().getFairId()).isEqualTo(FAIR_ID);
        assertThat(captor.getValue().getRequestedBy()).isEqualTo(REQUESTED_BY);
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("사유가 없으면 FAIR_CANCEL_REASON_REQUIRED를 던지고 저장하지 않는다")
    void create_사유없으면_예외를_던진다() {
        assertErrorCode(
                () -> cancelRequestService.create(FAIR_ID, REQUESTED_BY, new CreateFairCancelRequestRequest(" ")),
                ErrorCode.FAIR_CANCEL_REASON_REQUIRED
        );
        verify(cancelRequestMapper, never()).insert(any());
    }

    @Test
    @DisplayName("존재하지 않는 행사면 FAIR_NOT_FOUND를 던진다")
    void create_행사없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(
                () -> cancelRequestService.create(FAIR_ID, REQUESTED_BY, new CreateFairCancelRequestRequest("사유")),
                ErrorCode.FAIR_NOT_FOUND
        );
        verify(cancelRequestMapper, never()).insert(any());
    }

    @Test
    @DisplayName("심사 전(RECEIVED) 행사는 취소를 신청할 수 없어 FAIR_CANCEL_NOT_REQUESTABLE을 던진다")
    void create_심사전이면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));

        assertErrorCode(
                () -> cancelRequestService.create(FAIR_ID, REQUESTED_BY, new CreateFairCancelRequestRequest("사유")),
                ErrorCode.FAIR_CANCEL_NOT_REQUESTABLE
        );
        verify(cancelRequestMapper, never()).insert(any());
    }

    @Test
    @DisplayName("이미 취소된 행사는 다시 신청할 수 없어 FAIR_CANCEL_NOT_REQUESTABLE을 던진다")
    void create_이미취소됐으면_예외를_던진다() {
        Fair fair = fairWithStatus(FairStatus.PAYMENT_PENDING);
        fair.setCanceledAt(NOW.minusDays(1));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        assertErrorCode(
                () -> cancelRequestService.create(FAIR_ID, REQUESTED_BY, new CreateFairCancelRequestRequest("사유")),
                ErrorCode.FAIR_CANCEL_NOT_REQUESTABLE
        );
        verify(cancelRequestMapper, never()).insert(any());
    }

    @Test
    @DisplayName("이미 검토 대기 중인 취소 신청이 있으면 FAIR_CANCEL_NOT_REQUESTABLE을 던진다")
    void create_이미대기중인신청있으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));
        given(cancelRequestMapper.selectPendingByFairId(FAIR_ID)).willReturn(new FairCancelRequest());

        assertErrorCode(
                () -> cancelRequestService.create(FAIR_ID, REQUESTED_BY, new CreateFairCancelRequestRequest("사유")),
                ErrorCode.FAIR_CANCEL_NOT_REQUESTABLE
        );
        verify(cancelRequestMapper, never()).insert(any());
    }

    /**
     * selectPendingByFairId 확인과 insert 사이의 레이스로 다른 트랜잭션이 먼저 PENDING을
     * 커밋하면, 이 트랜잭션의 insert는 pending_key 유니크 제약(V11 마이그레이션)에 걸려
     * DuplicateKeyException을 던진다. 이 케이스를 같은 에러 코드로 변환하는지 검증한다.
     */
    @Test
    @DisplayName("insert 시점에 유니크 제약을 위반하면(동시 신청) FAIR_CANCEL_NOT_REQUESTABLE로 변환한다")
    void create_동시신청으로_유니크제약위반되면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));
        given(cancelRequestMapper.selectPendingByFairId(FAIR_ID)).willReturn(null);
        willThrow(new DuplicateKeyException("UK_FAIR_CANCEL_REQUESTS_PENDING"))
                .given(cancelRequestMapper).insert(any());

        assertErrorCode(
                () -> cancelRequestService.create(FAIR_ID, REQUESTED_BY, new CreateFairCancelRequestRequest("사유")),
                ErrorCode.FAIR_CANCEL_NOT_REQUESTABLE
        );
    }

    @Test
    @DisplayName("담당 행사가 아니면(FairAdminAccessGuard 거부) 신청을 저장하지 않고 예외를 전파한다")
    void create_담당행사아니면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertErrorCode(
                () -> cancelRequestService.create(FAIR_ID, REQUESTED_BY, new CreateFairCancelRequestRequest("사유")),
                ErrorCode.ACCESS_DENIED
        );
        verify(cancelRequestMapper, never()).insert(any());
    }

    // ===== getCancelRequests =====

    @Test
    @DisplayName("존재하지 않는 행사의 취소 신청 이력을 조회하면 FAIR_NOT_FOUND를 던진다")
    void getCancelRequests_행사없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);
        assertErrorCode(() -> cancelRequestService.getCancelRequests(FAIR_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("취소 신청 이력을 응답 목록으로 매핑한다")
    void getCancelRequests_목록을_매핑한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));
        given(cancelRequestMapper.selectByFairId(FAIR_ID)).willReturn(List.of(cancelRequest(FairCancelRequestStatus.PENDING)));

        List<FairCancelRequestResponse> responses = cancelRequestService.getCancelRequests(FAIR_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo(FairCancelRequestStatus.PENDING.name());
    }

    @Test
    @DisplayName("담당 행사가 아니면(FairAdminAccessGuard 거부) 이력을 조회하지 않고 예외를 전파한다")
    void getCancelRequests_담당행사아니면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertErrorCode(() -> cancelRequestService.getCancelRequests(FAIR_ID), ErrorCode.ACCESS_DENIED);
        verify(cancelRequestMapper, never()).selectByFairId(any());
    }

    // ===== getQueue (관리자 취소 신청 큐) =====

    @Test
    @DisplayName("status를 주면 그 상태만 걸러 매퍼에 그대로 넘기고 fairName까지 매핑한다")
    void getQueue_status를_주면_그대로_넘기고_매핑한다() {
        FairCancelRequestQueueRow row = new FairCancelRequestQueueRow();
        row.setFairCancelRequestId(CANCEL_REQUEST_ID);
        row.setFairId(FAIR_ID);
        row.setFairName("2026 서울 펫페어");
        row.setRequestedBy(REQUESTED_BY);
        row.setReason("경영상 사유");
        row.setStatus(FairCancelRequestStatus.PENDING);
        row.setCreatedAt(NOW.minusDays(1));
        given(cancelRequestMapper.selectQueue(FairCancelRequestStatus.PENDING)).willReturn(List.of(row));

        List<FairCancelRequestQueueItemResponse> response = cancelRequestService.getQueue(FairCancelRequestStatus.PENDING);

        verify(cancelRequestMapper).selectQueue(FairCancelRequestStatus.PENDING);
        assertThat(response).hasSize(1);
        FairCancelRequestQueueItemResponse item = response.get(0);
        assertThat(item.fairCancelRequestId()).isEqualTo(CANCEL_REQUEST_ID);
        assertThat(item.fairId()).isEqualTo(FAIR_ID);
        assertThat(item.fairName()).isEqualTo("2026 서울 펫페어");
        assertThat(item.status()).isEqualTo(FairCancelRequestStatus.PENDING.name());
    }

    @Test
    @DisplayName("status가 없으면 null을 그대로 매퍼에 넘겨 전체를 조회한다")
    void getQueue_status없으면_전체를_조회한다() {
        given(cancelRequestMapper.selectQueue(null)).willReturn(List.of());

        List<FairCancelRequestQueueItemResponse> response = cancelRequestService.getQueue(null);

        verify(cancelRequestMapper).selectQueue(null);
        assertThat(response).isEmpty();
    }

    // ===== review =====

    @Test
    @DisplayName("PENDING 취소 신청을 승인하면 APPROVED로 바뀌고 fairs.canceled_at을 채운다")
    void review_승인하면_행사canceledAt을_채운다() {
        given(cancelRequestMapper.selectById(CANCEL_REQUEST_ID)).willReturn(cancelRequest(FairCancelRequestStatus.PENDING));
        given(cancelRequestMapper.update(any())).willReturn(1);
        given(fairMapper.update(any())).willReturn(1);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithName("2026 서울 펫페어"));

        ReviewFairCancelRequestResponse response = cancelRequestService.review(
                FAIR_ID, CANCEL_REQUEST_ID, REVIEWER_ID, new ReviewFairCancelRequestRequest(FairReviewDecision.APPROVE, null)
        );

        assertThat(response.status()).isEqualTo(FairCancelRequestStatus.APPROVED.name());
        assertThat(response.canceledAt()).isEqualTo(NOW);
        assertThat(response.rejectReason()).isNull();

        ArgumentCaptor<FairCancelRequest> requestCaptor = ArgumentCaptor.forClass(FairCancelRequest.class);
        verify(cancelRequestMapper).update(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(FairCancelRequestStatus.APPROVED);
        assertThat(requestCaptor.getValue().getReviewedBy()).isEqualTo(REVIEWER_ID);

        ArgumentCaptor<Fair> fairCaptor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).update(fairCaptor.capture());
        assertThat(fairCaptor.getValue().getFairId()).isEqualTo(FAIR_ID);
        assertThat(fairCaptor.getValue().getCanceledAt()).isEqualTo(NOW);

        verify(auditLogService).record(
                eq(REVIEWER_ID), eq(ActorType.ADMIN), eq("SUPER_ADMIN"),
                eq(ActionType.FAIR_CANCEL_APPROVE), eq(TargetType.FAIR), eq(FAIR_ID),
                isNull(), eq(Map.of("fairCancelRequestId", CANCEL_REQUEST_ID, "canceledAt", NOW))
        );

        // 알림은 afterCommit 콜백이라 트랜잭션이 실제로 커밋되기 전까지는 호출되지 않는다.
        verifyNoInteractions(notificationService);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<SaveNotificationDto.Request> notifCaptor = ArgumentCaptor.forClass(SaveNotificationDto.Request.class);
        verify(notificationService).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().userId()).isEqualTo(REQUESTED_BY);
        assertThat(notifCaptor.getValue().recipientType()).isEqualTo(RecipientType.EVENT_ADMIN);
        assertThat(notifCaptor.getValue().type()).isEqualTo(NotificationType.FAIR_CANCEL_REQUEST_APPROVED);
        assertThat(notifCaptor.getValue().channels()).containsExactly(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL);
        assertThat(notifCaptor.getValue().body()).contains("2026 서울 펫페어");
    }

    @Test
    @DisplayName("fairs 갱신이 실패하면(영향 행 0건) INTERNAL_SERVER_ERROR를 던지고 감사 로그를 남기지 않는다")
    void review_fairs갱신실패하면_예외를_던진다() {
        given(cancelRequestMapper.selectById(CANCEL_REQUEST_ID)).willReturn(cancelRequest(FairCancelRequestStatus.PENDING));
        given(cancelRequestMapper.update(any())).willReturn(1);
        given(fairMapper.update(any())).willReturn(0);

        assertErrorCode(
                () -> cancelRequestService.review(
                        FAIR_ID, CANCEL_REQUEST_ID, REVIEWER_ID,
                        new ReviewFairCancelRequestRequest(FairReviewDecision.APPROVE, null)
                ),
                ErrorCode.INTERNAL_SERVER_ERROR
        );
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PENDING 취소 신청을 사유와 함께 반려하면 REJECTED로 바뀌고 fairs는 건드리지 않는다")
    void review_반려하면_행사는_건드리지않는다() {
        given(cancelRequestMapper.selectById(CANCEL_REQUEST_ID)).willReturn(cancelRequest(FairCancelRequestStatus.PENDING));
        given(cancelRequestMapper.update(any())).willReturn(1);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithName("2026 서울 펫페어"));

        ReviewFairCancelRequestResponse response = cancelRequestService.review(
                FAIR_ID, CANCEL_REQUEST_ID, REVIEWER_ID,
                new ReviewFairCancelRequestRequest(FairReviewDecision.REJECT, "서류 미비")
        );

        assertThat(response.status()).isEqualTo(FairCancelRequestStatus.REJECTED.name());
        assertThat(response.rejectReason()).isEqualTo("서류 미비");
        assertThat(response.canceledAt()).isNull();
        verify(fairMapper, never()).update(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());

        verifyNoInteractions(notificationService);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<SaveNotificationDto.Request> notifCaptor = ArgumentCaptor.forClass(SaveNotificationDto.Request.class);
        verify(notificationService).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().userId()).isEqualTo(REQUESTED_BY);
        assertThat(notifCaptor.getValue().type()).isEqualTo(NotificationType.FAIR_CANCEL_REQUEST_REJECTED);
        assertThat(notifCaptor.getValue().body()).contains("서류 미비");
    }

    @Test
    @DisplayName("반려인데 사유가 없으면 FAIR_CANCEL_REJECT_REASON_REQUIRED를 던지고 갱신하지 않는다")
    void review_반려사유없으면_예외를_던진다() {
        given(cancelRequestMapper.selectById(CANCEL_REQUEST_ID)).willReturn(cancelRequest(FairCancelRequestStatus.PENDING));

        assertErrorCode(
                () -> cancelRequestService.review(
                        FAIR_ID, CANCEL_REQUEST_ID, REVIEWER_ID,
                        new ReviewFairCancelRequestRequest(FairReviewDecision.REJECT, " ")
                ),
                ErrorCode.FAIR_CANCEL_REJECT_REASON_REQUIRED
        );
        verify(cancelRequestMapper, never()).update(any());
        verify(fairMapper, never()).update(any());
    }

    @Test
    @DisplayName("이미 검토된(PENDING이 아닌) 취소 신청은 FAIR_CANCEL_REQUEST_NOT_PENDING을 던진다")
    void review_이미검토된신청이면_예외를_던진다() {
        given(cancelRequestMapper.selectById(CANCEL_REQUEST_ID)).willReturn(cancelRequest(FairCancelRequestStatus.APPROVED));
        // status = 'PENDING' 조건부 UPDATE라 이미 APPROVED인 행은 실제로 0건 갱신된다.
        given(cancelRequestMapper.update(any())).willReturn(0);

        assertErrorCode(
                () -> cancelRequestService.review(
                        FAIR_ID, CANCEL_REQUEST_ID, REVIEWER_ID,
                        new ReviewFairCancelRequestRequest(FairReviewDecision.APPROVE, null)
                ),
                ErrorCode.FAIR_CANCEL_REQUEST_NOT_PENDING
        );
        verify(fairMapper, never()).update(any());
    }

    /**
     * 조건부 UPDATE(동시성 방어)의 핵심 케이스: selectById로 읽은 시점엔 PENDING이었지만,
     * 그 사이 다른 트랜잭션이 먼저 검토를 끝내서 실제 UPDATE는 0건 갱신되는 경우다.
     * FairTransitionServiceTest의 "갱신 0건" 케이스와 같은 방식으로 검증한다.
     */
    @Test
    @DisplayName("조회 이후 이미 다른 트랜잭션이 검토를 끝냈으면(갱신 0건) fairs를 건드리지 않고 FAIR_CANCEL_REQUEST_NOT_PENDING을 던진다")
    void review_동시검토로_이미처리됐으면_fairs를_건드리지않는다() {
        given(cancelRequestMapper.selectById(CANCEL_REQUEST_ID)).willReturn(cancelRequest(FairCancelRequestStatus.PENDING));
        given(cancelRequestMapper.update(any())).willReturn(0);

        assertErrorCode(
                () -> cancelRequestService.review(
                        FAIR_ID, CANCEL_REQUEST_ID, REVIEWER_ID,
                        new ReviewFairCancelRequestRequest(FairReviewDecision.APPROVE, null)
                ),
                ErrorCode.FAIR_CANCEL_REQUEST_NOT_PENDING
        );
        verify(fairMapper, never()).update(any());
    }

    @Test
    @DisplayName("다른 행사 소속 취소 신청을 검토하면 FAIR_CANCEL_REQUEST_NOT_FOUND를 던진다")
    void review_다른행사소속이면_예외를_던진다() {
        FairCancelRequest cancelRequest = cancelRequest(FairCancelRequestStatus.PENDING);
        cancelRequest.setFairId(OTHER_FAIR_ID);
        given(cancelRequestMapper.selectById(CANCEL_REQUEST_ID)).willReturn(cancelRequest);

        assertErrorCode(
                () -> cancelRequestService.review(
                        FAIR_ID, CANCEL_REQUEST_ID, REVIEWER_ID,
                        new ReviewFairCancelRequestRequest(FairReviewDecision.APPROVE, null)
                ),
                ErrorCode.FAIR_CANCEL_REQUEST_NOT_FOUND
        );
        verify(cancelRequestMapper, never()).update(any());
    }

    @Test
    @DisplayName("존재하지 않는 취소 신청을 검토하면 FAIR_CANCEL_REQUEST_NOT_FOUND를 던진다")
    void review_존재하지않으면_예외를_던진다() {
        given(cancelRequestMapper.selectById(CANCEL_REQUEST_ID)).willReturn(null);

        assertErrorCode(
                () -> cancelRequestService.review(
                        FAIR_ID, CANCEL_REQUEST_ID, REVIEWER_ID,
                        new ReviewFairCancelRequestRequest(FairReviewDecision.APPROVE, null)
                ),
                ErrorCode.FAIR_CANCEL_REQUEST_NOT_FOUND
        );
    }

    // ===== fixtures =====

    private Fair fairWithStatus(FairStatus status) {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setStatus(status);
        return fair;
    }

    /** review() 성공 경로에서 알림 본문에 쓰는 행사 이름 조회용. */
    private Fair fairWithName(String name) {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setName(name);
        return fair;
    }

    private FairCancelRequest cancelRequest(FairCancelRequestStatus status) {
        FairCancelRequest cancelRequest = new FairCancelRequest();
        cancelRequest.setFairCancelRequestId(CANCEL_REQUEST_ID);
        cancelRequest.setFairId(FAIR_ID);
        cancelRequest.setRequestedBy(REQUESTED_BY);
        cancelRequest.setReason("경영상 사유");
        cancelRequest.setStatus(status);
        cancelRequest.setCreatedAt(NOW.minusDays(1));
        return cancelRequest;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
