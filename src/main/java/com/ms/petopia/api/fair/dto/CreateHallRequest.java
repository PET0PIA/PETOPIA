package com.ms.petopia.api.fair.dto;

/**
 * 홀 등록 요청. fairId는 요청 본문이 아니라 경로에서 받는다.
 */
public record CreateHallRequest(
        String name,
        /**
         * {@code POST /api/files/presigned-upload}로 발급받아 S3에 직접 업로드한 임시 객체 키.
         * 서비스가 이 키를 확정(tmp → uploads) 처리해 최종 URL을 만들어 저장한다.
         * 이미지가 없으면 null.
         */
        String floorPlanImageObjectKey
) {
}
