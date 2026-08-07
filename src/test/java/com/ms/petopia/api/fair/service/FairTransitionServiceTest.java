package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.FairTransitionRow;
import com.ms.petopia.api.fair.mapper.FairTransitionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FairTransitionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 10, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Mock
    private FairTransitionMapper transitionMapper;

    @Mock
    private FairTimeProvider timeProvider;

    @InjectMocks
    private FairTransitionService transitionService;

    // ===== completeDuePayments =====

    @Test
    @DisplayName("개설비 결제가 완료된 PAYMENT_PENDING 행사를 PREPARING으로 바꾼다")
    void completeDuePayments_결제완료된행사를_전환한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(transitionMapper.selectPaymentCompletedForUpdate(200)).willReturn(List.of(row(4L), row(5L)));
        given(transitionMapper.completeFairPayment(4L, NOW)).willReturn(1);
        given(transitionMapper.completeFairPayment(5L, NOW)).willReturn(1);

        assertThat(transitionService.completeDuePayments(200)).isEqualTo(2);
        verify(transitionMapper).completeFairPayment(4L, NOW);
        verify(transitionMapper).completeFairPayment(5L, NOW);
    }

    /**
     * 조회(FOR UPDATE SKIP LOCKED) 이후 다른 트랜잭션이 먼저 상태를 바꿨으면(예: 그 사이
     * 취소 승인이 처리됨) 조건부 UPDATE가 0건이라 그 건은 집계에서 빠진다.
     */
    @Test
    @DisplayName("조회 이후 이미 다른 트랜잭션이 상태를 바꿨으면(갱신 0건) 집계에서 제외한다")
    void completeDuePayments_동시전환은_집계에서제외한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(transitionMapper.selectPaymentCompletedForUpdate(200)).willReturn(List.of(row(4L)));
        given(transitionMapper.completeFairPayment(4L, NOW)).willReturn(0);

        assertThat(transitionService.completeDuePayments(200)).isZero();
    }

    @Test
    @DisplayName("대상이 없으면 갱신을 호출하지 않고 0을 반환한다")
    void completeDuePayments_대상없으면_아무것도하지않는다() {
        given(timeProvider.now()).willReturn(NOW);
        given(transitionMapper.selectPaymentCompletedForUpdate(200)).willReturn(List.of());

        assertThat(transitionService.completeDuePayments(200)).isZero();
        verify(transitionMapper, never()).completeFairPayment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ===== expireDuePayments =====

    @Test
    @DisplayName("결제 기한이 지난 PAYMENT_PENDING 행사를 EXPIRED로 바꾸고 갱신 건수를 반환한다")
    void expireDuePayments_기한초과행사를_만료한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(transitionMapper.selectPaymentExpiringForUpdate(NOW, 200)).willReturn(List.of(row(1L)));
        given(transitionMapper.expireFair(1L, NOW)).willReturn(1);

        int expired = transitionService.expireDuePayments(200);

        assertThat(expired).isEqualTo(1);
        verify(transitionMapper).expireFair(1L, NOW);
    }

    @Test
    @DisplayName("조회 이후 이미 다른 트랜잭션이 상태를 바꿨으면(갱신 0건) 카운트에 넣지 않는다")
    void expireDuePayments_동시성으로_이미바뀐행사는_세지않는다() {
        given(timeProvider.now()).willReturn(NOW);
        given(transitionMapper.selectPaymentExpiringForUpdate(NOW, 200)).willReturn(List.of(row(1L)));
        given(transitionMapper.expireFair(1L, NOW)).willReturn(0);

        assertThat(transitionService.expireDuePayments(200)).isZero();
    }

    // ===== startDueFairs =====

    @Test
    @DisplayName("운영 시작일이 된 PREPARING 행사를 IN_PROGRESS로 바꾼다")
    void startDueFairs_시작일이된행사를_진행중으로바꾼다() {
        given(timeProvider.now()).willReturn(NOW);
        given(transitionMapper.selectPreparingToStartForUpdate(TODAY, 200)).willReturn(List.of(row(2L)));
        given(transitionMapper.startFair(2L, NOW)).willReturn(1);

        assertThat(transitionService.startDueFairs(200)).isEqualTo(1);
        verify(transitionMapper).startFair(2L, NOW);
    }

    // ===== endDueFairs =====

    @Test
    @DisplayName("운영 종료일이 지난 IN_PROGRESS 행사를 ENDED로 바꾼다")
    void endDueFairs_종료일이지난행사를_종료한다() {
        given(timeProvider.now()).willReturn(NOW);
        given(transitionMapper.selectInProgressToEndForUpdate(TODAY, 200)).willReturn(List.of(row(3L)));
        given(transitionMapper.endFair(3L, NOW)).willReturn(1);

        assertThat(transitionService.endDueFairs(200)).isEqualTo(1);
        verify(transitionMapper).endFair(3L, NOW);
    }

    @Test
    @DisplayName("대상이 없으면 갱신을 호출하지 않고 0을 반환한다")
    void endDueFairs_대상없으면_아무것도하지않는다() {
        given(timeProvider.now()).willReturn(NOW);
        given(transitionMapper.selectInProgressToEndForUpdate(TODAY, 200)).willReturn(List.of());

        assertThat(transitionService.endDueFairs(200)).isZero();
        verify(transitionMapper, never()).endFair(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ===== 공통 검증 =====

    @Test
    @DisplayName("batchSize가 0 이하면 IllegalArgumentException을 던진다")
    void batchSize가_0이하면_예외를_던진다() {
        assertThatThrownBy(() -> transitionService.completeDuePayments(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transitionService.expireDuePayments(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transitionService.startDueFairs(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transitionService.endDueFairs(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FairTransitionRow row(Long fairId) {
        FairTransitionRow row = new FairTransitionRow();
        row.setFairId(fairId);
        return row;
    }
}
