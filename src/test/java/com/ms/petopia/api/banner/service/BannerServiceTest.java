package com.ms.petopia.api.banner.service;

import com.ms.petopia.api.banner.domain.Banner;
import com.ms.petopia.api.banner.dto.request.BannerCreateRequest;
import com.ms.petopia.api.banner.dto.request.BannerOrderRequest;
import com.ms.petopia.api.banner.dto.request.BannerUpdateRequest;
import com.ms.petopia.api.banner.dto.response.BannerResponse;
import com.ms.petopia.api.banner.mapper.BannerMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

    @Mock
    private BannerMapper bannerMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private BannerService bannerService;

    private Banner createBanner(Long bannerId) {
        return Banner.builder()
                .bannerId(bannerId)
                .title("테스트 배너")
                .imageKey("uploads/banner/test.jpg")
                .sortOrder(0)
                .isActive(true)
                .build();
    }

    @Nested
    @DisplayName("활성 배너 목록 조회")
    class GetActiveList {

        @Test
        @DisplayName("노출 중인 배너 목록을 반환한다")
        void returnsActiveList() {
            given(bannerMapper.selectActiveList()).willReturn(List.of(createBanner(1L)));

            List<BannerResponse> result = bannerService.getActiveList();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("배너 단건 조회")
    class GetById {

        @Test
        @DisplayName("존재하는 배너를 정상 조회한다")
        void getsSuccessfully() {
            given(bannerMapper.selectById(1L)).willReturn(createBanner(1L));

            BannerResponse result = bannerService.getById(1L);

            assertThat(result.getBannerId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("배너가 없으면 예외를 던진다")
        void throwsWhenNotFound() {
            given(bannerMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> bannerService.getById(999L))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 배너");
        }
    }

    @Nested
    @DisplayName("배너 등록")
    class Create {

        @Test
        @DisplayName("정상적으로 배너를 등록한다")
        void createsSuccessfully() {
            BannerCreateRequest request = new BannerCreateRequest();
            request.setTitle("신규 배너");
            request.setImageKey("uploads/banner/new.jpg");
            request.setLinkTarget(Banner.LinkTarget.SELF);
            request.setSortOrder(0);

            given(storageService.confirm("uploads/banner/new.jpg", UploadPolicy.IMAGE))
                    .willReturn("banner/new.jpg");
            given(storageService.toPublicUrl("banner/new.jpg"))
                    .willReturn("https://cdn.example.com/banner/new.jpg");
            given(bannerMapper.selectById(any())).willReturn(createBanner(1L));

            BannerResponse result = bannerService.create(1L, request);

            verify(bannerMapper).insert(any());
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("배너 수정")
    class Update {

        @Test
        @DisplayName("값이 있는 필드만 수정한다")
        void updatesSuccessfully() {
            BannerUpdateRequest request = new BannerUpdateRequest();
            request.setTitle("수정된 배너");

            given(bannerMapper.selectById(1L))
                    .willReturn(createBanner(1L), createBanner(1L));

            BannerResponse result = bannerService.update(1L, request);

            verify(bannerMapper).update(any());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("수정할 필드가 없으면 UPDATE를 호출하지 않는다")
        void skipsUpdateWhenAllFieldsNull() {
            given(bannerMapper.selectById(1L))
                    .willReturn(createBanner(1L), createBanner(1L));

            bannerService.update(1L, new BannerUpdateRequest());

            verify(bannerMapper, never()).update(any());
        }

        @Test
        @DisplayName("배너가 없으면 예외를 던진다")
        void throwsWhenNotFound() {
            given(bannerMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> bannerService.update(999L, new BannerUpdateRequest()))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 배너");
        }
    }

    @Nested
    @DisplayName("배너 삭제")
    class Delete {

        @Test
        @DisplayName("정상적으로 삭제한다")
        void deletesSuccessfully() {
            given(bannerMapper.selectById(1L)).willReturn(createBanner(1L));

            bannerService.delete(1L);

            verify(bannerMapper).delete(1L);
        }

        @Test
        @DisplayName("배너가 없으면 예외를 던지고 삭제를 시도하지 않는다")
        void throwsWhenNotFound() {
            given(bannerMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> bannerService.delete(999L))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 배너");

            verify(bannerMapper, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("배너 순서 변경")
    class UpdateOrder {

        @Test
        @DisplayName("전달된 순서대로 sort_order를 갱신한다")
        void updatesOrderByIndex() {
            given(bannerMapper.selectActiveList())
                    .willReturn(List.of(createBanner(1L), createBanner(2L), createBanner(3L)));

            BannerOrderRequest request = new BannerOrderRequest();
            request.setBannerIds(List.of(3L, 1L, 2L));

            bannerService.updateOrder(request);

            verify(bannerMapper).updateOrder(3L, 0);
            verify(bannerMapper).updateOrder(1L, 1);
            verify(bannerMapper).updateOrder(2L, 2);
        }
    }
}
