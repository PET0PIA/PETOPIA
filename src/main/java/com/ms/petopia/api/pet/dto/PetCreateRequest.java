package com.ms.petopia.api.pet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

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

    @Size(max = 50, message = "품종은 최대 50자까지 입력할 수 있습니다.")
    private String breed;

    @Past(message = "생년월일은 오늘 이전이어야 합니다.")
    private LocalDate birthDate;

    @Pattern(regexp = "^(MALE|FEMALE)$", message = "성별은 MALE 또는 FEMALE만 가능합니다.")
    private String gender;

    private Boolean isNeutered;

    /**
     * 알레르기 여부 3값. 안 보내면(null) "아직 안 물어봄"으로 남는다.
     * false면 목록을 비워야 하고, true여야 allergies를 함께 보낼 수 있다.
     */
    private Boolean hasAllergy;

    /** hasAllergy=true일 때 고른 알레르기 항목들. 같은 항목을 두 번 보내면 서버가 하나로 합친다. */
    @Valid
    private List<PetAllergySelectionRequest> allergies;

    /** presigned-upload로 받은 임시 객체 키. 선택 입력이며, 서버가 tmp -> uploads로 확정해 imageUrl로 저장한다. */
    private String imageObjectKey;
}
