package com.ms.petopia.api.recommendation.dto;

import java.util.List;

//동선 추천 결과 - 홀 하나와, 그 홀 안에서 방문 순서가 정해진 부스 목록
public record HallRoute(
        Long hallId,
        String hallName,
        //배치도 배경 이미지. 그 홀에 업로드 안 돼있으면 null(프론트가 배경 없이 격자만 그림).
        String floorPlanImageUrl,
        List<BoothRouteItem> stops
) {
}
