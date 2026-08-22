package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.FairCancelRefundTarget;
import com.ms.petopia.api.fair.mapper.FairCancelRefundTargetMapper;
import com.ms.petopia.api.payment.dto.PaymentListResponse;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FairCancelRefundOrchestrationServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long TARGET_ID = 500L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 10, 0);

    @Mock
    private FairCancelRefundTargetMapper targetMapper;

    @Mock
    private PaymentService paymentService;

    @Mock
    private RefundService refundService;

    @Mock
    private FairTimeProvider timeProvider;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FairCancelRefundOrchestrationService orchestrationService;

    // ===== enumerateTargets =====

    @Test
    @DisplayName("아직 안 훑은 취소 행사의 COMPLETED 예약금·참가비 결제를 작업행으로 등록하고 완료 기록을 남긴다")
    void enumerateTargets_새결제를_작업행으로_등록한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(targetMapper.selectUnenumeratedCanceledFairIds(50)).willReturn(List.of(FAIR_ID));
        given(paymentService.getPayments(FAIR_ID, null, "RESERVATION_DEPOSIT", "COMPLETED", 0, 100))
                .willReturn(singlePage(payment(1L)));
        given(paymentService.getPayments(FAIR_ID, null, "VENDOR_FEE", "COMPLETED", 0, 100))
                .willReturn(singlePage(payment(2L)));

        int enumerated = orchestrationService.enumerateTargets(50);

        assertThat(enumerated).isEqualTo(2);
        ArgumentCaptor<FairCancelRefundTarget> captor = ArgumentCaptor.forClass(FairCancelRefundTarget.class);
        verify(targetMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(FairCancelRefundTarget::getPaymentId)
                .containsExactlyInAnyOrder(1L, 2L);
        verify(targetMapper).markEnumerationCompleted(FAIR_ID, NOW);
    }

    @Test
    @DisplayName("이미 등록된 결제는 유니크 제약 위반을 무시하고 건너뛰지만 완료 기록은 남긴다")
    void enumerateTargets_이미등록된결제는_건너뛴다() {
        given(timeProvider.now()).willReturn(NOW);
        given(targetMapper.selectUnenumeratedCanceledFairIds(50)).willReturn(List.of(FAIR_ID));
        given(paymentService.getPayments(FAIR_ID, null, "RESERVATION_DEPOSIT", "COMPLETED", 0, 100))
                .willReturn(singlePage(payment(1L)));
        given(paymentService.getPayments(FAIR_ID, null, "VENDOR_FEE", "COMPLETED", 0, 100))
                .willReturn(emptyPage());
        willThrow(new DuplicateKeyException("UK_FAIR_CANCEL_REFUND_TARGETS_PAYMENT"))
                .given(targetMapper).insert(any());

        int enumerated = orchestrationService.enumerateTargets(50);

        assertThat(enumerated).isZero();
        verify(targetMapper).markEnumerationCompleted(FAIR_ID, NOW);
    }

    @Test
    @DisplayName("페이지가 여러 개면 끝까지 순회해서 전부 등록한다")
    void enumerateTargets_여러페이지면_끝까지순회한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(targetMapper.selectUnenumeratedCanceledFairIds(50)).willReturn(List.of(FAIR_ID));
        given(paymentService.getPayments(FAIR_ID, null, "RESERVATION_DEPOSIT", "COMPLETED", 0, 100))
                .willReturn(new PaymentListResponse(List.of(payment(1L)), 0, 100, 2, 2));
        given(paymentService.getPayments(FAIR_ID, null, "RESERVATION_DEPOSIT", "COMPLETED", 1, 100))
                .willReturn(new PaymentListResponse(List.of(payment(2L)), 1, 100, 2, 2));
        given(paymentService.getPayments(FAIR_ID, null, "VENDOR_FEE", "COMPLETED", 0, 100))
                .willReturn(emptyPage());

        int enumerated = orchestrationService.enumerateTargets(50);

        assertThat(enumerated).isEqualTo(2);
    }

    @Test
    @DisplayName("아직 안 훑은 취소 행사가 없으면 0을 반환한다")
    void enumerateTargets_대상없으면_0을반환한다() {
        given(targetMapper.selectUnenumeratedCanceledFairIds(50)).willReturn(List.of());

        assertThat(orchestrationService.enumerateTargets(50)).isZero();
        verify(paymentService, never()).getPayments(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("결제유형 하나라도 조회 중 예외가 나면 그 행사는 완료 기록을 남기지 않고 다음 행사로 넘어간다")
    void enumerateTargets_행사하나실패해도_나머지행사는_계속처리한다() {
        Long otherFairId = 20L;
        given(timeProvider.now()).willReturn(NOW);
        given(targetMapper.selectUnenumeratedCanceledFairIds(50)).willReturn(List.of(FAIR_ID, otherFairId));
        given(paymentService.getPayments(eq(FAIR_ID), any(), any(), any(), anyInt(), anyInt()))
                .willThrow(new IllegalStateException("결제 도메인 일시 장애"));
        given(paymentService.getPayments(eq(otherFairId), any(), any(), any(), anyInt(), anyInt()))
                .willReturn(emptyPage());

        int enumerated = orchestrationService.enumerateTargets(50);

        assertThat(enumerated).isZero();
        verify(targetMapper, never()).markEnumerationCompleted(eq(FAIR_ID), any());
        verify(targetMapper).markEnumerationCompleted(otherFairId, NOW);
    }

    @Test
    @DisplayName("결제가 0건인 행사도 완료 기록을 남긴다")
    void enumerateTargets_결제가0건이어도_완료기록을남긴다() {
        given(timeProvider.now()).willReturn(NOW);
        given(targetMapper.selectUnenumeratedCanceledFairIds(50)).willReturn(List.of(FAIR_ID));
        given(paymentService.getPayments(eq(FAIR_ID), any(), any(), any(), anyInt(), anyInt()))
                .willReturn(emptyPage());

        int enumerated = orchestrationService.enumerateTargets(50);

        assertThat(enumerated).isZero();
        verify(targetMapper).markEnumerationCompleted(FAIR_ID, NOW);
    }

    // ===== processPendingTargets =====

    @Test
    @DisplayName("환불에 성공하면 markCompleted를 호출하고 성공 건수를 반환한다")
    void processPendingTargets_성공하면_완료처리한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(targetMapper.selectPendingForUpdate(200))
                .willReturn(List.of(target(TARGET_ID, "RESERVATION_DEPOSIT")));
        given(refundService.refund(any(), any(), any())).willReturn((RefundResponse) null);
        given(targetMapper.markCompleted(TARGET_ID, NOW)).willReturn(1);

        int completed = orchestrationService.processPendingTargets(200);

        assertThat(completed).isEqualTo(1);
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundService).refund(eq(TARGET_ID), any(), captor.capture());
        assertThat(captor.getValue().refundReason()).isEqualTo(RefundReason.FAIR_CANCEL_USER);
        assertThat(captor.getValue().requestedByDomain()).isEqualTo(RequestedByDomain.FAIR);
        verify(targetMapper).markCompleted(TARGET_ID, NOW);
    }

    @Test
    @DisplayName("재시도해도 성공할 수 없는 실패(이미 환불됨)면 즉시 FAILED로 확정한다")
    void processPendingTargets_영구실패면_바로실패처리한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(targetMapper.selectPendingForUpdate(200))
                .willReturn(List.of(target(TARGET_ID, "VENDOR_FEE")));
        willThrow(new CommonException(ErrorCode.REFUND_ALREADY_PROCESSED))
                .given(refundService).refund(any(), any(), any());

        int completed = orchestrationService.processPendingTargets(200);

        assertThat(completed).isZero();
        verify(targetMapper).markFailed(eq(TARGET_ID), any(), eq(NOW));
        verify(targetMapper, never()).markRetryOrGiveUp(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("예상 못한 실패면 재시도 대상으로 남긴다")
    void processPendingTargets_예상못한실패면_재시도대상으로남긴다() {
        given(timeProvider.now()).willReturn(NOW);
        given(targetMapper.selectPendingForUpdate(200))
                .willReturn(List.of(target(TARGET_ID, "RESERVATION_DEPOSIT")));
        willThrow(new IllegalStateException("일시적 오류")).given(refundService).refund(any(), any(), any());

        int completed = orchestrationService.processPendingTargets(200);

        assertThat(completed).isZero();
        verify(targetMapper).markRetryOrGiveUp(eq(TARGET_ID), any(), eq(5), eq(NOW));
        verify(targetMapper, never()).markFailed(any(), any(), any());
    }

    @Test
    @DisplayName("일부 결제가 실패해도 나머지는 계속 처리하고 성공 건수만 반환한다")
    void processPendingTargets_일부실패해도_나머지는_계속처리한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(targetMapper.selectPendingForUpdate(200)).willReturn(List.of(
                target(1L, "RESERVATION_DEPOSIT"),
                target(2L, "VENDOR_FEE")
        ));
        willThrow(new CommonException(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE))
                .given(refundService).refund(eq(1L), any(), any());
        given(refundService.refund(eq(2L), any(), any())).willReturn((RefundResponse) null);
        given(targetMapper.markCompleted(2L, NOW)).willReturn(1);

        int completed = orchestrationService.processPendingTargets(200);

        assertThat(completed).isEqualTo(1);
        verify(targetMapper).markFailed(eq(1L), any(), eq(NOW));
        verify(targetMapper).markCompleted(2L, NOW);
    }

    @Test
    @DisplayName("대상이 없으면 0을 반환한다")
    void processPendingTargets_대상없으면_0을반환한다() {
        given(targetMapper.selectPendingForUpdate(200)).willReturn(List.of());

        assertThat(orchestrationService.processPendingTargets(200)).isZero();
        verify(refundService, never()).refund(any(), any(), any());
    }

    private FairCancelRefundTarget target(Long targetId, String paymentType) {
        FairCancelRefundTarget target = new FairCancelRefundTarget();
        target.setFairCancelRefundTargetId(targetId);
        target.setFairId(FAIR_ID);
        target.setPaymentId(targetId);
        target.setPaymentType(paymentType);
        target.setStatus(FairCancelRefundTarget.STATUS_PENDING);
        return target;
    }

    private PaymentResponse payment(Long paymentId) {
        return new PaymentResponse(
                paymentId, "PAYMENT_" + paymentId, "RESERVATION_DEPOSIT", 10_000L, "COMPLETED",
                "MOCK", NOW, NOW.minusMinutes(5), FAIR_ID, null, 1L, null, null,
                null, null, null, null,
                null, null, null, null, null, null, null
        );
    }

    private PaymentListResponse singlePage(PaymentResponse... payments) {
        return new PaymentListResponse(List.of(payments), 0, 100, payments.length, 1);
    }

    private PaymentListResponse emptyPage() {
        return new PaymentListResponse(List.of(), 0, 100, 0, 0);
    }
}
