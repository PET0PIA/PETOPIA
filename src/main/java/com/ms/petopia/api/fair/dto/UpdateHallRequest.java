package com.ms.petopia.api.fair.dto;

/**
 * 홀 수정 요청. null인 필드는 변경하지 않는다({@link com.ms.petopia.api.fair.mapper.HallMapper#update} 참고).
 */
public record UpdateHallRequest(
        String name,
        String floorPlanImageUrl
) {
}
