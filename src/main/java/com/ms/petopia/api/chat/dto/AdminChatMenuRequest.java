package com.ms.petopia.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 문의 유형 버튼 등록·수정.
 *
 * @param code       불변 식별자. 대화가 이 값을 참조하므로 수정 시에는 무시된다.
 * @param aiContext  AI 유형일 때 프롬프트에 주입할 도메인 지식. 이 값의 품질이 곧 답변 품질이다.
 */
public record AdminChatMenuRequest(
        @NotBlank(message = "코드를 입력해주세요.")
        @Size(max = 40)
        // 대문자·숫자·밑줄만 허용한다. 코드가 URL이나 로그에 그대로 노출되므로 공백·한글이
        // 섞이면 다루기 번거롭고, 오타를 눈으로 잡기도 어려워진다.
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "코드는 영문 대문자·숫자·밑줄만 사용할 수 있어요.")
        String code,

        @NotBlank(message = "버튼 문구를 입력해주세요.")
        @Size(max = 100)
        String label,

        @NotNull(message = "답변 유형을 선택해주세요.")
        ChatAnswerType answerType,

        @Size(max = 5000)
        String fixedAnswer,

        @Size(max = 5000)
        String aiContext,

        Integer displayOrder,

        Boolean isActive
) {
}
