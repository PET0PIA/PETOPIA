package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;

/**
 * 행사 신청서 수정(재제출) 요청. RECEIVED(심사 대기) 또는 REJECTED(반려) 상태의
 * 신청서만 수정할 수 있고, REJECTED 상태였다면 이 요청이 성공하는 순간 RECEIVED로
 * 되돌아가 다시 심사 대기열에 선다({@code FairService#updateApplication} 참고).
 *
 * <p>PATCH 의미론을 따른다 - 필드를 비워 보내면(null) 기존 값을 그대로 둔다. 값을
 * 지우고 싶다는 의도(예: 공고문 삭제)는 이 API로 표현할 수 없다({@code CreateFairApplicationRequest}와
 * 동일한 제약).
 */
public record UpdateFairApplicationRequest(
        String name,
        String description,
        /** DOG / CAT / ETC */
        String category,
        /**
         * {@code POST /api/files/presigned-upload}로 발급받아 S3에 직접 업로드한 임시 객체 키.
         * 포스터를 바꾸지 않으면 null로 보낸다(기존 값 유지).
         */
        String posterImageObjectKey,
        String noticeText,

        String placeName,
        String address,
        /** INDOOR / OUTDOOR */
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
        String managerEmail
) {
}
