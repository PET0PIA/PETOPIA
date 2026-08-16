package com.ms.petopia.api.popup.service;

import com.ms.petopia.api.popup.domain.Popup;
import com.ms.petopia.api.popup.dto.request.PopupCreateRequest;
import com.ms.petopia.api.popup.dto.request.PopupUpdateRequest;
import com.ms.petopia.api.popup.dto.response.PopupResponse;
import com.ms.petopia.api.popup.mapper.PopupMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PopupServiceTest {

    @Mock
    private PopupMapper popupMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private PopupService popupService;

    private Popup createPopup(Long popupId) {
        return Popup.builder()
                .popupId(popupId)
                .title("테스트 팝업")
                .imageKey("uploads/image/test.jpg")
                .width(400)
                .height(300)
                .isActive(true)
                .build();
    }

    @Nested
    @DisplayName("활성 팝업 목록 조회")
    class GetActiveList {

        @Test
        @DisplayName("노출 중인 팝업 목록을 반환한다")
        void returnsActiveList() {
            given(popupMapper.selectActiveList()).willReturn(List.of(createPopup(1L)));

            List<PopupResponse> result = popupService.getActiveList();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("팝업 단건 조회")
    class GetById {

        @Test
        @DisplayName("존재하는 팝업을 정상 조회한다")
        void getsSuccessfully() {
            given(popupMapper.selectById(1L)).willReturn(createPopup(1L));

            PopupResponse result = popupService.getById(1L);

            assertThat(result.getPopupId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("팝업이 없으면 예외를 던진다")
        void throwsWhenNotFound() {
            given(popupMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> popupService.getById(999L))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 팝업");
        }
    }

    @Nested
    @DisplayName("팝업 등록")
    class Create {

        @Test
        @DisplayName("정상적으로 팝업을 등록한다")
        void createsSuccessfully() {
            PopupCreateRequest request = new PopupCreateRequest();
            request.setTitle("신규 팝업");
            request.setImageKey("tmp/image/new.jpg");
            request.setLinkTarget(Popup.LinkTarget.SELF);
            request.setWidth(400);
            request.setHeight(300);

            given(storageService.confirm(eq("tmp/image/new.jpg"), eq(UploadPolicy.IMAGE)))
                    .willReturn("uploads/image/new.jpg");
            given(storageService.toPublicUrl("uploads/image/new.jpg"))
                    .willReturn("https://s3.example.com/uploads/image/new.jpg");
            given(popupMapper.selectById(any())).willReturn(createPopup(1L));

            PopupResponse result = popupService.create(1L, request);

            verify(storageService).confirm("tmp/image/new.jpg", UploadPolicy.IMAGE);
            verify(storageService).toPublicUrl("uploads/image/new.jpg");

            ArgumentCaptor<Popup> captor = ArgumentCaptor.forClass(Popup.class);
            verify(popupMapper).insert(captor.capture());
            assertThat(captor.getValue().getImageKey()).isEqualTo("https://s3.example.com/uploads/image/new.jpg");
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("팝업 수정")
    class Update {

        @Test
        @DisplayName("값이 있는 필드만 수정한다")
        void updatesSuccessfully() {
            PopupUpdateRequest request = new PopupUpdateRequest();
            request.setTitle("수정된 팝업");

            given(popupMapper.selectById(1L))
                    .willReturn(createPopup(1L), createPopup(1L));

            PopupResponse result = popupService.update(1L, request);

            verify(popupMapper).update(any());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("수정할 필드가 없으면 UPDATE를 호출하지 않는다")
        void skipsUpdateWhenAllFieldsNull() {
            given(popupMapper.selectById(1L))
                    .willReturn(createPopup(1L), createPopup(1L));

            popupService.update(1L, new PopupUpdateRequest());

            verify(popupMapper, never()).update(any());
        }

        @Test
        @DisplayName("공백만 입력한 필드는 삭제 신호로 보고 NULL로 반영한다")
        void clearsFieldWhenBlank() {
            PopupUpdateRequest request = new PopupUpdateRequest();
            request.setBgColor("   ");

            given(popupMapper.selectById(1L))
                    .willReturn(createPopup(1L), createPopup(1L));

            popupService.update(1L, request);

            ArgumentCaptor<Popup> captor = ArgumentCaptor.forClass(Popup.class);
            verify(popupMapper).update(captor.capture());
            assertThat(captor.getValue().getBgColor()).isEmpty();
        }

        @Test
        @DisplayName("이미지와 본문을 모두 비우면 예외를 던지고 UPDATE를 호출하지 않는다")
        void throwsWhenClearingBothImageAndSubtitle() {
            PopupUpdateRequest request = new PopupUpdateRequest();
            request.setImageKey("   ");

            given(popupMapper.selectById(1L)).willReturn(createPopup(1L));

            assertThatThrownBy(() -> popupService.update(1L, request))
                    .isInstanceOf(CommonException.class);

            verify(popupMapper, never()).update(any());
        }

        @Test
        @DisplayName("팝업이 없으면 예외를 던진다")
        void throwsWhenNotFound() {
            given(popupMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> popupService.update(999L, new PopupUpdateRequest()))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 팝업");
        }
    }

    @Nested
    @DisplayName("팝업 삭제")
    class Delete {

        @Test
        @DisplayName("정상적으로 삭제한다")
        void deletesSuccessfully() {
            given(popupMapper.selectById(1L)).willReturn(createPopup(1L));

            popupService.delete(1L);

            verify(popupMapper).delete(1L);
        }

        @Test
        @DisplayName("팝업이 없으면 예외를 던지고 삭제를 시도하지 않는다")
        void throwsWhenNotFound() {
            given(popupMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> popupService.delete(999L))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 팝업");

            verify(popupMapper, never()).delete(any());
        }
    }
}
