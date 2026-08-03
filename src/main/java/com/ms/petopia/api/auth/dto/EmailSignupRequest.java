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
    private String nickname;

    @NotNull
    private LocalDate birthDate;

    @NotBlank
    private String phone;

    @NotBlank
    private String gender;

    @NotBlank
    private String address;

    @NotNull
    @AssertTrue
    private Boolean agreedTerms;

    @NotNull
    @AssertTrue
    private Boolean agreedPrivacy;
}
