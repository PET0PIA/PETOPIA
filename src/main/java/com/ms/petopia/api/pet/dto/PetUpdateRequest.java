package com.ms.petopia.api.pet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

//부분 수정. 필드를 안 보내면 그 값은 그대로 둔다.
@Getter
@Setter
@NoArgsConstructor
public class PetUpdateRequest {

    @Size(min = 1, max = 50, message = "이름은 1~50자여야 합니다.")
    @Pattern(regexp = ".*\\S.*", message = "이름은 공백만으로 입력할 수 없습니다.")
    private String name;

    @Size(min = 1, max = 20, message = "종은 1~20자여야 합니다.")
    @Pattern(regexp = ".*\\S.*", message = "종은 공백만으로 입력할 수 없습니다.")
    private String species;

    @Size(max = 50, message = "품종은 최대 50자까지 입력할 수 있습니다.")
    private String breed;

    @Past(message = "생년월일은 오늘 이전이어야 합니다.")
    private LocalDate birthDate;

    @Pattern(regexp = "^(MALE|FEMALE)$", message = "성별은 MALE 또는 FEMALE만 가능합니다.")
    private String gender;

    private Boolean isNeutered;

    /**
     * 알레르기 여부 3값. 다른 필드와 같은 부분 수정 규칙을 따른다 - 안 보내면(null)
     * 기존 여부와 기존 목록을 모두 그대로 둔다.
     * false를 보내면 여부를 "없음"으로 바꾸고 기존 목록을 전부 지운다.
     */
    private Boolean hasAllergy;

    /**
     * 알레르기 항목 교체 목록. hasAllergy=true와 함께 보낼 때만 의미가 있다
     * (여부 없이 목록만 보내면 400 - 어느 쪽이 사용자의 의도인지 서버가 정할 수 없다).
     * hasAllergy=true인데 이 필드를 안 보내면 기존 목록을 그대로 둔다.
     */
    @Valid
    private List<PetAllergySelectionRequest> allergies;

    /** presigned-upload로 받은 임시 객체 키. null이면(새로 첨부 안 함) 기존 이미지를 그대로 둔다. */
    private String imageObjectKey;
}
