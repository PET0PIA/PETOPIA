package com.ms.petopia.api.booth.dto.response;

import com.ms.petopia.api.booth.domain.Booth;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// 부스 상세 조회 응답 (GET /api/booths/{boothId})
@Getter
@Builder
public class BoothResponse {

    private Long boothId;
    private String name;
    private String intro;
    private String category;
    private String targetAnimal;
    private String imageUrl;
    private List<BoothItemResponse> items;

    public static BoothResponse from(Booth booth, List<BoothItemResponse> items) {

        return BoothResponse.builder()
                .boothId(booth.getBoothId())
                .name(booth.getName())
                .intro(booth.getIntro())
                .category(booth.getCategory())
                .targetAnimal(booth.getTargetAnimal() != null ? booth.getTargetAnimal().name() : null)
                .imageUrl(booth.getImageUrl())
                .items(items)
                .build();

    }

}
