package com.ms.petopia.api.pet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 등록·수정 요청에 담기는 알레르기 선택 1건.
 *
 * <p>{@code otherText}는 마스터의 {@code requires_text=1}인 항목(기타)에만 쓴다.
 * 그 외 항목에 값이 와도 서버가 null로 버린다 - 쓰이지 않는 자리에 문자열이 쌓이면
 * 나중에 통계에서 이 값을 믿을 수 없게 된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PetAllergySelectionRequest {

    @NotNull(message = "알레르기 항목을 선택해 주세요.")
    private Long allergyTypeId;

    @Size(max = 100, message = "직접 입력은 최대 100자까지 가능합니다.")
    private String otherText;
}
