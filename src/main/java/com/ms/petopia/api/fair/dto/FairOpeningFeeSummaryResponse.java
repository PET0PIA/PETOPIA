package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 개설비 결제 페이지 전용 요약 조회 응답. 신청서 전체 상세({@link FairApplicationDetailResponse})와
 * 달리 결제에 필요한 최소 정보만 담는다 - 이 화면을 보는 사람은 신청자 본인이 아니라 승인 시
 * 행사 담당자로 배정된 신청자(EVENT_ADMIN) 계정이라({@code AdminAccountService#assignApplicantAsEventAdmin})
 * managerPhone/managerEmail 같은 PII도 노출하지 않는다.
 *
 * @param status           승인 전(RECEIVED)이거나 반려(REJECTED)·만료(EXPIRED)면 결제할 수 없는
 *                         상태라는 걸 화면이 이 값으로 판단해 적절한 안내를 보여준다.
 * @param openingFeeAmount 승인 시 확정된 개설비 금액(원). 승인 전이면 null.
 * @param paymentDueAt     개설비 결제 기한. 승인 전이면 null.
 * @param canceledAt       행사 취소가 확정된 시각. 취소 안 됐으면 null(2026-08-23 추가) - 취소 승인은
 *                         status를 안 바꾸고 이 필드만 채우므로(FairCancelRequestService#review),
 *                         화면이 결제 가능 여부·취소 배지를 판단하려면 status만으로는 부족하다.
 */
public record FairOpeningFeeSummaryResponse(
        Long fairId,
        String name,
        String status,
        Long openingFeeAmount,
        LocalDateTime paymentDueAt,
        LocalDateTime canceledAt
) {
}
