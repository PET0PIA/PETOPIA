package com.ms.petopia.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

//콜백에서 받은 로그인 핸드오프 코드로 실제 토큰을 교환할 때 쓰는 요청
public record OAuthExchangeRequest(
        @NotBlank String code
) {
}
