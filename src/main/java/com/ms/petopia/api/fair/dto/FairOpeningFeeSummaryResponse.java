package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 개설비 결제 페이지 전용 요약 조회 응답. 신청서 전체 상세({@link FairApplicationDetailResponse})와
 * 달리 결제에 필요한 최소 정보만 담는다 - 이 화면을 보는 사람은 신청자 본인이 아니라 승인 시
 * 새로 발급된 담당자(EVENT_ADMIN) 계정이라({@code AdminAccountService#issueEventAdminAccount})
 * managerPhone/managerEmail 같은 PII도 노출하지 않는다.
 *
 * @param status           승인 전(RECEIVED)이거나 반려(REJECTED)·만료(EXPIRED)면 결제할 수 없는
 *                         상태라는 걸 화면이 이 값으로 판단해 적절한 안내를 보여준다.
 * @param openingFeeAmount 승인 시 확정된 개설비 금액(원). 승인 전이면 null.
 * @param paymentDueAt     개설비 결제 기한. 승인 전이면 null.
 */
public record FairOpeningFeeSummaryResponse(
        Long fairId,
        String name,
        String status,
        Long openingFeeAmount,
        LocalDateTime paymentDueAt
) {
}
