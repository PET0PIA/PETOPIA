package com.ms.petopia.api.recommendation.dto;

import java.util.List;

//동선 추천 결과 - 홀 하나와, 그 홀 안에서 방문 순서가 정해진 부스 목록
public record HallRoute(
        Long hallId,
        String hallName,
        List<BoothRouteItem> stops
) {
}
