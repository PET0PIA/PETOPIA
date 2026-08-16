package com.ms.petopia.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자가 보내는 자유 질문.
 *
 * <p>길이 상한은 DB의 TEXT 한계가 아니라 프롬프트 비용과 상담사 가독성 때문이다.
 * 2000자를 넘길 만한 내용이면 대화로 나눠 받는 편이 서로 낫다.
 */
public record SendChatMessageRequest(
        @NotBlank(message = "메시지를 입력해주세요.")
        @Size(max = 2000, message = "메시지는 2000자까지 보낼 수 있어요.")
        String content
) {
}
