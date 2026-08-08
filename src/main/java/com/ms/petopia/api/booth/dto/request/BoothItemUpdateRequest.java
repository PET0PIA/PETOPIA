package com.ms.petopia.api.booth.dto.request;

import com.ms.petopia.api.booth.domain.BoothItem;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/*
 * 판매상품·이벤트 수정 요청 (PUT /api/booth-items/{boothItemId})
 * 전부 선택 입력 - null이 아닌 필드만 갱신(부분 업데이트)
 */
@Getter
@Setter
public class BoothItemUpdateRequest {

    @Size(min = 1, max = 100, message = "상품·이벤트명은 100자 이하여야 합니다.")
    private String name;

    private BoothItem.Type type; // PRODUCT / EVENT / SAMPLE

    @Size(max = 255, message = "비고는 255자 이하여야 합니다.")
    private String note;

    @Size(max = 500, message = "이미지 키는 500자 이하여야 합니다.")
    private String imageObjectKey;

}
