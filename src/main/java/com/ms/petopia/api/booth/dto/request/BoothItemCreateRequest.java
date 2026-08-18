package com.ms.petopia.api.booth.dto.request;

import com.ms.petopia.api.booth.domain.BoothItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 판매상품·이벤트 등록 요청 (POST /api/booths/{boothId}/items)
@Getter
@Setter
public class BoothItemCreateRequest {

    @NotBlank(message = "상품·이벤트명은 필수입니다.")
    @Size(max = 100, message = "상품·이벤트명은 100자 이하여야 합니다.")
    private String name;

    @NotNull(message = "타입은 필수입니다.")
    private BoothItem.Type type; // PRODUCT / EVENT / SAMPLE

    @Size(max = 255, message = "비고는 255자 이하여야 합니다.")
    private String note;

    @Size(max = 500, message = "이미지 키는 500자 이하여야 합니다.")
    private String imageObjectKey;

}
