package com.ms.petopia.api.user.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

//마이페이지 수정
@Getter
@Setter
@NoArgsConstructor
public class UserUpdateRequest {

    @Size(min = 1, max = 50, message = "닉네임은 1~50자여야 합니다.")
    private String nickname;

    @Past(message = "생년월일은 오늘 이전이어야 합니다.")
    private LocalDate birthDate;

    @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다. (예: 01012345678 또는 010-1234-5678)")
    private String phone;

    @Pattern(regexp = "^(남성|여성)$", message = "성별은 남성 또는 여성만 가능합니다.")
    private String gender;

    @Size(min = 1, max = 300, message = "주소를 입력해 주세요.")
    private String address;
}
