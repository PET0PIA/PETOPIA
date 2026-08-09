package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;

/**
 * 행사 신청서 수정(재제출) 요청. RECEIVED(심사 대기) 또는 REJECTED(반려) 상태의
 * 신청서만 수정할 수 있고, REJECTED 상태였다면 이 요청이 성공하는 순간 RECEIVED로
 * 되돌아가 다시 심사 대기열에 선다({@code FairService#updateApplication} 참고).
 *
 * <p>이 레코드 자체는 필드가 null인 것과 요청에서 아예 빠진 것을 구분하지 못한다(Jackson이
 * 둘 다 null로 채운다). 그 구분은 {@link com.ms.petopia.api.fair.controller.FairController#updateApplication}이
 * 원본 요청 바디에서 뽑아낸 필드명 집합({@code presentFields})으로 한다 - 요청에 없던 필드는
 * 기존 값을 유지하고, 있는데 null이면 그 필드를 명시적으로 지운다(name/managerName/managerEmail은
 * 필수라 이렇게 지우려 하면 거부된다. {@link FairService#updateApplication} 참고).
 */
public record UpdateFairApplicationRequest(
        String name,
        String description,
        /** DOG / CAT / ETC */
        String category,
        /**
         * {@code POST /api/files/presigned-upload}로 발급받아 S3에 직접 업로드한 임시 객체 키.
         * 포스터를 바꾸지 않으려면 이 필드 자체를 요청에서 빼야 한다(기존 값 유지). 명시적으로
         * null을 보내면 기존 포스터를 삭제한다.
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
