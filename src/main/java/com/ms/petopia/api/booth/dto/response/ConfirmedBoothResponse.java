package com.ms.petopia.api.booth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 확정 부스 안내판 조회 응답 (GET /api/fairs/{fairId}/confirmed-booths) - 슬롯 하나당 한 행
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmedBoothResponse {

    private Long boothId;
    private String businessName;
    private String slotNumber; // 슬롯 번호
    private BigDecimal posX; // 슬롯 좌표
    private BigDecimal posY; // 슬롯 좌표

}
