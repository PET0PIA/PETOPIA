package com.ms.petopia.api.booth.dto.response;

import lombok.*;

// GET /api/booths/favorites 응답 (목록 항목 하나)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothFavoriteResponse {

    private Long boothId;
    private String name; // 부스명
    private String imageUrl; // 부스 대표 이미지
    private String category; // 취급 품목 카테고리 (사료/간식, 미용, 훈련, 굿즈 등)
    private String targetAnimal; // 주 대상 동물 (DOG/CAT/ETC)
    private Long fairId; // 이 부스가 속한 행사 ID
    private String fairName; // 그 행사 이름

}
