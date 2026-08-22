package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;

/**
 * 승인·공개된 뒤에도 EVENT_ADMIN이 직접 고칠 수 있는 "정보성" 필드만 담은 조회·수정 응답.
 * 예약금·취소/변경 기한·모집/예약 기간처럼 이미 진행 중인 예약·모집에 영향을 주는 필드나
 * managerEmail(로그인 계정과 묶임)은 여기 포함하지 않는다.
 *
 * @see com.ms.petopia.api.fair.service.FairService#getFairInfo
 * @see com.ms.petopia.api.fair.service.FairService#updateFairInfo
 */
public record FairInfoResponse(
        Long fairId,
        String name,
        String description,
        String category,
        String posterImageUrl,
        String noticeText,
        String placeName,
        String address,
        String indoorOutdoor,
        LocalDate operationStartDate,
        LocalDate operationEndDate,
        String managerName,
        String managerPhone
) {
}
