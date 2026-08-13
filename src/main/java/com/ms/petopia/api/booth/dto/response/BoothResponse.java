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

    private boolean favorited; // 로그인 안 했거나 즐겨찾기 안 했으면 false
    private boolean owner; // 로그인한 사용자가 이 부스의 소유자인지 (관리 버튼 노출용, 비로그인이면 false)

    public static BoothResponse from(Booth booth, List<BoothItemResponse> items, boolean favorited, boolean owner) {

        return BoothResponse.builder()
                .boothId(booth.getBoothId())
                .name(booth.getName())
                .intro(booth.getIntro())
                .category(booth.getCategory())
                .targetAnimal(booth.getTargetAnimal() != null ? booth.getTargetAnimal().name() : null)
                .imageUrl(booth.getImageUrl())
                .items(items)
                .favorited(favorited)
                .owner(owner)
                .build();

    }

    // 프로필 수정 응답 등 즐겨찾기 여부가 의미 없는 곳에서 쓰는 오버로드(false 고정)
    public static BoothResponse from(Booth booth, List<BoothItemResponse> items) {
        return from(booth, items, false, false);
    }

}
