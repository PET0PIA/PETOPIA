package com.ms.petopia.api.auth.dto;

import java.time.LocalDate;

//관리자 목록 조회(페어와 관리자 목록) - 화면에 보여줄 데이터
public record AdminAccountListItemResponse(
        Long userId,
        String email,
        String nickname,
        String status,
        Long fairId,
        String fairName,
        LocalDate operationStartDate,
        LocalDate operationEndDate
) {
}
