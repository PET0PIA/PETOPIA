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
}
