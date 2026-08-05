package com.ms.petopia.api.fair.dto;

/**
 * 홀 수정 요청. null인 필드는 변경하지 않는다({@link com.ms.petopia.api.fair.mapper.HallMapper#update} 참고).
 *
 * <p>{@code floorPlanImageObjectKey}가 null이면 도면 이미지를 그대로 둔다 - 기존 이미지를
 * 지우고 싶어도 이 요청만으로는 지울 수 없다(현재 수정 화면에 그 기능이 없음).
 */
public record UpdateHallRequest(
        String name,
        /**
         * {@code POST /api/files/presigned-upload}로 발급받아 S3에 직접 업로드한 임시 객체 키.
         * 서비스가 이 키를 확정(tmp → uploads) 처리해 최종 URL로 교체한다.
         */
        String floorPlanImageObjectKey
) {
}
