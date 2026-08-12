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

    // 테스트용 사업자를 만드는 헬퍼 메서드
    private Business createBusiness(Long businessId, Long ownerId) {

        Business business = new Business();

        business.setBusinessId(businessId);
        business.setOwnerId(ownerId);

        return business;
    }

    // 테스트용 부스를 만드는 헬퍼 메서드
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

            // given: 부스와 그 부스에 등록된 상품 1건이 존재하는 상황
            Long boothId = 1L;

            Booth booth = createBooth(boothId, 1L);
            BoothItem item = BoothItem.builder()
                    .boothItemId(1L).boothId(boothId).name("체험팩").type(BoothItem.Type.SAMPLE)
                    .build();

            given(boothMapper.selectById(boothId)).willReturn(booth);
            given(boothMapper.selectItemsByBoothId(boothId)).willReturn(List.of(item));

            // when
            BoothResponse result = boothService.getBooth(boothId, null);

            // then: 부스 정보 + 상품 목록이 응답에 같이 담겼는지 확인
            assertThat(result.getBoothId()).isEqualTo(boothId);
            assertThat(result.getItems()).hasSize(1);

        }

        @Test
        @DisplayName("부스가 없으면 예외를 던진다")
        void throwsWhenBoothNotFound() {

            // given: 존재하지 않는 boothId
            Long boothId = 999L;

            given(boothMapper.selectById(boothId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> boothService.getBooth(boothId, null))
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

            /*
             * given: 요청자(1L)가 그 부스가 속한 사업자의 owner인 상황.
             * selectById는 소유권 확인용(1번째)과 갱신 후 재조회용(2번째) 두 번 불리므로 순서대로 스텁
             */
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

            // then: 수정 쿼리가 실제로 호출됐는지 확인
            assertThat(result).isNotNull();
            verify(boothMapper).updateBooth(any());

        }

        @Test
        @DisplayName("본인 소유가 아니면 예외를 던진다")
        void throwsWhenNotOwner() {

            // given: 부스는 businessId=1(owner=1L) 소유인데, 요청자는 2L인 상황
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

            // given: 존재하지 않는 boothId
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

            // given: presigned-upload로 받은 임시 objectKey를 포함한 수정 요청
            Long callerId = 1L;
            Long boothId = 1L;
            Long businessId = 1L;

            BoothUpdateRequest request = new BoothUpdateRequest();
            request.setImageObjectKey("tmp/image/booth.jpg");

            given(boothMapper.selectById(boothId))
                    .willReturn(createBooth(boothId, businessId), createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, callerId));
            // 스토리지 확정 흐름 스텁
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

        @Test
        @DisplayName("갱신할 필드가 하나도 없으면 UPDATE 없이 현재 상태를 그대로 반환한다")
        void skipsUpdateWhenAllFieldsNull() {

            // given: 요청 필드가 전부 null인 상황({} 바디)
            Long callerId = 1L;
            Long boothId = 1L;
            Long businessId = 1L;

            given(boothMapper.selectById(boothId))
                    .willReturn(createBooth(boothId, businessId), createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, callerId));

            // when
            BoothResponse result = boothService.updateBooth(callerId, boothId, new BoothUpdateRequest());

            // then: 빈 SET절로 SQL 오류가 나는 걸 막기 위해 updateBooth 자체가 호출되지 않아야 함
            assertThat(result).isNotNull();
            verify(boothMapper, never()).updateBooth(any());

        }

    }

    @Nested
    @DisplayName("판매상품·이벤트 등록")
    class AddItem {

        @Test
        @DisplayName("본인 소유 부스면 정상적으로 등록한다")
        void addsSuccessfully() {

            // given: 요청자가 그 부스의 소유자인 상황
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

            // then: 등록 쿼리가 호출됐고 응답에 입력값이 그대로 담겼는지 확인
            assertThat(result.getName()).isEqualTo("체험팩");
            verify(boothMapper).insertBoothItem(any());

        }

        @Test
        @DisplayName("본인 소유가 아니면 예외를 던진다")
        void throwsWhenNotOwner() {

            // given: 부스는 owner=1L 소유인데, 요청자는 2L인 상황
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

            /*
             * given: 요청자가 그 상품이 속한 부스의 소유자인 상황.
             * selectItemById는 존재확인용(1번째)과 갱신 후 재조회용(2번째) 두 번 불리므로 순서대로 스텁
             */
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

            // then: 수정된 이름이 응답에 반영됐는지 확인
            assertThat(result.getName()).isEqualTo("체험팩(수정)");

        }

        @Test
        @DisplayName("상품이 없으면 예외를 던진다")
        void throwsWhenItemNotFound() {

            // given: 존재하지 않는 boothItemId
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

            // given: 상품은 존재하지만, 그 상품이 속한 부스의 소유자가 요청자(2L)가 아닌 상황
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

        @Test
        @DisplayName("갱신할 필드가 하나도 없으면 UPDATE 없이 현재 상태를 그대로 반환한다")
        void skipsUpdateWhenAllFieldsNull() {

            // given: 요청 필드가 전부 null인 상황({} 바디)
            Long callerId = 1L;
            Long boothId = 1L;
            Long boothItemId = 1L;
            Long businessId = 1L;

            BoothItem item = BoothItem.builder().boothItemId(boothItemId).boothId(boothId).build();

            given(boothMapper.selectItemById(boothItemId)).willReturn(item, item);
            given(boothMapper.selectById(boothId)).willReturn(createBooth(boothId, businessId));
            given(businessMapper.selectById(businessId)).willReturn(createBusiness(businessId, callerId));

            // when
            BoothItemResponse result = boothService.updateItem(callerId, boothItemId, new BoothItemUpdateRequest());

            // then: 빈 SET절로 SQL 오류가 나는 걸 막기 위해 updateBoothItem 자체가 호출되지 않아야 함
            assertThat(result).isNotNull();
            verify(boothMapper, never()).updateBoothItem(any());

        }

    }

    @Nested
    @DisplayName("판매상품·이벤트 삭제")
    class DeleteItem {

        @Test
        @DisplayName("본인 소유 부스의 상품이면 정상적으로 삭제한다")
        void deletesSuccessfully() {

            // given: 요청자가 그 상품이 속한 부스의 소유자인 상황
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

            // then: 삭제 쿼리가 실제로 호출됐는지 확인
            verify(boothMapper).deleteBoothItem(boothItemId);

        }

        @Test
        @DisplayName("상품이 없으면 예외를 던지고 삭제를 시도하지 않는다")
        void throwsWhenItemNotFound() {

            // given: 존재하지 않는 boothItemId
            Long callerId = 1L;
            Long boothItemId = 999L;

            given(boothMapper.selectItemById(boothItemId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> boothService.deleteItem(callerId, boothItemId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("판매상품·이벤트를 찾을 수 없습니다");

            // 상품 자체가 없으니, 삭제 쿼리는 시도되면 안 됨
            verify(boothMapper, never()).deleteBoothItem(any());

        }

    }

    @Nested
    @DisplayName("확정 부스 안내판 조회")
    class GetConfirmedBooths {

        @Test
        @DisplayName("정상적으로 슬롯 목록을 조회한다")
        void getsConfirmedBoothsSuccessfully() {

            // given: 행사가 존재하고, 확정 부스 슬롯 1건이 있는 상황
            Long fairId = 1L;
            ConfirmedBoothResponse response = new ConfirmedBoothResponse(1L, "멍냥사료", "A-01", null, null, null, null, null, null, null);

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

            // given: 존재하지 않는 fairId
            Long fairId = 999L;

            given(boothMapper.existsFair(fairId)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> boothService.getConfirmedBooths(fairId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 행사");

            // 행사 자체가 없으니, 슬롯 조회 쿼리는 시도되면 안 됨
            verify(boothMapper, never()).selectConfirmedBooths(any());

        }

    }

    @Nested
    @DisplayName("즐겨찾기 추가")
    class AddFavorite {

        @Test
        @DisplayName("부스가 존재하면 정상적으로 추가한다")
        void addsSuccessfully() {

            // given: 존재하는 boothId
            Long userId = 1L;
            Long boothId = 1L;

            given(boothMapper.selectById(boothId)).willReturn(createBooth(boothId, 1L));

            // when
            boothService.addFavorite(userId, boothId);

            // then: insert 쿼리가 실제로 호출됐는지 확인
            verify(boothMapper).insertFavorite(userId, boothId);

        }

        @Test
        @DisplayName("부스가 없으면 예외를 던지고 추가를 시도하지 않는다")
        void throwsWhenBoothNotFound() {

            // given: 존재하지 않는 boothId
            Long userId = 1L;
            Long boothId = 999L;

            given(boothMapper.selectById(boothId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> boothService.addFavorite(userId, boothId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("부스를 찾을 수 없습니다");

            // 부스 자체가 없으니, insert 쿼리는 시도되면 안 됨
            verify(boothMapper, never()).insertFavorite(any(), any());

        }

    }

    @Nested
    @DisplayName("즐겨찾기 삭제")
    class RemoveFavorite {

        @Test
        @DisplayName("정상적으로 삭제한다")
        void removesSuccessfully() {

            // given
            Long userId = 1L;
            Long boothId = 1L;

            // when
            boothService.removeFavorite(userId, boothId);

            // then: 삭제 쿼리가 실제로 호출됐는지 확인 (존재하지 않는 조합이어도 멱등하게 통과하므로 존재 확인 없음)
            verify(boothMapper).deleteFavorite(userId, boothId);

        }

    }


}
