package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 행사 신청서 상세 조회 응답. 관리자 검토 화면 등에서 신청 내용을 보여줄 때 쓴다.
 *
 * <p>{@link Fair}를 그대로 노출하지 않고 필요한 필드만 추려 담는다({@code submitted_snapshot} 등
 * 내부 전용 컬럼은 제외).
 *
 * @param canceledAt 취소 승인 일시(취소 아니면 null). 취소 승인({@code FairCancelRequestService#review})은
 *                   fairs.canceled_at만 채우고 status는 그대로 두므로, 취소 여부는 status가 아니라
 *                   이 필드로 판단해야 한다.
 */
public record FairApplicationDetailResponse(
        Long fairId,
        Long applicantUserId,
        String name,
        String description,
        String category,
        String posterImageUrl,
        String noticeText,
        String placeName,
        String address,
        String indoorOutdoor,
        LocalDate vendorRecruitStartDate,
        LocalDate vendorRecruitEndDate,
        LocalDate reservationStartDate,
        LocalDate reservationEndDate,
        LocalDate operationStartDate,
        LocalDate operationEndDate,
        Long reservationFee,
        Integer reservationCancelDeadlineHours,
        Integer reservationChangeDeadlineHours,
        String managerName,
        String managerPhone,
        String managerEmail,
        String status,
        String rejectReason,
        LocalDateTime reviewedAt,
        LocalDateTime paymentDueAt,
        LocalDateTime createdAt,
        LocalDateTime canceledAt
) {
}
