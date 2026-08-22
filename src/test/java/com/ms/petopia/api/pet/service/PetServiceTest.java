package com.ms.petopia.api.pet.service;

import com.ms.petopia.api.pet.domain.Pet;
import com.ms.petopia.api.pet.domain.PetAllergyType;
import com.ms.petopia.api.pet.dto.PetAllergyRow;
import com.ms.petopia.api.pet.dto.PetAllergySelectionRequest;
import com.ms.petopia.api.pet.dto.PetCreateRequest;
import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.pet.dto.PetUpdateRequest;
import com.ms.petopia.api.pet.mapper.PetAllergyTypeMapper;
import com.ms.petopia.api.pet.mapper.PetMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PET_ID = 10L;
    private static final Long CHICKEN_TYPE_ID = 100L;
    private static final Long POLLEN_TYPE_ID = 101L;
    private static final Long OTHER_TYPE_ID = 199L;

    @Mock
    private PetMapper petMapper;
    @Mock
    private PetAllergyTypeMapper petAllergyTypeMapper;
    @Mock
    private StorageService storageService;
    @InjectMocks
    private PetService petService;

    @Test
    void getMyPets_소유한반려동물목록을응답으로변환한다() {
        given(petMapper.selectPetsByUserId(USER_ID)).willReturn(List.of(pet(USER_ID)));

        List<PetResponse> result = petService.getMyPets(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).petId()).isEqualTo(PET_ID);
        assertThat(result.get(0).name()).isEqualTo("초코");
    }

    @Test
    void getMyPets_알레르기를한번에조회해_반려동물별로붙인다() {
        given(petMapper.selectPetsByUserId(USER_ID)).willReturn(List.of(pet(USER_ID)));
        given(petMapper.selectAllergiesByPetIds(List.of(PET_ID)))
                .willReturn(List.of(allergyRow(CHICKEN_TYPE_ID, "CHICKEN", "FOOD", "닭고기", false, null)));

        List<PetResponse> result = petService.getMyPets(USER_ID);

        assertThat(result.get(0).allergies()).hasSize(1);
        assertThat(result.get(0).allergies().get(0).label()).isEqualTo("닭고기");
    }

    @Test
    void getPet_본인소유면_응답으로변환한다() {
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        PetResponse result = petService.getPet(USER_ID, PET_ID);

        assertThat(result.petId()).isEqualTo(PET_ID);
        assertThat(result.species()).isEqualTo("DOG");
    }

    @Test
    void getPet_존재하지않으면_PET_NOT_FOUND를던진다() {
        given(petMapper.selectPetById(PET_ID)).willReturn(null);

        assertThatThrownBy(() -> petService.getPet(USER_ID, PET_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_NOT_FOUND);
    }

    @Test
    void getPet_다른사람소유면_PET_ACCESS_DENIED를던진다() {
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(OTHER_USER_ID));

        assertThatThrownBy(() -> petService.getPet(USER_ID, PET_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_ACCESS_DENIED);
    }

    @Test
    void createPet_등록후_생성된PK로다시조회한값을응답한다() {
        PetCreateRequest request = new PetCreateRequest();
        request.setName("초코");
        request.setSpecies("DOG");

        //실제 DB의 useGeneratedKeys 동작을 흉내낸다 - insert 시 넘긴 Pet 객체에 petId가 채워짐
        given(petMapper.insertPet(any(Pet.class))).willAnswer(invocation -> {
            Pet argument = invocation.getArgument(0);
            argument.setPetId(PET_ID);
            return 1;
        });
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        PetResponse result = petService.createPet(USER_ID, request);

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(petMapper).insertPet(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getName()).isEqualTo("초코");
        assertThat(result.petId()).isEqualTo(PET_ID);
        verify(petMapper, never()).insertAllergies(any(), anyList());
    }

    @Test
    void createPet_이미지objectKey가있으면_확정후공개URL을저장한다() {
        PetCreateRequest request = new PetCreateRequest();
        request.setName("초코");
        request.setSpecies("DOG");
        request.setImageObjectKey("tmp/abc.jpg");

        given(storageService.confirm("tmp/abc.jpg", UploadPolicy.IMAGE)).willReturn("uploads/abc.jpg");
        given(storageService.toPublicUrl("uploads/abc.jpg")).willReturn("https://cdn.petopia.com/uploads/abc.jpg");
        given(petMapper.insertPet(any(Pet.class))).willAnswer(invocation -> {
            Pet argument = invocation.getArgument(0);
            argument.setPetId(PET_ID);
            return 1;
        });
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.createPet(USER_ID, request);

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(petMapper).insertPet(captor.capture());
        assertThat(captor.getValue().getImageUrl()).isEqualTo("https://cdn.petopia.com/uploads/abc.jpg");
    }

    // ===== 알레르기 등록 =====

    @Test
    void createPet_알레르기가있다고하면_고른항목을함께저장한다() {
        PetCreateRequest request = createRequestWithAllergy(true,
                selection(CHICKEN_TYPE_ID, null), selection(POLLEN_TYPE_ID, null));
        given(petAllergyTypeMapper.selectByIds(List.of(CHICKEN_TYPE_ID, POLLEN_TYPE_ID)))
                .willReturn(List.of(
                        allergyType(CHICKEN_TYPE_ID, "CHICKEN", false, true),
                        allergyType(POLLEN_TYPE_ID, "POLLEN", false, true)));
        givenInsertAssignsPetId();
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.createPet(USER_ID, request);

        ArgumentCaptor<Pet> petCaptor = ArgumentCaptor.forClass(Pet.class);
        verify(petMapper).insertPet(petCaptor.capture());
        assertThat(petCaptor.getValue().getHasAllergy()).isTrue();

        List<PetAllergySelectionRequest> saved = captureSavedSelections();
        assertThat(saved).extracting(PetAllergySelectionRequest::getAllergyTypeId)
                .containsExactly(CHICKEN_TYPE_ID, POLLEN_TYPE_ID);
    }

    @Test
    void createPet_같은항목을두번보내면_하나로합쳐서저장한다() {
        PetCreateRequest request = createRequestWithAllergy(true,
                selection(CHICKEN_TYPE_ID, null), selection(CHICKEN_TYPE_ID, null));
        given(petAllergyTypeMapper.selectByIds(List.of(CHICKEN_TYPE_ID)))
                .willReturn(List.of(allergyType(CHICKEN_TYPE_ID, "CHICKEN", false, true)));
        givenInsertAssignsPetId();
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.createPet(USER_ID, request);

        assertThat(captureSavedSelections()).hasSize(1);
    }

    @Test
    void createPet_기타항목은_직접입력값을그대로저장한다() {
        PetCreateRequest request = createRequestWithAllergy(true, selection(OTHER_TYPE_ID, "  자갈  "));
        given(petAllergyTypeMapper.selectByIds(List.of(OTHER_TYPE_ID)))
                .willReturn(List.of(allergyType(OTHER_TYPE_ID, "OTHER", true, true)));
        givenInsertAssignsPetId();
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.createPet(USER_ID, request);

        assertThat(captureSavedSelections().get(0).getOtherText()).isEqualTo("자갈");
    }

    @Test
    void createPet_기타항목인데직접입력이비면_PET_ALLERGY_SELECTION_INVALID를던진다() {
        PetCreateRequest request = createRequestWithAllergy(true, selection(OTHER_TYPE_ID, "   "));
        given(petAllergyTypeMapper.selectByIds(List.of(OTHER_TYPE_ID)))
                .willReturn(List.of(allergyType(OTHER_TYPE_ID, "OTHER", true, true)));

        assertThatThrownBy(() -> petService.createPet(USER_ID, request))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_ALLERGY_SELECTION_INVALID);
        verify(petMapper, never()).insertPet(any());
    }

    @Test
    void createPet_직접입력칸이없는항목에온텍스트는_버린다() {
        PetCreateRequest request = createRequestWithAllergy(true, selection(CHICKEN_TYPE_ID, "몰래보낸값"));
        given(petAllergyTypeMapper.selectByIds(List.of(CHICKEN_TYPE_ID)))
                .willReturn(List.of(allergyType(CHICKEN_TYPE_ID, "CHICKEN", false, true)));
        givenInsertAssignsPetId();
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.createPet(USER_ID, request);

        assertThat(captureSavedSelections().get(0).getOtherText()).isNull();
    }

    @Test
    void createPet_여부없이목록만보내면_PET_ALLERGY_SELECTION_INVALID를던진다() {
        PetCreateRequest request = createRequestWithAllergy(null, selection(CHICKEN_TYPE_ID, null));

        assertThatThrownBy(() -> petService.createPet(USER_ID, request))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_ALLERGY_SELECTION_INVALID);
    }

    @Test
    void createPet_없다고했는데목록을보내면_PET_ALLERGY_SELECTION_INVALID를던진다() {
        PetCreateRequest request = createRequestWithAllergy(false, selection(CHICKEN_TYPE_ID, null));

        assertThatThrownBy(() -> petService.createPet(USER_ID, request))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_ALLERGY_SELECTION_INVALID);
    }

    @Test
    void createPet_없는알레르기항목이면_PET_ALLERGY_TYPE_NOT_FOUND를던진다() {
        PetCreateRequest request = createRequestWithAllergy(true, selection(CHICKEN_TYPE_ID, null));
        given(petAllergyTypeMapper.selectByIds(List.of(CHICKEN_TYPE_ID))).willReturn(List.of());

        assertThatThrownBy(() -> petService.createPet(USER_ID, request))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_ALLERGY_TYPE_NOT_FOUND);
    }

    @Test
    void createPet_비활성알레르기항목이면_PET_ALLERGY_TYPE_INACTIVE를던진다() {
        PetCreateRequest request = createRequestWithAllergy(true, selection(CHICKEN_TYPE_ID, null));
        given(petAllergyTypeMapper.selectByIds(List.of(CHICKEN_TYPE_ID)))
                .willReturn(List.of(allergyType(CHICKEN_TYPE_ID, "CHICKEN", false, false)));

        assertThatThrownBy(() -> petService.createPet(USER_ID, request))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_ALLERGY_TYPE_INACTIVE);
    }

    // ===== 알레르기 수정 =====

    @Test
    void updatePet_알레르기없음으로바꾸면_기존목록을전부지운다() {
        PetUpdateRequest request = new PetUpdateRequest();
        request.setHasAllergy(false);
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper).updatePet(eq(PET_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull());
        verify(petMapper).deleteAllergiesByPetId(PET_ID);
        verify(petMapper, never()).insertAllergies(any(), anyList());
    }

    @Test
    void updatePet_목록을보내면_전체삭제후재삽입한다() {
        PetUpdateRequest request = new PetUpdateRequest();
        request.setHasAllergy(true);
        request.setAllergies(List.of(selection(POLLEN_TYPE_ID, null)));
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));
        given(petAllergyTypeMapper.selectByIds(List.of(POLLEN_TYPE_ID)))
                .willReturn(List.of(allergyType(POLLEN_TYPE_ID, "POLLEN", false, true)));

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper).deleteAllergiesByPetId(PET_ID);
        assertThat(captureSavedSelections()).extracting(PetAllergySelectionRequest::getAllergyTypeId)
                .containsExactly(POLLEN_TYPE_ID);
    }

    @Test
    void updatePet_있다고만하고목록을안보내면_기존목록을그대로둔다() {
        PetUpdateRequest request = new PetUpdateRequest();
        request.setHasAllergy(true);
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper).updatePet(eq(PET_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true), isNull());
        verify(petMapper, never()).deleteAllergiesByPetId(any());
        verify(petMapper, never()).insertAllergies(any(), anyList());
    }

    @Test
    void updatePet_여부를안보내면_알레르기를건드리지않는다() {
        PetUpdateRequest request = new PetUpdateRequest();
        request.setName("새이름");
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper, never()).deleteAllergiesByPetId(any());
        verify(petMapper, never()).insertAllergies(any(), anyList());
    }

    @Test
    void updatePet_이미지objectKey가있으면_확정후공개URL로갱신한다() {
        PetUpdateRequest request = new PetUpdateRequest();
        request.setImageObjectKey("tmp/new.jpg");
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));
        given(storageService.confirm("tmp/new.jpg", UploadPolicy.IMAGE)).willReturn("uploads/new.jpg");
        given(storageService.toPublicUrl("uploads/new.jpg")).willReturn("https://cdn.petopia.com/uploads/new.jpg");

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper).updatePet(eq(PET_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("https://cdn.petopia.com/uploads/new.jpg"));
    }

    @Test
    void updatePet_일부필드만보내면_그필드만매퍼에넘긴다() {
        PetUpdateRequest request = new PetUpdateRequest();
        request.setName("새이름");
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper).updatePet(eq(PET_ID), eq("새이름"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void updatePet_아무필드도안보내면_매퍼를호출하지않는다() {
        PetUpdateRequest request = new PetUpdateRequest();
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper, never()).updatePet(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updatePet_존재하지않으면_PET_NOT_FOUND를던진다() {
        given(petMapper.selectPetById(PET_ID)).willReturn(null);

        assertThatThrownBy(() -> petService.updatePet(USER_ID, PET_ID, new PetUpdateRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_NOT_FOUND);
    }

    @Test
    void updatePet_다른사람소유면_PET_ACCESS_DENIED를던지고_매퍼를호출하지않는다() {
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(OTHER_USER_ID));

        assertThatThrownBy(() -> petService.updatePet(USER_ID, PET_ID, new PetUpdateRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_ACCESS_DENIED);
        verify(petMapper, never()).updatePet(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deletePet_본인소유면_알레르기까지함께삭제한다() {
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.deletePet(USER_ID, PET_ID);

        verify(petMapper).deleteAllergiesByPetId(PET_ID);
        verify(petMapper).deletePetById(PET_ID);
    }

    @Test
    void deletePet_존재하지않으면_PET_NOT_FOUND를던진다() {
        given(petMapper.selectPetById(PET_ID)).willReturn(null);

        assertThatThrownBy(() -> petService.deletePet(USER_ID, PET_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_NOT_FOUND);
        verify(petMapper, never()).deletePetById(any());
    }

    @Test
    void deletePet_다른사람소유면_PET_ACCESS_DENIED를던지고_삭제하지않는다() {
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(OTHER_USER_ID));

        assertThatThrownBy(() -> petService.deletePet(USER_ID, PET_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PET_ACCESS_DENIED);
        verify(petMapper, never()).deletePetById(any());
    }

    // ===== 테스트 보조 =====

    private void givenInsertAssignsPetId() {
        given(petMapper.insertPet(any(Pet.class))).willAnswer(invocation -> {
            Pet argument = invocation.getArgument(0);
            argument.setPetId(PET_ID);
            return 1;
        });
    }

    @SuppressWarnings("unchecked")
    private List<PetAllergySelectionRequest> captureSavedSelections() {
        ArgumentCaptor<List<PetAllergySelectionRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(petMapper).insertAllergies(eq(PET_ID), captor.capture());
        return captor.getValue();
    }

    private PetCreateRequest createRequestWithAllergy(Boolean hasAllergy, PetAllergySelectionRequest... selections) {
        PetCreateRequest request = new PetCreateRequest();
        request.setName("초코");
        request.setSpecies("DOG");
        request.setHasAllergy(hasAllergy);
        request.setAllergies(List.of(selections));
        return request;
    }

    private PetAllergySelectionRequest selection(Long allergyTypeId, String otherText) {
        PetAllergySelectionRequest selection = new PetAllergySelectionRequest();
        selection.setAllergyTypeId(allergyTypeId);
        selection.setOtherText(otherText);
        return selection;
    }

    private PetAllergyType allergyType(Long id, String code, boolean requiresText, boolean active) {
        PetAllergyType type = new PetAllergyType();
        type.setAllergyTypeId(id);
        type.setCode(code);
        type.setCategory("OTHER".equals(code) ? "OTHER" : "FOOD");
        type.setLabel(code);
        type.setRequiresText(requiresText);
        type.setSortOrder(1);
        type.setActive(active);
        return type;
    }

    private PetAllergyRow allergyRow(Long id, String code, String category, String label, boolean requiresText, String otherText) {
        PetAllergyRow row = new PetAllergyRow();
        row.setPetId(PET_ID);
        row.setAllergyTypeId(id);
        row.setCode(code);
        row.setCategory(category);
        row.setLabel(label);
        row.setRequiresText(requiresText);
        row.setOtherText(otherText);
        return row;
    }

    private Pet pet(Long ownerId) {
        return Pet.builder()
                .petId(PET_ID)
                .userId(ownerId)
                .name("초코")
                .species("DOG")
                .breed("포메라니안")
                .birthDate(LocalDate.of(2023, 5, 1))
                .gender("MALE")
                .isNeutered(true)
                .imageUrl("https://example.com/choco.jpg")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }
}
