package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 예약 단건 상세. 목록 아이템 필드에 더해 예약 유형과 케밥 메뉴 노출용 가능 여부 플래그를 담는다.
 * canChangeVisitDate·canCancel은 상태·유형에 마감 시각까지 반영한 판단이지만, 최종 검증은
 * 방문일 변경·취소 API가 한다(그 사이 마감이 지나면 R018·R019).
 *
 * @param pets          동반 반려동물의 예약 시점 스냅샷. 동반이 없으면 빈 목록이다.
 *                      원본(pets)이 수정·삭제돼도 이 값은 변하지 않는다(정책 P5)
 * @param paymentId     예약금 결제의 ID. 무료 예약(결제 행 없음)이면 null
 * @param paymentMethod 표시용 결제수단. 결제 완료 전이면 "결제 전", 무료 예약이면 null.
 *                      DB의 payment.status·method·easy_pay_provider를 조합한 완성 문구다 —
 *                      화면이 상태별 분기를 다시 들고 있지 않도록 서버에서 판단을 끝낸다
 *                      (ReservationQueryService.paymentMethodLabel 참고)
 */
public record ReservationDetailResponse(
        Long reservationId,
        String reservationNo,
        Long fairId,
        String fairName,
        String fairPosterImageUrl,
        LocalDate visitDate,
        LocalTime entryStartTime,
        LocalTime entryEndTime,
        String reservationStatus,
        String reservationType,
        boolean isEnded,
        boolean qrAvailable,
        boolean paymentAvailable,
        /** 결제 대기 예약의 결제 제한시각. 결제 대기가 아니거나 무료 예약이면 null. */
        LocalDateTime paymentExpiresAt,
        long amount,
        LocalDateTime reservedAt,
        LocalDateTime checkedInAt,
        boolean canChangeVisitDate,
        boolean canCancel,
        /**
         * 이 시각까지 방문일을 변경할 수 있다. 행사가 정한 기한(없으면 기본 12시간)을
         * 입장 시작 시각에서 뺀 값이다. 방문일·입장시각이 없거나 설정이 잘못된(음수) 행사면 null.
         */
        LocalDateTime changeDeadlineAt,
        /**
         * 이 시각까지 예약을 취소할 수 있다. 계산 방식은 changeDeadlineAt과 같다.
         * 결제 대기(PENDING_PAYMENT) 예약의 취소에는 이 마감이 적용되지 않는다 -
         * 아직 받은 돈이 없어 언제든 취소할 수 있다(ReservationCancellationService 참고).
         */
        LocalDateTime cancelDeadlineAt,
        Long paymentId,
        String paymentMethod,
        List<ReservationPetResponse> pets,
        /** 주최측 행사 취소로 자동 취소된 예약인지. 사용자가 직접 취소한 건과 구분해 안내한다. */
        boolean canceledByFairCancellation
) {
}
