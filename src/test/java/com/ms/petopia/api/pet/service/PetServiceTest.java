package com.ms.petopia.api.pet.service;

import com.ms.petopia.api.pet.domain.Pet;
import com.ms.petopia.api.pet.dto.PetCreateRequest;
import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.pet.dto.PetUpdateRequest;
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

    @Mock
    private PetMapper petMapper;
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

    @Test
    void updatePet_이미지objectKey가있으면_확정후공개URL로갱신한다() {
        PetUpdateRequest request = new PetUpdateRequest();
        request.setImageObjectKey("tmp/new.jpg");
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));
        given(storageService.confirm("tmp/new.jpg", UploadPolicy.IMAGE)).willReturn("uploads/new.jpg");
        given(storageService.toPublicUrl("uploads/new.jpg")).willReturn("https://cdn.petopia.com/uploads/new.jpg");

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper).updatePet(eq(PET_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("https://cdn.petopia.com/uploads/new.jpg"));
    }

    @Test
    void updatePet_일부필드만보내면_그필드만매퍼에넘긴다() {
        PetUpdateRequest request = new PetUpdateRequest();
        request.setName("새이름");
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper).updatePet(eq(PET_ID), eq("새이름"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void updatePet_아무필드도안보내면_매퍼를호출하지않는다() {
        PetUpdateRequest request = new PetUpdateRequest();
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.updatePet(USER_ID, PET_ID, request);

        verify(petMapper, never()).updatePet(any(), any(), any(), any(), any(), any(), any(), any());
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
        verify(petMapper, never()).updatePet(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deletePet_본인소유면_삭제한다() {
        given(petMapper.selectPetById(PET_ID)).willReturn(pet(USER_ID));

        petService.deletePet(USER_ID, PET_ID);

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
