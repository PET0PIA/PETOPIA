package com.ms.petopia.api.pet.service;

import com.ms.petopia.api.pet.domain.Pet;
import com.ms.petopia.api.pet.domain.PetAllergyType;
import com.ms.petopia.api.pet.dto.PetAllergyResponse;
import com.ms.petopia.api.pet.dto.PetAllergyRow;
import com.ms.petopia.api.pet.dto.PetAllergySelectionRequest;
import com.ms.petopia.api.pet.dto.PetAllergyTypeResponse;
import com.ms.petopia.api.pet.dto.PetCreateRequest;
import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.pet.dto.PetUpdateRequest;
import com.ms.petopia.api.pet.mapper.PetAllergyTypeMapper;
import com.ms.petopia.api.pet.mapper.PetMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetMapper petMapper;
    private final PetAllergyTypeMapper petAllergyTypeMapper;
    private final StorageService storageService;

    //알레르기 선택지 목록. 활성 항목만 카테고리·정렬순으로 내려준다(공개 조회)
    public List<PetAllergyTypeResponse> getAllergyTypes() {
        return petAllergyTypeMapper.selectActive().stream()
                .map(PetAllergyTypeResponse::from)
                .toList();
    }

    //반려동물 목록 확인
    public List<PetResponse> getMyPets(Long userId) {
        List<Pet> pets = petMapper.selectPetsByUserId(userId);
        //반려동물마다 알레르기를 따로 조회하면 N+1이 된다 - petId를 모아 한 번에 가져와 붙인다
        Map<Long, List<PetAllergyResponse>> allergiesByPetId = loadAllergies(
                pets.stream().map(Pet::getPetId).toList());
        return pets.stream()
                .map(pet -> toResponse(pet, allergiesByPetId.getOrDefault(pet.getPetId(), List.of())))
                .toList();
    }

    //반려동물 상세 조회
    public PetResponse getPet(Long userId, Long petId) {
        return toResponseWithAllergies(getOwnedPet(userId, petId));
    }

    //동물 등록
    @Transactional
    public PetResponse createPet(Long userId, PetCreateRequest request) {
        //알레르기 여부·목록을 먼저 검증한다 - pets INSERT 뒤에 거절하면 이미지 확정(tmp -> uploads)만
        //해두고 롤백되는 낭비가 생긴다
        List<PetAllergySelectionRequest> selections =
                resolveSelections(request.getHasAllergy(), request.getAllergies());

        Pet pet = Pet.builder()
                .userId(userId)
                .name(request.getName())
                .species(request.getSpecies())
                .breed(request.getBreed())
                .birthDate(request.getBirthDate())
                .gender(request.getGender())
                .isNeutered(request.getIsNeutered())
                .hasAllergy(request.getHasAllergy())
                .imageUrl(resolveImageUrl(request.getImageObjectKey()))
                .build();
        petMapper.insertPet(pet);
        if (!selections.isEmpty()) {
            petMapper.insertAllergies(pet.getPetId(), selections);
        }
        //insert 후의 pet 객체엔 created_at이 없다(DB DEFAULT CURRENT_TIMESTAMP를 애플리케이션이
        //모름) - petId는 useGeneratedKeys로 채워지니 그걸로 다시 조회해서 정확한 값을 응답한다.
        return toResponseWithAllergies(petMapper.selectPetById(pet.getPetId()));
    }

    //동물 부분 수정. null이 아닌 것만 반영
    @Transactional
    public PetResponse updatePet(Long userId, Long petId, PetUpdateRequest request) {
        getOwnedPet(userId, petId); //존재 + 소유권 확인

        Boolean hasAllergy = request.getHasAllergy();
        List<PetAllergySelectionRequest> selections = resolveSelections(hasAllergy, request.getAllergies());

        boolean hasAnyField = request.getName() != null
                || request.getSpecies() != null
                || request.getBreed() != null
                || request.getBirthDate() != null
                || request.getGender() != null
                || request.getIsNeutered() != null
                || hasAllergy != null
                || (request.getImageObjectKey() != null && !request.getImageObjectKey().isBlank());

        if (hasAnyField) {
            petMapper.updatePet(
                    petId,
                    request.getName(),
                    request.getSpecies(),
                    request.getBreed(),
                    request.getBirthDate(),
                    request.getGender(),
                    request.getIsNeutered(),
                    hasAllergy,
                    resolveImageUrl(request.getImageObjectKey())
            );
        }

        /*
         * 알레르기 목록 갱신 규칙. 다른 필드의 부분 수정 규칙("안 보내면 그대로 둔다")을 따른다.
         *   hasAllergy 미전송        -> 여부·목록 둘 다 손대지 않는다
         *   hasAllergy = false       -> 여부를 '없음'으로 바꾸고 기존 목록을 전부 지운다
         *   hasAllergy = true  + 목록 -> 전체 삭제 후 재삽입(교체). 부분 갱신은 지원하지 않는다 -
         *                               "지금 화면에 보이는 목록이 곧 저장될 목록"이 사용자에게
         *                               가장 예측 가능하고, 항목별 추가/삭제 API보다 단순하다
         *   hasAllergy = true, 목록 미전송 -> 여부만 바꾸고 기존 목록은 그대로 둔다
         */
        if (Boolean.FALSE.equals(hasAllergy)) {
            petMapper.deleteAllergiesByPetId(petId);
        } else if (Boolean.TRUE.equals(hasAllergy) && request.getAllergies() != null) {
            petMapper.deleteAllergiesByPetId(petId);
            if (!selections.isEmpty()) {
                petMapper.insertAllergies(petId, selections);
            }
        }

        return toResponseWithAllergies(petMapper.selectPetById(petId));
    }

    @Transactional
    public void deletePet(Long userId, Long petId) {
        getOwnedPet(userId, petId); //존재 + 소유권 확인
        //pet_allergies에는 FK가 없어 DB가 알아서 지워주지 않는다 - 남겨두면 같은 pet_id가
        //재사용될 때(AUTO_INCREMENT 리셋 등) 남의 알레르기가 붙어 보일 수 있으므로 먼저 지운다.
        petMapper.deleteAllergiesByPetId(petId);
        petMapper.deletePetById(petId);
    }

    /*
     * 알레르기 여부와 선택 목록의 조합을 검증하고, 저장할 목록을 확정해 돌려준다.
     * 반환값이 빈 목록이면 저장할 선택이 없다는 뜻이다.
     */
    private List<PetAllergySelectionRequest> resolveSelections(Boolean hasAllergy,
                                                              List<PetAllergySelectionRequest> requested) {
        boolean requestedAny = requested != null && !requested.isEmpty();

        //여부를 안 보내고 목록만 보내면 사용자의 의도를 서버가 정할 수 없다(있다고 답한 건지,
        //여부는 그대로 두고 목록만 바꾸려는 건지) - 임의로 추측하지 않고 거절한다.
        if (hasAllergy == null && requestedAny) {
            throw new CommonException(ErrorCode.PET_ALLERGY_SELECTION_INVALID);
        }
        //"없음"인데 항목을 골랐다면 화면과 데이터가 어긋난 상태다.
        if (Boolean.FALSE.equals(hasAllergy) && requestedAny) {
            throw new CommonException(ErrorCode.PET_ALLERGY_SELECTION_INVALID);
        }
        if (!Boolean.TRUE.equals(hasAllergy) || !requestedAny) {
            return List.of();
        }

        //같은 항목을 두 번 보내도 PK 충돌로 500이 되지 않게 첫 번째 것만 남긴다.
        Map<Long, PetAllergySelectionRequest> distinct = new LinkedHashMap<>();
        for (PetAllergySelectionRequest selection : requested) {
            distinct.putIfAbsent(selection.getAllergyTypeId(), selection);
        }

        List<Long> ids = List.copyOf(distinct.keySet());
        Map<Long, PetAllergyType> types = new LinkedHashMap<>();
        for (PetAllergyType type : petAllergyTypeMapper.selectByIds(ids)) {
            types.put(type.getAllergyTypeId(), type);
        }
        if (types.size() != ids.size()) {
            throw new CommonException(ErrorCode.PET_ALLERGY_TYPE_NOT_FOUND);
        }
        if (types.values().stream().anyMatch(type -> !type.isActive())) {
            throw new CommonException(ErrorCode.PET_ALLERGY_TYPE_INACTIVE);
        }

        List<PetAllergySelectionRequest> resolved = new ArrayList<>(ids.size());
        for (Long id : ids) {
            PetAllergyType type = types.get(id);
            PetAllergySelectionRequest selection = distinct.get(id);
            String otherText = selection.getOtherText() == null ? null : selection.getOtherText().trim();
            if (type.isRequiresText()) {
                //'기타'는 무엇에 알레르기가 있는지가 이 칸에만 있다 - 비어 있으면 저장해도 의미가 없다.
                if (otherText == null || otherText.isEmpty()) {
                    throw new CommonException(ErrorCode.PET_ALLERGY_SELECTION_INVALID);
                }
            } else {
                //직접 입력칸이 없는 항목에 온 값은 버린다(쓰이지 않는 자리에 문자열을 쌓지 않는다).
                otherText = null;
            }
            PetAllergySelectionRequest normalized = new PetAllergySelectionRequest();
            normalized.setAllergyTypeId(id);
            normalized.setOtherText(otherText);
            resolved.add(normalized);
        }
        return resolved;
    }

    /*
     * presigned 업로드로 받은 임시 객체 키를 확정(tmp -> uploads)하고 공개 URL로 바꾼다.
     * 키가 없으면(이미지를 안 바꾸는 경우) null을 그대로 반환한다 - 부분 업데이트에서
     * null은 "이 필드는 갱신 안 함"을 뜻하므로 자연스럽게 기존 이미지가 유지된다.
     */
    private String resolveImageUrl(String temporaryObjectKey) {
        if (temporaryObjectKey == null || temporaryObjectKey.isBlank()) {
            return null;
        }
        String confirmedKey = storageService.confirm(temporaryObjectKey, UploadPolicy.IMAGE);
        return storageService.toPublicUrl(confirmedKey);
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

    private Map<Long, List<PetAllergyResponse>> loadAllergies(List<Long> petIds) {
        if (petIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<PetAllergyResponse>> grouped = new LinkedHashMap<>();
        for (PetAllergyRow row : petMapper.selectAllergiesByPetIds(petIds)) {
            grouped.computeIfAbsent(row.getPetId(), key -> new ArrayList<>())
                    .add(PetAllergyResponse.from(row));
        }
        return grouped;
    }

    private PetResponse toResponseWithAllergies(Pet pet) {
        return toResponse(pet, loadAllergies(List.of(pet.getPetId()))
                .getOrDefault(pet.getPetId(), List.of()));
    }

    private PetResponse toResponse(Pet pet, List<PetAllergyResponse> allergies) {
        return new PetResponse(
                pet.getPetId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getBirthDate(),
                pet.getGender(),
                pet.getIsNeutered(),
                pet.getImageUrl(),
                pet.getCreatedAt(),
                pet.getHasAllergy(),
                allergies
        );
    }
}
