package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.payment.dto.PaymentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 행사 취소 승인 뒤 환불 대상 결제를 찾기 위해 PAYMENT/REFUND 테이블(결제 도메인 소유)을
 * 읽기 전용으로 조회한다. recruitnotice/reservation 도메인이 fairs.canceled_at을 직접
 * 조회하는 것과 같은 관례다 - 도메인 간 이벤트 발행 구조가 아직 없어서, 필요한 도메인이
 * 상대 테이블을 직접 읽는 것을 전제로 한다.
 *
 * <p>쓰기는 하지 않는다 - 환불 생성 자체는 {@link com.ms.petopia.api.refund.service.RefundService#refund}를
 * 그대로 호출해서 그 도메인이 가진 검증(COMPLETED 여부, 정산 재계산 표시 등)을 그대로 탄다.
 */
@Mapper
public interface FairCancelRefundMapper {

    /**
     * 취소된 행사의 환불 대상 결제를 조회한다 - 관람객 예약금(RESERVATION_DEPOSIT)과
     * 참가비(VENDOR_FEE) 중 결제완료(COMPLETED)됐고 아직 환불되지 않은 건만 대상이다.
     * 개설비(FAIR_OPENING_FEE)는 제외한다 - {@code RefundReason.OPENING_FEE_MANUAL} 주석대로
     * 관리자가 수동으로 처리하는 대상이라 자동 환불 범위가 아니다.
     */
    List<PaymentRow> selectRefundablePaymentsByFairId(@Param("fairId") Long fairId);
}
