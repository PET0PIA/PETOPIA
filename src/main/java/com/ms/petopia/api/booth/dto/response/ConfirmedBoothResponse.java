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
    private String imageUrl; // 부스 대표 이미지
    private String category; // 취급 품목 카테고리 (사료/간식, 미용, 훈련, 굿즈 등)
    private String targetAnimal; // 주 대상 동물 (DOG/CAT/ETC)
    private String slotNumber; // 슬롯 번호
    private BigDecimal posX; // 슬롯 좌표
    private BigDecimal posY; // 슬롯 좌표
    private BigDecimal width; // 슬롯 크기
    private BigDecimal height; // 슬롯 크기
    private Long hallId;
    private String hallName; // 홀 이름
    private String floorPlanImageUrl; // 홀 도면

}
