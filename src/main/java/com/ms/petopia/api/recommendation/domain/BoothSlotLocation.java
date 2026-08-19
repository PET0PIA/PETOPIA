package com.ms.petopia.api.recommendation.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

//부스의 슬롯 위치 정보를 담은 domain

@Getter
@Setter
public class BoothSlotLocation {

    private Long boothId;
    private Long hallId;
    private String hallName;
    private String slotNumber;
    private BigDecimal posX;
    private BigDecimal posY;
    private BigDecimal width;
    private BigDecimal height;
    //동선 추천 화면에서 배치도 배경으로 쓴다. 업로드 안 한 행사면 null(프론트가 배경 없이 격자만 그림).
    private String floorPlanImageUrl;
}
