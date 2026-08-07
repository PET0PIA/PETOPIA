package com.ms.petopia.api.booth.dto.request;

import com.ms.petopia.api.booth.domain.Booth;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/*
 * 부스 프로필 수정 요청 (PUT /api/booths/{boothId})
 * 전부 선택 입력 - null이 아닌 필드만 갱신(부분 업데이트)
 */
@Getter
@Setter
public class BoothUpsertRequest {

    @Size(max = 100, message = "부스명은 100자 이하여야 합니다.")
    private String name;

    @Size(max = 1000, message = "소개글은 1000자 이하여야 합니다.")
    private String intro;

    @Size(max = 50, message = "카테고리는 50자 이하여야 합니다.")
    private String category;

    private Booth.TargetAnimal targetAnimal; // DOG / CAT / ETC

    @Size(max = 500, message = "이미지 키는 500자 이하여야 합니다.")
    private String imageObjectKey; // presigned-upload로 받은 임시 객체 키, 선택 입력

}
