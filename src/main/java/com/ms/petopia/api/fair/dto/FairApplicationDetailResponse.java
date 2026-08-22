package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 행사 신청서 상세 조회 응답. 관리자 검토 화면 등에서 신청 내용을 보여줄 때 쓴다.
 *
 * <p>{@link Fair}를 그대로 노출하지 않고 필요한 필드만 추려 담는다({@code submitted_snapshot} 등
 * 내부 전용 컬럼은 제외).
 *
 * @param canceledAt  취소 승인 일시(취소 아니면 null). 취소 승인({@code FairCancelRequestService#review})은
 *                    fairs.canceled_at만 채우고 status는 그대로 두므로, 취소 여부는 status가 아니라
 *                    이 필드로 판단해야 한다.
 * @param publishedAt 공개(예약 오픈) 일시(미공개면 null). {@link com.ms.petopia.api.fair.service.FairService#publish}로만
 *                    채워지고 status와는 독립적이다 - 관리자 검토 화면이 "공개하기" 버튼을 보여줄지
 *                    판단하는 데 쓴다.
 * @param openingFeeAmount 승인 시 확정된 개설비 금액(원). 승인 전이거나 반려면 null.
 */
public record FairApplicationDetailResponse(
        Long fairId,
        Long applicantUserId,
        String name,
        String description,
        String category,
        String posterImageUrl,
        String noticeText,
        /** 반려동물 동반 가능 여부. */
        boolean petAllowed,
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
        Long openingFeeAmount,
        LocalDateTime paymentDueAt,
        LocalDateTime createdAt,
        LocalDateTime canceledAt,
        LocalDateTime publishedAt
) {
}
