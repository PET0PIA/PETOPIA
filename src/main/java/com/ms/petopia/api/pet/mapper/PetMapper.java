package com.ms.petopia.api.pet.mapper;

import com.ms.petopia.api.pet.domain.Pet;
import com.ms.petopia.api.pet.dto.PetAllergyRow;
import com.ms.petopia.api.pet.dto.PetAllergySelectionRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PetMapper {

    int insertPet(Pet pet);

    List<Pet> selectPetsByUserId(@Param("userId") Long userId);

    Pet selectPetById(@Param("petId") Long petId);

    //부분 수정
    int updatePet(
            @Param("petId") Long petId,
            @Param("name") String name,
            @Param("species") String species,
            @Param("breed") String breed,
            @Param("birthDate") LocalDate birthDate,
            @Param("gender") String gender,
            @Param("isNeutered") Boolean isNeutered,
            @Param("hasAllergy") Boolean hasAllergy,
            @Param("imageUrl") String imageUrl
    );

    int deletePetById(@Param("petId") Long petId);

    // ===== 알레르기 선택(pet_allergies) =====

    /**
     * 여러 반려동물의 알레르기 선택을 한 번에 가져온다(N+1 방지). 서비스가 petId로 그룹핑한다.
     * petIds가 비어 있으면 호출하지 않는다(서비스 계층에서 가드).
     *
     * <p>마스터가 비활성(is_active=0)이 된 항목도 함께 내려준다 - 이미 등록해 둔 정보를
     * 나중에 항목이 정리됐다는 이유로 화면에서 사라지게 하면 사용자가 자기 입력을 잃는다.
     */
    List<PetAllergyRow> selectAllergiesByPetIds(@Param("petIds") List<Long> petIds);

    /** 선택 목록을 한 번에 저장한다(다중행 INSERT). selections가 비어있으면 호출하지 않는다. */
    void insertAllergies(@Param("petId") Long petId, @Param("selections") List<PetAllergySelectionRequest> selections);

    /** 목록 교체·반려동물 삭제 시 기존 선택을 먼저 지운다. 없어도 오류가 아니다(0건 삭제). */
    int deleteAllergiesByPetId(@Param("petId") Long petId);
}
