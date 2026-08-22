package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;

/**
 * 승인·공개된 행사의 정보성 필드 수정 요청("행사 정보 관리" 화면). {@link UpdateFairApplicationRequest}와
 * 같은 PATCH 계약을 따른다 - 이 레코드만으로는 필드가 null인 것과 요청에서 아예 빠진 것을 구분하지
 * 못하므로, {@link com.ms.petopia.api.fair.controller.FairController#updateFairInfo}가 원본 요청
 * 바디에서 뽑아낸 필드명 집합({@code presentFields})으로 구분한다 - 요청에 없던 필드는 기존 값을
 * 유지하고, 있는데 null이면 그 필드를 명시적으로 지운다(name/managerName은 필수라 이렇게 지우려
 * 하면 거부된다. {@link com.ms.petopia.api.fair.service.FairService#updateFairInfo} 참고).
 *
 * <p>예약금·취소/변경 기한·모집/예약 기간은 이미 진행 중인 예약·모집에 영향을 줄 수 있어 이
 * 화면에서 다루지 않는다. managerEmail도 로그인 계정과 묶여 있어 여기서 바꿀 수 없다.
 */
public record UpdateFairInfoRequest(
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
        LocalDate operationStartDate,
        LocalDate operationEndDate,
        String managerName,
        String managerPhone
) {
}
