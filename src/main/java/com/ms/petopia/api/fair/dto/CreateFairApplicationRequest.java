package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;

/**
 * 행사 신청 등록 요청.
 *
 * <p>{@code applicant_user_id}는 요청 본문이 아니라 (임시로) X-User-Id 헤더에서 받는다.
 * {@code status}는 신청 시점에 항상 RECEIVED로 시작하므로 요청에 포함하지 않는다.
 */
public record CreateFairApplicationRequest(
        String name,
        String description,
        /** DOG / CAT / ETC */
        String category,
        /**
         * {@code POST /api/files/presigned-upload}로 발급받아 S3에 직접 업로드한 임시 객체 키.
         * 서비스가 이 키를 확정(tmp → uploads) 처리해 최종 URL을 만들어 저장한다.
         * 이미지가 없으면 null.
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

        /** 관람객 예약금(원). 미입력 시 DB 기본값 0(무료) */
        Long reservationFee,
        Integer reservationCancelDeadlineHours,
        Integer reservationChangeDeadlineHours,

        String managerName,
        String managerPhone,
        String managerEmail
) {
}
