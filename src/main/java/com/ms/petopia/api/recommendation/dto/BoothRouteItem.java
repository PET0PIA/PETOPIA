package com.ms.petopia.api.recommendation.dto;

//동선 추천 결과에서 한 홀 안에 있는 부스 하나를 나타낸다
public record BoothRouteItem(
        Long boothId,
        String boothName,
        String reason,
        boolean matched,
        String slotNumber,

        //이 홀 안에서 몇 번째로 방문하는지 (1부터 시작)
        int order

) {
}
