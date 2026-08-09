package com.ms.petopia.api.pet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class PetCreateRequest {

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 50, message = "이름은 최대 50자까지 입력할 수 있습니다.")
    private String name;

    @NotBlank(message = "종을 입력해 주세요. (예: 강아지, 고양이, 햄스터)")
    @Size(max = 20, message = "종은 최대 20자까지 입력할 수 있습니다.")
    private String species;

    @Size(max = 50, message = "품종은 최대 20자까지 입력할 수 있습니다.")
    private String breed;

    @PastOrPresent(message = "생년월일은 오늘 이전이어야 합니다.")
    private LocalDate birthDate;

    @Pattern(regexp = "^(MALE|FEMALE)$", message = "성별은 MALE 또는 FEMALE만 가능합니다.")
    private String gender;

    private Boolean isNeutered;

    @Size(max = 500, message = "이미지 URL은 최대 500자까지 입력할 수 있습니다.")
    private String imageUrl;
}
