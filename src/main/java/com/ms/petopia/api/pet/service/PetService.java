package com.ms.petopia.api.pet.service;

import com.ms.petopia.api.pet.domain.Pet;
import com.ms.petopia.api.pet.dto.PetCreateRequest;
import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.pet.dto.PetUpdateRequest;
import com.ms.petopia.api.pet.mapper.PetMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetMapper petMapper;

    //반려동물 목록 확인
    public List<PetResponse> getMyPets(Long userId) {
        return petMapper.selectPetsByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    //반려동물 상세 조회
    public PetResponse getPet(Long userId, Long petId) {
        return toResponse(getOwnedPet(userId, petId));
    }

    //동물 등록
    @Transactional
    public PetResponse createPet(Long userId, PetCreateRequest request) {
        Pet pet = Pet.builder()
                .userId(userId)
                .name(request.getName())
                .species(request.getSpecies())
                .breed(request.getBreed())
                .birthDate(request.getBirthDate())
                .gender(request.getGender())
                .isNeutered(request.getIsNeutered())
                .imageUrl(request.getImageUrl())
                .build();
        petMapper.insertPet(pet);
        //insert 후의 pet 객체엔 created_at이 없다(DB DEFAULT CURRENT_TIMESTAMP를 애플리케이션이
        //모름) - petId는 useGeneratedKeys로 채워지니 그걸로 다시 조회해서 정확한 값을 응답한다.
        return toResponse(petMapper.selectPetById(pet.getPetId()));
    }

    //동물 부분 수정. null이 아닌 것만 반영
    @Transactional
    public PetResponse updatePet(Long userId, Long petId, PetUpdateRequest request) {
        getOwnedPet(userId, petId); //존재 + 소유권 확인

        boolean hasAnyField = request.getName() != null
                || request.getSpecies() != null
                || request.getBreed() != null
                || request.getBirthDate() != null
                || request.getGender() != null
                || request.getIsNeutered() != null
                || request.getImageUrl() != null;

        if (hasAnyField) {
            petMapper.updatePet(
                    petId,
                    request.getName(),
                    request.getSpecies(),
                    request.getBreed(),
                    request.getBirthDate(),
                    request.getGender(),
                    request.getIsNeutered(),
                    request.getImageUrl()
            );
        }

        return toResponse(petMapper.selectPetById(petId));
    }

    @Transactional
    public void deletePet(Long userId, Long petId) {
        getOwnedPet(userId, petId); //존재 + 소유권 확인
        petMapper.deletePetById(petId);
    }

    //존재 여부와 소유권을 함께 확인. 없으면 404, 남의 것이면 403
    private Pet getOwnedPet(Long userId, Long petId) {
        Pet pet = petMapper.selectPetById(petId);
        if (pet == null) {
            throw new CommonException(ErrorCode.PET_NOT_FOUND);
        }
        if (!pet.getUserId().equals(userId)) {
            throw new CommonException(ErrorCode.PET_ACCESS_DENIED);
        }
        return pet;
    }

    private PetResponse toResponse(Pet pet) {
        return new PetResponse(
                pet.getPetId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getBirthDate(),
                pet.getGender(),
                pet.getIsNeutered(),
                pet.getImageUrl(),
                pet.getCreatedAt()
        );
    }
}
