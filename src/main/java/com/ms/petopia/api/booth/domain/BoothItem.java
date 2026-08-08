package com.ms.petopia.api.booth.domain;

import lombok.*;

// 부스에서 파는 판매상품·이벤트. booth 1건에 여러 건 소속
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothItem {

    private Long boothItemId;
    private Long boothId; // booth.booth_id
    private String name;
    private Type type; // PRODUCT(판매상품) / EVENT(이벤트) / SAMPLE(체험/샘플)
    private String imageUrl;
    private String note; // 비고 (예: "선착순 100개")

    public enum Type {
        PRODUCT, EVENT, SAMPLE
    }

}
