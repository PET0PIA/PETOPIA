package com.ms.petopia.api.recommendation.dto;

import java.math.BigDecimal;

//동선 추천 결과에서 한 홀 안에 있는 부스 하나를 나타낸다
public record BoothRouteItem(
        Long boothId,
        String boothName,
        String reason,
        boolean matched,
        String slotNumber,

        //이 홀 안에서 몇 번째로 방문하는지 (1부터 시작)
        int order,

        //프론트가 지도 위에 마커를 그릴 때 쓰는 상대 좌표·크기(0~1). BoothSlotResponse와 동일 단위.
        BigDecimal posX,
        BigDecimal posY,
        BigDecimal width,
        BigDecimal height

) {
}
