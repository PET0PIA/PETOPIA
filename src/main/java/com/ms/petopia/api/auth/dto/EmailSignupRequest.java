package com.ms.petopia.api.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
