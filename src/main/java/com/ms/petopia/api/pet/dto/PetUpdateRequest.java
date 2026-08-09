package com.ms.petopia.api.pet.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

//부분 수정. 필드를 안 보내면 그 값은 그대로 둔다.
@Getter
@Setter
@NoArgsConstructor
public class PetUpdateRequest {

    @Size(min = 1, max = 50, message = "이름은 1~50자여야 합니다.")
    private String name;

    @Size(min = 1, max = 20, message = "종은 1~20자여야 합니다.")
    private String species;

    @Size(max = 50, message = "품종은 최대 50자까지 입력할 수 있습니다.")
    private String breed;

    @PastOrPresent(message = "생년월일은 오늘 이전이어야 합니다.")
    private LocalDate birthDate;

    @Pattern(regexp = "^(MALE|FEMALE)$", message = "성별은 MALE 또는 FEMALE만 가능합니다.")
    private String gender;

    private Boolean isNeutered;

    @Size(max = 500, message = "이미지 URL은 최대 500자까지 입력할 수 있습니다.")
    private String imageUrl;
}
