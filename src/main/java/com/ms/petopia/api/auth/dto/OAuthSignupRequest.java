package com.ms.petopia.api.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

//OAuth 가입 관문 - /callback에서 받은 tempKey + 추가 프로필 정보를 같이 제출받는 요청
@Getter
@Setter
@NoArgsConstructor
public class OAuthSignupRequest {

    @NotBlank
    private String tempKey;     //email, provider, oauthId가 들어 있음

    @NotBlank
    @Size(max = 30, message = "실명을 입력해 주세요")
    private String nickname;

    @NotNull
    @Past
    private LocalDate birthDate;

    @NotBlank
    @Pattern(
            regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$",
            message = "휴대폰 번호 형식이 올바르지 않습니다. (예: 01012345678 또는 010-1234-5678)"
    )
    private String phone;

    @NotBlank
    @Pattern(regexp = "^(남성|여성)$", message = "성별은 남성 또는 여성만 가능합니다.")
    private String gender;

    @NotBlank
    @Size(max = 100, message = "주소를 입력해 주세요 ex) 서울특별시 노원구")
    private String address;

    @NotNull
    @AssertTrue
    private Boolean agreedTerms;

    @NotNull
    @AssertTrue
    private Boolean agreedPrivacy;
}
