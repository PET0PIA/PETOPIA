package com.ms.petopia.api.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class EmailSignupRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$",
            message = "비밀번호는 영문, 숫자, 특수문자(!@#$%^&*)를 포함한 8자 이상이어야 합니다."
    )
    private String password;

    @NotBlank
    private String passwordConfirm;

    @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
    public boolean isPasswordConfirmed() {
        if (password == null) return true;
        return password.equals(passwordConfirm);
    }

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
