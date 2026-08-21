package com.ms.petopia.api.settlement.service;

import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.commisionrate.service.CommissionRateService;
import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.dto.FairRevenueSummaryRow;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.api.settlement.client.FairContractClient;
import com.ms.petopia.api.settlement.dto.FairCancellationStatus;
import com.ms.petopia.api.settlement.dto.FairRevenueSummaryResponse;
import com.ms.petopia.api.settlement.dto.SettlementItemRow;
import com.ms.petopia.api.settlement.dto.SettlementResponse;
import com.ms.petopia.api.settlement.dto.SettlementRow;
import com.ms.petopia.api.settlement.mapper.SettlementMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private RefundMapper refundMapper;

    @Mock
    private CommissionRateService commissionRateService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RecruitNoticeMapper recruitNoticeMapper;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private FairContractClient fairContractClient;

    @Mock
    private FairAdminAccessGuard fairAdminAccessGuard;

    @InjectMocks
    private SettlementService settlementService;

    /**
     * calculate/confirm 테스트 대부분은 취소 여부 자체를 검증 대상으로 삼지 않으므로,
     * "취소 안 됨"을 기본값으로 깔아둔다 — 취소 케이스를 검증하는 테스트만 이 스텁을
     * 별도로 덮어쓴다. lenient인 이유는 이 스텁을 실제로 안 쓰는 테스트(예외를 먼저
     * 던지고 끝나는 경우)에서 UnnecessaryStubbingException이 나지 않게 하기 위함.
     */
    @BeforeEach
    void stubFairNotCanceledByDefault() {
        lenient().when(fairContractClient.getCancellationStatus(anyLong()))
                .thenReturn(notCanceledStatus(10L));
    }

    private FairCancellationStatus notCanceledStatus(Long fairId) {
        return new FairCancellationStatus(fairId, false, null);
    }

    private FairCancellationStatus canceledStatus(Long fairId) {
        return new FairCancellationStatus(fairId, true, LocalDateTime.of(2026, 8, 1, 0, 0));
    }

    private PaymentRow vendorFeePayment(Long paymentId, long amount) {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(paymentId);
        row.setPaymentType("VENDOR_FEE");
        row.setAmount(amount);
        row.setStatus("COMPLETED");
        row.setFairId(10L);
        row.setBusinessId(20L);
        return row;
    }

    @Test
    @DisplayName("환불 없는 완료 참가비 결제들로 정산을 계산하면 수수료율만큼 차감된 지급액이 나온다")
    void calculate_환불없음_성공() {
        // Arrange: 참가비 결제 두 건, 합계 150000원, 환불 없음
        given(settlementMapper.selectByFairAndBusiness(10L, 20L)).willReturn(null);
        given(paymentMapper.selectCompletedVendorFeePayments(10L, 20L)).willReturn(List.of(
                vendorFeePayment(1L, 100000L),
                vendorFeePayment(2L, 50000L)
        ));
        given(refundMapper.selectByPaymentId(1L)).willReturn(null);
        given(refundMapper.selectByPaymentId(2L)).willReturn(null);
        given(commissionRateService.resolveEffectiveRate(10L)).willReturn(new BigDecimal("0.0500"));

        // Act
        SettlementResponse result = settlementService.calculate(10L, 20L);

        // Assert: gross 150000, refund 0, commission 150000*0.05=7500, net 142500
        assertThat(result.grossAmount()).isEqualTo(150000L);
        assertThat(result.refundAmount()).isEqualTo(0L);
        assertThat(result.commissionRate()).isEqualByComparingTo(new BigDecimal("0.0500"));
        assertThat(result.commissionAmount()).isEqualTo(7500L);
        assertThat(result.netAmount()).isEqualTo(142500L);
        assertThat(result.status()).isEqualTo("PENDING");

        verify(settlementMapper).insertItems(anyList());
    }

    @Test
    @DisplayName("환불된 결제가 섞여 있으면 환불금액만큼 차감한 뒤 수수료를 계산한다")
    void calculate_환불포함_차감후계산() {
        // Arrange: 결제1(100000원, 전액환불) + 결제2(50000원, 환불없음)
        given(settlementMapper.selectByFairAndBusiness(10L, 20L)).willReturn(null);
        given(paymentMapper.selectCompletedVendorFeePayments(10L, 20L)).willReturn(List.of(
                vendorFeePayment(1L, 100000L),
                vendorFeePayment(2L, 50000L)
        ));
        RefundRow refund = new RefundRow();
        refund.setRefundId(5L);
        refund.setStatus("COMPLETED");
        refund.setRefundAmount(100000L);
        given(refundMapper.selectByPaymentId(1L)).willReturn(refund);
        given(refundMapper.selectByPaymentId(2L)).willReturn(null);
        given(commissionRateService.resolveEffectiveRate(10L)).willReturn(new BigDecimal("0.0500"));

        // Act
        SettlementResponse result = settlementService.calculate(10L, 20L);

        // Assert: gross 150000, refund 100000, 정산대상 50000, commission 50000*0.05=2500, net 47500
        assertThat(result.grossAmount()).isEqualTo(150000L);
        assertThat(result.refundAmount()).isEqualTo(100000L);
        assertThat(result.commissionAmount()).isEqualTo(2500L);
        assertThat(result.netAmount()).isEqualTo(47500L);
    }

    @Test
    @DisplayName("완료된 참가비 결제가 하나도 없으면 0원 정산으로 계산되고 상세내역은 저장하지 않는다")
    void calculate_결제없음_0원정산() {
        given(settlementMapper.selectByFairAndBusiness(10L, 20L)).willReturn(null);
        given(paymentMapper.selectCompletedVendorFeePayments(10L, 20L)).willReturn(List.of());
        given(commissionRateService.resolveEffectiveRate(10L)).willReturn(new BigDecimal("0.0500"));

        SettlementResponse result = settlementService.calculate(10L, 20L);

        assertThat(result.grossAmount()).isEqualTo(0L);
        assertThat(result.netAmount()).isEqualTo(0L);
        verify(settlementMapper, never()).insertItems(anyList());
    }

    @Test
    @DisplayName("이미 계산된 정산이 있으면 다시 계산할 수 없다")
    void calculate_이미존재_예외를던진다() {
        SettlementRow existing = new SettlementRow();
        existing.setSettlementId(1L);
        given(settlementMapper.selectByFairAndBusiness(10L, 20L)).willReturn(existing);

        assertThatThrownBy(() -> settlementService.calculate(10L, 20L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("동시에 계산 요청이 들어와 UK 제약을 위반하면 이미 존재하는 정산으로 처리한다")
    void calculate_동시계산_중복키_예외를던진다() {
        given(settlementMapper.selectByFairAndBusiness(10L, 20L)).willReturn(null);
        given(paymentMapper.selectCompletedVendorFeePayments(10L, 20L)).willReturn(List.of());
        given(commissionRateService.resolveEffectiveRate(10L)).willReturn(new BigDecimal("0.0500"));
        willThrow(new DuplicateKeyException("settlement fair-business unique violation"))
                .given(settlementMapper).insert(any(SettlementRow.class));

        assertThatThrownBy(() -> settlementService.calculate(10L, 20L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("취소된 행사는 정산을 계산할 수 없다")
    void calculate_취소된행사_예외를던진다() {
        given(fairContractClient.getCancellationStatus(10L)).willReturn(canceledStatus(10L));

        assertThatThrownBy(() -> settlementService.calculate(10L, 20L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_FAIR_CANCELED);

        verify(settlementMapper, never()).selectByFairAndBusiness(any(), any());
        verify(settlementMapper, never()).insert(any(SettlementRow.class));
    }

    @Test
    @DisplayName("다른 행사 담당 EVENT_ADMIN은 정산을 계산할 수 없다")
    void calculate_행사담당자아님_예외를던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(10L);

        assertThatThrownBy(() -> settlementService.calculate(10L, 20L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(settlementMapper, never()).insert(any(SettlementRow.class));
    }

    private SettlementRow pendingSettlementRow() {
        SettlementRow row = new SettlementRow();
        row.setSettlementId(1L);
        row.setFairId(10L);
        row.setBusinessId(20L);
        row.setGrossAmount(150000L);
        row.setRefundAmount(0L);
        row.setCommissionRate(new BigDecimal("0.0500"));
        row.setCommissionAmount(7500L);
        row.setNetAmount(142500L);
        row.setStatus("PENDING");
        return row;
    }

    @Test
    @DisplayName("PENDING 정산을 확정하면 CONFIRMED로 바뀐다")
    void confirm_성공() {
        given(settlementMapper.selectById(1L)).willReturn(pendingSettlementRow());
        given(settlementMapper.confirm(eq(1L), eq(99L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(1);

        SettlementResponse result = settlementService.confirm(1L, 99L);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.confirmedByUserId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("존재하지 않는 정산을 확정하려 하면 예외를 던진다")
    void confirm_존재하지않음_예외를던진다() {
        given(settlementMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> settlementService.confirm(999L, 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 행사 담당 EVENT_ADMIN은 정산을 확정할 수 없다")
    void confirm_행사담당자아님_예외를던진다() {
        given(settlementMapper.selectById(1L)).willReturn(pendingSettlementRow());
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(10L);

        assertThatThrownBy(() -> settlementService.confirm(1L, 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(settlementMapper, never()).confirm(any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 확정된 정산을 다시 확정하려 하면 예외를 던진다")
    void confirm_PENDING아님_예외를던진다() {
        SettlementRow row = pendingSettlementRow();
        row.setStatus("CONFIRMED");
        given(settlementMapper.selectById(1L)).willReturn(row);

        assertThatThrownBy(() -> settlementService.confirm(1L, 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_CONFIRMABLE);
    }

    @Test
    @DisplayName("재계산이 필요한 정산은 확정할 수 없다")
    void confirm_재계산필요_예외를던진다() {
        // Arrange: PENDING이긴 하지만 환불로 인해 needs_recalculation이 서 있는 상황
        // (CodeRabbit 리뷰 지적, PR #54 — recalculate 없이 confirm하면 옛날 금액으로 굳어버림)
        SettlementRow row = pendingSettlementRow();
        row.setNeedsRecalculation(true);
        given(settlementMapper.selectById(1L)).willReturn(row);

        assertThatThrownBy(() -> settlementService.confirm(1L, 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_RECALCULATION_REQUIRED);

        verify(settlementMapper, never()).confirm(any(), any(), any(), any());
    }

    @Test
    @DisplayName("동시에 두 번 확정 요청이 들어오면 나중 요청은 예외를 던진다")
    void confirm_동시확정_예외를던진다() {
        given(settlementMapper.selectById(1L)).willReturn(pendingSettlementRow());
        given(settlementMapper.confirm(eq(1L), eq(99L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(0);

        assertThatThrownBy(() -> settlementService.confirm(1L, 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_CONFIRMABLE);
    }

    @Test
    @DisplayName("취소된 행사의 정산은 확정할 수 없다")
    void confirm_취소된행사_예외를던진다() {
        // Arrange: 정산 자체는 PENDING으로 정상이지만, 그 사이 행사가 취소된 상황
        given(settlementMapper.selectById(1L)).willReturn(pendingSettlementRow());
        given(fairContractClient.getCancellationStatus(10L)).willReturn(canceledStatus(10L));

        assertThatThrownBy(() -> settlementService.confirm(1L, 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_FAIR_CANCELED);

        verify(settlementMapper, never()).confirm(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PENDING 정산을 재계산하면 최신 결제·환불 상태로 금액이 갱신된다")
    void recalculate_PENDING_성공() {
        // Arrange: 계산 당시엔 없던 결제(3L)가 그 사이 새로 완료된 상황
        given(settlementMapper.selectById(1L)).willReturn(pendingSettlementRow());
        given(paymentMapper.selectCompletedVendorFeePayments(10L, 20L)).willReturn(List.of(
                vendorFeePayment(1L, 100000L),
                vendorFeePayment(2L, 50000L),
                vendorFeePayment(3L, 80000L)
        ));
        given(refundMapper.selectByPaymentId(1L)).willReturn(null);
        given(refundMapper.selectByPaymentId(2L)).willReturn(null);
        given(refundMapper.selectByPaymentId(3L)).willReturn(null);
        given(settlementMapper.updateAggregates(
                eq(1L), eq(230000L), eq(0L), eq(11500L), eq(218500L), any(LocalDateTime.class)))
                .willReturn(1);

        // Act
        SettlementResponse result = settlementService.recalculate(1L);

        // Assert: gross 230000, commission 230000*0.05=11500, net 218500
        assertThat(result.grossAmount()).isEqualTo(230000L);
        assertThat(result.commissionAmount()).isEqualTo(11500L);
        assertThat(result.netAmount()).isEqualTo(218500L);

        // 기존 감사근거는 지우고 최신 내역으로 다시 채운다
        verify(settlementMapper).deleteItemsBySettlementId(1L);
        verify(settlementMapper).insertItems(anyList());
    }

    @Test
    @DisplayName("존재하지 않는 정산을 재계산하려 하면 예외를 던진다")
    void recalculate_존재하지않음_예외를던진다() {
        given(settlementMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> settlementService.recalculate(999L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 행사 담당 EVENT_ADMIN은 정산을 재계산할 수 없다")
    void recalculate_행사담당자아님_예외를던진다() {
        given(settlementMapper.selectById(1L)).willReturn(pendingSettlementRow());
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(10L);

        assertThatThrownBy(() -> settlementService.recalculate(1L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(settlementMapper, never()).deleteItemsBySettlementId(any());
    }

    @Test
    @DisplayName("CONFIRMED 정산은 재계산할 수 없다 — 확정 이후 금액은 불변")
    void recalculate_CONFIRMED_예외를던진다() {
        SettlementRow row = pendingSettlementRow();
        row.setStatus("CONFIRMED");
        given(settlementMapper.selectById(1L)).willReturn(row);

        assertThatThrownBy(() -> settlementService.recalculate(1L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_RECALCULABLE);

        verify(settlementMapper, never()).deleteItemsBySettlementId(any());
    }

    @Test
    @DisplayName("재계산 도중 동시에 확정되면 예외를 던진다")
    void recalculate_동시확정_예외를던진다() {
        // Arrange: selectById로 PENDING 확인한 직후, UPDATE 시점엔 이미 다른 요청이 확정해버린 경우
        given(settlementMapper.selectById(1L)).willReturn(pendingSettlementRow());
        given(paymentMapper.selectCompletedVendorFeePayments(10L, 20L)).willReturn(List.of());
        given(settlementMapper.updateAggregates(
                eq(1L), eq(0L), eq(0L), eq(0L), eq(0L), any(LocalDateTime.class)))
                .willReturn(0);

        assertThatThrownBy(() -> settlementService.recalculate(1L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_RECALCULABLE);
    }

    @Test
    @DisplayName("행사·업체로 정산을 조회하면 상세를 반환한다")
    void getByFairAndBusiness_존재_상세반환() {
        given(settlementMapper.selectByFairAndBusiness(10L, 20L)).willReturn(pendingSettlementRow());

        SettlementResponse result = settlementService.getByFairAndBusiness(10L, 20L);

        assertThat(result.settlementId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("계산된 적 없는 행사·업체로 조회하면 예외를 던진다")
    void getByFairAndBusiness_없음_예외를던진다() {
        given(settlementMapper.selectByFairAndBusiness(10L, 30L)).willReturn(null);

        assertThatThrownBy(() -> settlementService.getByFairAndBusiness(10L, 30L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 행사 담당 EVENT_ADMIN은 정산 단건을 조회할 수 없다")
    void getByFairAndBusiness_행사담당자아님_예외를던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(10L);

        assertThatThrownBy(() -> settlementService.getByFairAndBusiness(10L, 20L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(settlementMapper, never()).selectByFairAndBusiness(any(), any());
    }

    @Test
    @DisplayName("행사 하나에 속한 정산 목록을 조회한다")
    void getByFair_목록반환() {
        SettlementRow other = pendingSettlementRow();
        other.setSettlementId(2L);
        other.setBusinessId(30L);
        given(settlementMapper.selectByFairId(10L)).willReturn(List.of(pendingSettlementRow(), other));

        List<SettlementResponse> results = settlementService.getByFair(10L);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("다른 행사 담당 EVENT_ADMIN은 정산 목록을 조회할 수 없다")
    void getByFair_행사담당자아님_예외를던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(10L);

        assertThatThrownBy(() -> settlementService.getByFair(10L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(settlementMapper, never()).selectByFairId(any());
    }

    @Test
    @DisplayName("정산 통합검색 - fairId만 있으면 그 행사 담당자 확인 후 조회한다")
    void getByFilter_fairId만_행사담당자확인() {
        given(settlementMapper.selectByFilter(10L, null)).willReturn(List.of(pendingSettlementRow()));

        List<SettlementResponse> results = settlementService.getByFilter(10L, null);

        assertThat(results).hasSize(1);
        verify(fairAdminAccessGuard).checkAssigned(10L);
        verify(fairAdminAccessGuard, never()).requireSuperAdmin();
    }

    @Test
    @DisplayName("정산 통합검색 - businessId만 있으면 여러 행사를 넘나드니 SUPER_ADMIN만 허용한다")
    void getByFilter_businessId만_슈퍼어드민만() {
        given(settlementMapper.selectByFilter(null, 20L)).willReturn(List.of(pendingSettlementRow()));

        List<SettlementResponse> results = settlementService.getByFilter(null, 20L);

        assertThat(results).hasSize(1);
        verify(fairAdminAccessGuard).requireSuperAdmin();
        verify(fairAdminAccessGuard, never()).checkAssigned(any());
    }

    @Test
    @DisplayName("정산 통합검색 - businessId만 조회인데 SUPER_ADMIN이 아니면 예외를 던진다")
    void getByFilter_businessId만_슈퍼어드민아님_예외를던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).requireSuperAdmin();

        assertThatThrownBy(() -> settlementService.getByFilter(null, 20L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(settlementMapper, never()).selectByFilter(any(), any());
    }

    @Test
    @DisplayName("정산 통합검색 - fairId·businessId 둘 다 없으면 예외를 던진다")
    void getByFilter_둘다없음_예외를던진다() {
        assertThatThrownBy(() -> settlementService.getByFilter(null, null))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(settlementMapper, never()).selectByFilter(any(), any());
    }

    private SettlementRow confirmedSettlementRow() {
        SettlementRow row = pendingSettlementRow();
        row.setStatus("CONFIRMED");
        row.setConfirmedByUserId(99L);
        row.setConfirmedAt(LocalDateTime.of(2026, 8, 19, 10, 0));
        return row;
    }

    @Test
    @DisplayName("CONFIRMED 정산을 되돌리면 PENDING으로 바뀌고 확정 정보가 지워진다")
    void reopen_성공() {
        given(settlementMapper.selectById(1L)).willReturn(confirmedSettlementRow());
        given(settlementMapper.reopen(eq(1L), any(LocalDateTime.class))).willReturn(1);

        SettlementResponse result = settlementService.reopen(1L, 200L);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.confirmedByUserId()).isNull();
        assertThat(result.confirmedAt()).isNull();
        verify(auditLogService).record(
                eq(200L), eq(ActorType.ADMIN), eq("SUPER_ADMIN"), eq(ActionType.SETTLEMENT_REOPEN),
                eq(TargetType.SETTLEMENT), eq(1L), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 정산을 되돌리려 하면 예외를 던진다")
    void reopen_존재하지않음_예외를던진다() {
        given(settlementMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> settlementService.reopen(999L, 200L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("SUPER_ADMIN이 아니면 확정된 정산을 되돌릴 수 없다")
    void reopen_슈퍼어드민아님_예외를던진다() {
        given(settlementMapper.selectById(1L)).willReturn(confirmedSettlementRow());
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).requireSuperAdmin();

        assertThatThrownBy(() -> settlementService.reopen(1L, 200L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(settlementMapper, never()).reopen(any(), any());
    }

    @Test
    @DisplayName("PENDING 정산은 되돌릴 수 없다 — 이미 확정 전 상태")
    void reopen_PENDING_예외를던진다() {
        given(settlementMapper.selectById(1L)).willReturn(pendingSettlementRow());

        assertThatThrownBy(() -> settlementService.reopen(1L, 200L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_REOPENABLE);

        verify(settlementMapper, never()).reopen(any(), any());
    }

    @Test
    @DisplayName("되돌리는 도중 동시에 다른 요청이 먼저 처리하면 예외를 던진다")
    void reopen_동시처리_예외를던진다() {
        given(settlementMapper.selectById(1L)).willReturn(confirmedSettlementRow());
        given(settlementMapper.reopen(eq(1L), any(LocalDateTime.class))).willReturn(0);

        assertThatThrownBy(() -> settlementService.reopen(1L, 200L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_NOT_REOPENABLE);
    }

    @Test
    @DisplayName("되돌린 뒤 재계산 없이 바로 확정하려 하면 예외를 던진다 — 옛날 금액으로 재확정되는 걸 막는다")
    void reopen_이후_재계산없이확정하면_예외를던진다() {
        // Arrange: reopen() 성공 (CodeRabbit 리뷰 지적, PR #181 — reopen이 needs_recalculation을
        // TRUE로 세우지 않으면, CONFIRMED 시점엔 이미 FALSE였던 이 값 때문에 recalculate 없이도
        // confirm()이 그대로 통과해버려 정정 전 옛날 금액이 재확정될 수 있었다.)
        given(settlementMapper.selectById(1L)).willReturn(confirmedSettlementRow());
        given(settlementMapper.reopen(eq(1L), any(LocalDateTime.class))).willReturn(1);
        settlementService.reopen(1L, 200L);

        // Act: 실제 DB라면 reopen()이 needs_recalculation=TRUE로 세워둔 상태 — 그 상태를 반영한
        // row로 다시 조회되는 상황을 시뮬레이션한다.
        SettlementRow reopenedRow = pendingSettlementRow();
        reopenedRow.setNeedsRecalculation(true);
        given(settlementMapper.selectById(1L)).willReturn(reopenedRow);

        // Assert: recalculate() 없이 바로 confirm()을 부르면 막혀야 한다.
        assertThatThrownBy(() -> settlementService.confirm(1L, 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_RECALCULATION_REQUIRED);

        verify(settlementMapper, never()).confirm(any(), any(), any(), any());
    }

    // ── getFairRevenueSummaries (행사별 매출 요약, WBS 5.6) ──

    private FairRevenueSummaryRow revenueSummaryRow(Long fairId, String fairName, long ticketAmount, long vendorFeeAmount) {
        FairRevenueSummaryRow row = new FairRevenueSummaryRow();
        row.setFairId(fairId);
        row.setFairName(fairName);
        row.setTicketAmount(ticketAmount);
        row.setVendorFeeAmount(vendorFeeAmount);
        return row;
    }

    @Test
    @DisplayName("행사별 매출 요약은 티켓예매+참가비를 합산해 수수료율만큼 플랫폼/행사업체 몫으로 나눈다")
    void getFairRevenueSummaries_티켓과참가비합산_수수료율분배() {
        // Arrange: 티켓 70000원 + 참가비 30000원 = 전체 100000원, 수수료율 10%
        given(paymentMapper.selectFairRevenueSummary())
                .willReturn(List.of(revenueSummaryRow(10L, "댕댕펫", 70000L, 30000L)));
        given(commissionRateService.resolveEffectiveRate(10L)).willReturn(new BigDecimal("0.1000"));

        List<FairRevenueSummaryResponse> result = settlementService.getFairRevenueSummaries();

        assertThat(result).hasSize(1);
        FairRevenueSummaryResponse summary = result.get(0);
        assertThat(summary.fairId()).isEqualTo(10L);
        assertThat(summary.fairName()).isEqualTo("댕댕펫");
        assertThat(summary.ticketAmount()).isEqualTo(70000L);
        assertThat(summary.vendorFeeAmount()).isEqualTo(30000L);
        assertThat(summary.grossAmount()).isEqualTo(100000L);
        assertThat(summary.platformAmount()).isEqualTo(10000L);
        assertThat(summary.businessAmount()).isEqualTo(90000L);
    }

    @Test
    @DisplayName("행사별 매출 요약은 매출이 0원인 행사도 목록에 포함시킨다")
    void getFairRevenueSummaries_매출없는행사도포함() {
        given(paymentMapper.selectFairRevenueSummary())
                .willReturn(List.of(revenueSummaryRow(11L, "빈 행사", 0L, 0L)));
        given(commissionRateService.resolveEffectiveRate(11L)).willReturn(new BigDecimal("0.1000"));

        List<FairRevenueSummaryResponse> result = settlementService.getFairRevenueSummaries();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).grossAmount()).isZero();
        assertThat(result.get(0).platformAmount()).isZero();
        assertThat(result.get(0).businessAmount()).isZero();
    }

    @Test
    @DisplayName("행사별 매출 요약은 각 행사에 지금 적용되는 요율을 행사별로 따로 조회해서 쓴다")
    void getFairRevenueSummaries_행사마다요율따로조회() {
        given(paymentMapper.selectFairRevenueSummary())
                .willReturn(List.of(
                        revenueSummaryRow(10L, "댕댕펫", 100000L, 0L),
                        revenueSummaryRow(11L, "냥이", 100000L, 0L)
                ));
        given(commissionRateService.resolveEffectiveRate(10L)).willReturn(new BigDecimal("0.1000"));
        given(commissionRateService.resolveEffectiveRate(11L)).willReturn(new BigDecimal("0.2000"));

        List<FairRevenueSummaryResponse> result = settlementService.getFairRevenueSummaries();

        assertThat(result).extracting(FairRevenueSummaryResponse::platformAmount)
                .containsExactly(10000L, 20000L);
        verify(commissionRateService).resolveEffectiveRate(10L);
        verify(commissionRateService).resolveEffectiveRate(11L);
    }
}
