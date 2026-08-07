package com.ms.petopia.api.booth.dto.response;

import com.ms.petopia.api.booth.domain.BoothItem;
import lombok.Builder;
import lombok.Getter;

// 판매상품·이벤트 응답 (부스 상세에 포함되거나, 등록/수정 응답으로 단독 사용)
@Getter
@Builder
public class BoothItemResponse {

    private Long boothItemId;
    private Long boothId;
    private String name;
    private String type;
    private String imageUrl;
    private String note;

    public static BoothItemResponse from(BoothItem item) {

        return BoothItemResponse.builder()
                .boothItemId(item.getBoothItemId())
                .boothId(item.getBoothId())
                .name(item.getName())
                .type(item.getType() != null ? item.getType().name() : null)
                .imageUrl(item.getImageUrl())
                .note(item.getNote())
                .build();

    }

}
