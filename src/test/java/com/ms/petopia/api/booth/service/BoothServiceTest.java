package com.ms.petopia.api.booth.service;

import com.ms.petopia.api.booth.domain.Booth;
import com.ms.petopia.api.booth.domain.BoothItem;
import com.ms.petopia.api.booth.dto.request.BoothItemCreateRequest;
import com.ms.petopia.api.booth.dto.request.BoothItemUpdateRequest;
import com.ms.petopia.api.booth.dto.request.BoothUpdateRequest;
import com.ms.petopia.api.booth.dto.response.BoothItemResponse;
import com.ms.petopia.api.booth.dto.response.BoothResponse;
import com.ms.petopia.api.booth.dto.response.ConfirmedBoothResponse;
import com.ms.petopia.api.booth.mapper.BoothMapper;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * BoothService 단위 테스트.
 * BoothMapper/BusinessMapper/StorageService는 Mock으로 대체하고, Service의
 * 소유권 검증·부분 업데이트·조립 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BoothServiceTest {

    @Mock
    private BoothMapper boothMapper;

    @Mock
    private BusinessMapper businessMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private BoothService boothService;

    // 테스트용 사업자
    private Business createBusiness(Long businessId, Long ownerId) {

        Business business = new Business();

        business.setBusinessId(businessId);
        business.setOwnerId(ownerId);

        return business;
    }

    // 테스트용 부스
    private Booth createBooth(Long boothId, Long businessId) {

        return Booth.builder()
                .boothId(boothId)
                .applicationId(1L)
                .businessId(businessId)
                .name("멍냥사료 부스")
                .build();

    }

    @Nested
    @DisplayName("부스 상세 조회")
    class GetBooth {

        @Test
        @DisplayName("정상적으로 부스와 상품 목록을 조회한다")
        void getsBoothSuccessfully() {

            // given
            Long boothId = 1L;

            Booth booth = createBooth(boothId, 1L);
            BoothItem item = BoothItem.builder()
                    .boothItemId(1L).boothId(boothId).name("체험팩").type(BoothItem.Type.SAMPLE)
                    .build();

            given(boothMapper.selectById(boothId)).willReturn(booth);
            given(boothMapper.selectItemsByBoothId(boothId)).willReturn(List.of(item));

            // when
            BoothResponse result = boothService.getBooth(boothId);

            // then
            assertThat(result.getBoothId()).isEqualTo(boothId);
            assertThat(result.getItems()).hasSize(1);

        }

        @Test
        @DisplayName("부스가 없으면 예외를 던진다")
        void throwsWhenBoothNotFound() {

            // given
            Long boothId = 999L;

            given(boothMapper.selectById(boothId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> boothService.getBooth(boothId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("부스를 찾을 수 없습니다");

        }

    }

    @Nested
    @DisplayName("부스 프로필 수정")
    class UpdateBooth {

        @Test
        @DisplayName("본인 소유 부스면 정상적으로 수정한다")
        void updatesSuccessfully() {

            // given: 첫 조회(소유권 확인용)와 재조회(응답용)를 순서대로 스텁
            Long callerId = 1L;
            Long boothId = 1L;
            Long businessId = 1L;

            BoothUpdateRequest request = new BoothUpdateRequest();
            request.setName("수정된 이름");

            given(boothMapper.selectById(boothId))
                    .willReturn(createBooth(boothId, businessId), createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, callerId));

            // when
            BoothResponse result = boothService.updateBooth(callerId, boothId, request);

            // then
            assertThat(result).isNotNull();
            verify(boothMapper).updateBooth(any());

        }

        @Test
        @DisplayName("본인 소유가 아니면 예외를 던진다")
        void throwsWhenNotOwner() {

            // given: 부스는 businessId=1 소유인데, 요청자는 2L
            Long callerId = 2L;
            Long boothId = 1L;
            Long businessId = 1L;

            given(boothMapper.selectById(boothId)).willReturn(createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, 1L));

            // when & then
            assertThatThrownBy(() -> boothService.updateBooth(callerId, boothId, new BoothUpdateRequest()))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인 소유의 부스만");

        }

        @Test
        @DisplayName("부스가 없으면 예외를 던진다")
        void throwsWhenBoothNotFound() {

            // given
            Long callerId = 1L;
            Long boothId = 999L;

            given(boothMapper.selectById(boothId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> boothService.updateBooth(callerId, boothId, new BoothUpdateRequest()))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("부스를 찾을 수 없습니다");

        }

        @Test
        @DisplayName("imageObjectKey를 objectKey 확정 후 공개 URL로 변환해서 저장한다")
        void resolvesImageUrlFromObjectKey() {

            // given
            Long callerId = 1L;
            Long boothId = 1L;
            Long businessId = 1L;

            BoothUpdateRequest request = new BoothUpdateRequest();
            request.setImageObjectKey("tmp/image/booth.jpg");

            given(boothMapper.selectById(boothId))
                    .willReturn(createBooth(boothId, businessId), createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, callerId));
            given(storageService.confirm("tmp/image/booth.jpg", UploadPolicy.IMAGE))
                    .willReturn("uploads/image/booth.jpg");
            given(storageService.toPublicUrl("uploads/image/booth.jpg"))
                    .willReturn("https://cdn.petopia.kr/uploads/image/booth.jpg");

            ArgumentCaptor<Booth> captor = ArgumentCaptor.forClass(Booth.class);

            // when
            boothService.updateBooth(callerId, boothId, request);

            // then: 매퍼에 넘어간 patch 객체의 imageUrl이 confirm/toPublicUrl을 거친 최종 URL인지 확인
            verify(boothMapper).updateBooth(captor.capture());
            assertThat(captor.getValue().getImageUrl()).isEqualTo("https://cdn.petopia.kr/uploads/image/booth.jpg");

        }

    }

    @Nested
    @DisplayName("판매상품·이벤트 등록")
    class AddItem {

        @Test
        @DisplayName("본인 소유 부스면 정상적으로 등록한다")
        void addsSuccessfully() {

            // given
            Long callerId = 1L;
            Long boothId = 1L;
            Long businessId = 1L;

            BoothItemCreateRequest request = new BoothItemCreateRequest();
            request.setName("체험팩");
            request.setType(BoothItem.Type.SAMPLE);

            given(boothMapper.selectById(boothId)).willReturn(createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, callerId));

            // when
            BoothItemResponse result = boothService.addItem(callerId, boothId, request);

            // then
            assertThat(result.getName()).isEqualTo("체험팩");
            verify(boothMapper).insertBoothItem(any());

        }

        @Test
        @DisplayName("본인 소유가 아니면 예외를 던진다")
        void throwsWhenNotOwner() {

            // given
            Long callerId = 2L;
            Long boothId = 1L;
            Long businessId = 1L;

            given(boothMapper.selectById(boothId)).willReturn(createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, 1L));

            BoothItemCreateRequest request = new BoothItemCreateRequest();
            request.setName("체험팩");
            request.setType(BoothItem.Type.SAMPLE);

            // when & then
            assertThatThrownBy(() -> boothService.addItem(callerId, boothId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인 소유의 부스만");

        }

    }

    @Nested
    @DisplayName("판매상품·이벤트 수정")
    class UpdateItem {

        @Test
        @DisplayName("본인 소유 부스의 상품이면 정상적으로 수정한다")
        void updatesSuccessfully() {

            // given
            Long callerId = 1L;
            Long boothId = 1L;
            Long boothItemId = 1L;
            Long businessId = 1L;

            BoothItem item = BoothItem.builder()
                    .boothItemId(boothItemId).boothId(boothId).name("체험팩").type(BoothItem.Type.SAMPLE)
                    .build();
            BoothItem updated = BoothItem.builder()
                    .boothItemId(boothItemId).boothId(boothId).name("체험팩(수정)").type(BoothItem.Type.SAMPLE)
                    .build();

            BoothItemUpdateRequest request = new BoothItemUpdateRequest();
            request.setName("체험팩(수정)");

            given(boothMapper.selectItemById(boothItemId)).willReturn(item, updated);
            given(boothMapper.selectById(boothId)).willReturn(createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, callerId));

            // when
            BoothItemResponse result = boothService.updateItem(callerId, boothItemId, request);

            // then
            assertThat(result.getName()).isEqualTo("체험팩(수정)");

        }

        @Test
        @DisplayName("상품이 없으면 예외를 던진다")
        void throwsWhenItemNotFound() {

            // given
            Long callerId = 1L;
            Long boothItemId = 999L;

            given(boothMapper.selectItemById(boothItemId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> boothService.updateItem(callerId, boothItemId, new BoothItemUpdateRequest()))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("판매상품·이벤트를 찾을 수 없습니다");

        }

        @Test
        @DisplayName("본인 소유가 아니면 예외를 던진다")
        void throwsWhenNotOwner() {

            // given
            Long callerId = 2L;
            Long boothId = 1L;
            Long boothItemId = 1L;
            Long businessId = 1L;

            BoothItem item = BoothItem.builder().boothItemId(boothItemId).boothId(boothId).build();

            given(boothMapper.selectItemById(boothItemId)).willReturn(item);
            given(boothMapper.selectById(boothId)).willReturn(createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, 1L));

            // when & then
            assertThatThrownBy(() -> boothService.updateItem(callerId, boothItemId, new BoothItemUpdateRequest()))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인 소유의 부스만");

        }

    }

    @Nested
    @DisplayName("판매상품·이벤트 삭제")
    class DeleteItem {

        @Test
        @DisplayName("본인 소유 부스의 상품이면 정상적으로 삭제한다")
        void deletesSuccessfully() {

            // given
            Long callerId = 1L;
            Long boothId = 1L;
            Long boothItemId = 1L;
            Long businessId = 1L;

            BoothItem item = BoothItem.builder().boothItemId(boothItemId).boothId(boothId).build();

            given(boothMapper.selectItemById(boothItemId)).willReturn(item);
            given(boothMapper.selectById(boothId)).willReturn(createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, callerId));

            // when
            boothService.deleteItem(callerId, boothItemId);

            // then
            verify(boothMapper).deleteBoothItem(boothItemId);

        }

        @Test
        @DisplayName("상품이 없으면 예외를 던지고 삭제를 시도하지 않는다")
        void throwsWhenItemNotFound() {

            // given
            Long callerId = 1L;
            Long boothItemId = 999L;

            given(boothMapper.selectItemById(boothItemId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> boothService.deleteItem(callerId, boothItemId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("판매상품·이벤트를 찾을 수 없습니다");

            verify(boothMapper, never()).deleteBoothItem(any());

        }

    }

    @Nested
    @DisplayName("확정 부스 안내판 조회")
    class GetConfirmedBooths {

        @Test
        @DisplayName("정상적으로 슬롯 목록을 조회한다")
        void getsConfirmedBoothsSuccessfully() {

            // given
            Long fairId = 1L;
            ConfirmedBoothResponse response = new ConfirmedBoothResponse(1L, "멍냥사료", "A-01", null, null);

            given(boothMapper.existsFair(fairId)).willReturn(true);
            given(boothMapper.selectConfirmedBooths(fairId)).willReturn(List.of(response));

            // when
            List<ConfirmedBoothResponse> result = boothService.getConfirmedBooths(fairId);

            // then
            assertThat(result).hasSize(1);

        }

        @Test
        @DisplayName("존재하지 않는 행사면 예외를 던지고 조회를 시도하지 않는다")
        void throwsWhenFairNotFound() {

            // given
            Long fairId = 999L;

            given(boothMapper.existsFair(fairId)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> boothService.getConfirmedBooths(fairId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 행사");

            verify(boothMapper, never()).selectConfirmedBooths(any());

        }

    }


}
