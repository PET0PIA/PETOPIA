package com.ms.petopia.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 문의 유형 버튼을 눌러 대화를 시작한다.
 *
 * <p>menuId가 아니라 code를 받는다. 메뉴는 운영자가 지웠다 다시 만들 수 있는 데이터라
 * 자동 증가 ID가 안정적인 식별자가 아니고, 프론트가 특정 유형에 다른 UI를 붙일 때도
 * 의미를 읽을 수 있는 쪽이 code다.
 */
public record StartConversationRequest(
        @NotBlank(message = "문의 유형을 선택해주세요.")
        @Size(max = 40)
        String menuCode
) {
}
