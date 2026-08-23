package com.ms.petopia.api.notice.service;

import com.ms.petopia.api.notice.domain.Notice;
import com.ms.petopia.api.notice.domain.NoticeAttachment;
import com.ms.petopia.api.notice.domain.NoticeCategory;
import com.ms.petopia.api.notice.dto.request.NoticeAttachmentRequest;
import com.ms.petopia.api.notice.dto.request.NoticeSaveRequest;
import com.ms.petopia.api.notice.mapper.NoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/*
 * NoticeAdminService 단위 테스트. 첨부를 지울 때 스토리지 객체까지 함께 정리되는지를 본다.
 * 단위 테스트에는 트랜잭션이 없으므로 정리 작업은 afterCommit을 기다리지 않고 즉시 실행된다
 * (NoticeAdminService#deleteStoredObjectsAfterCommit의 트랜잭션 밖 분기).
 */
@ExtendWith(MockitoExtension.class)
class NoticeAdminServiceTest {

    private static final Long NOTICE_ID = 1L;
    /** 테스트에서 쓰는 공개 base URL. 첨부 URL은 이 값 + "/" + 객체 키 모양이다. */
    private static final String BASE_URL = "https://cdn.example";

    @Mock
    private NoticeMapper noticeMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private NoticeAdminService noticeAdminService;

    // DB에 저장돼 있는 공지처럼 가정하는 헬퍼.
    private Notice notice() {
        return Notice.builder()
                .noticeId(NOTICE_ID)
                .category(NoticeCategory.NOTICE)
                .title("휴관 안내")
                .content("<p>안내드립니다.</p>")
                .build();
    }

    // 확정된 첨부 한 건. fileUrl은 실제 코드와 같은 방식으로 조립한다.
    private NoticeAttachment attachment(Long attachmentId, String objectKey) {
        return NoticeAttachment.builder()
                .attachmentId(attachmentId)
                .noticeId(NOTICE_ID)
                .fileUrl(BASE_URL + "/" + objectKey)
                .originalName("안내문.pdf")
                .fileSize(1024L)
                .sortOrder(0)
                .build();
    }

    // 남길 기존 첨부의 id만 담은 수정 요청. 목록에서 빠진 첨부가 삭제 대상이 된다.
    private NoticeSaveRequest keepOnly(Long... attachmentIds) {
        NoticeSaveRequest request = new NoticeSaveRequest();
        request.setCategory(NoticeCategory.NOTICE);
        request.setTitle("휴관 안내");
        request.setContent("<p>안내드립니다.</p>");
        request.setAttachments(Arrays.stream(attachmentIds)
                .map(id -> {
                    NoticeAttachmentRequest item = new NoticeAttachmentRequest();
                    item.setAttachmentId(id);
                    return item;
                })
                .toList());
        return request;
    }

    @Nested
    @DisplayName("공지 삭제")
    class DeleteNotice {

        @Test
        @DisplayName("첨부파일의 스토리지 객체까지 함께 지운다")
        void deletesAttachmentObjects() {
            String firstKey = "uploads/document/2026/08/23/first.pdf";
            String secondKey = "uploads/document/2026/08/23/second.pdf";
            given(noticeMapper.selectById(NOTICE_ID)).willReturn(notice());
            given(noticeMapper.selectAttachments(NOTICE_ID))
                    .willReturn(List.of(attachment(10L, firstKey), attachment(11L, secondKey)));
            given(storageService.toObjectKey(BASE_URL + "/" + firstKey)).willReturn(Optional.of(firstKey));
            given(storageService.toObjectKey(BASE_URL + "/" + secondKey)).willReturn(Optional.of(secondKey));

            noticeAdminService.delete(NOTICE_ID);

            verify(storageService).delete(firstKey);
            verify(storageService).delete(secondKey);
        }

        /*
         * 순서가 뒤집히면 조용히 망가지는 종류의 버그다. 공지를 먼저 지우면 notice_attachment도
         * FK CASCADE로 함께 사라져서, 그 뒤에 조회해도 지울 객체가 하나도 나오지 않는다.
         */
        @Test
        @DisplayName("첨부 목록을 공지 삭제보다 먼저 조회한다")
        void readsAttachmentsBeforeDeletingNotice() {
            String objectKey = "uploads/document/2026/08/23/first.pdf";
            given(noticeMapper.selectById(NOTICE_ID)).willReturn(notice());
            given(noticeMapper.selectAttachments(NOTICE_ID)).willReturn(List.of(attachment(10L, objectKey)));
            given(storageService.toObjectKey(BASE_URL + "/" + objectKey)).willReturn(Optional.of(objectKey));

            noticeAdminService.delete(NOTICE_ID);

            InOrder inOrder = inOrder(noticeMapper);
            inOrder.verify(noticeMapper).selectAttachments(NOTICE_ID);
            inOrder.verify(noticeMapper).delete(NOTICE_ID);
        }

        @Test
        @DisplayName("첨부가 없으면 스토리지를 건드리지 않는다")
        void doesNotTouchStorageWithoutAttachments() {
            given(noticeMapper.selectById(NOTICE_ID)).willReturn(notice());
            given(noticeMapper.selectAttachments(NOTICE_ID)).willReturn(List.of());

            noticeAdminService.delete(NOTICE_ID);

            verify(noticeMapper).delete(NOTICE_ID);
            verifyNoInteractions(storageService);
        }
    }

    @Nested
    @DisplayName("첨부 목록 동기화")
    class SyncAttachments {

        @Test
        @DisplayName("목록에서 빠진 첨부만 스토리지에서 지운다")
        void deletesOnlyRemovedAttachmentObject() {
            String keptKey = "uploads/document/2026/08/23/kept.pdf";
            String removedKey = "uploads/document/2026/08/23/removed.pdf";
            given(noticeMapper.selectById(NOTICE_ID)).willReturn(notice());
            given(noticeMapper.selectAttachments(NOTICE_ID))
                    .willReturn(List.of(attachment(10L, keptKey), attachment(11L, removedKey)));
            given(storageService.toObjectKey(BASE_URL + "/" + removedKey)).willReturn(Optional.of(removedKey));

            noticeAdminService.update(NOTICE_ID, keepOnly(10L));

            verify(noticeMapper).deleteAttachmentsByIds(List.of(11L));
            verify(storageService).delete(removedKey);
            verify(storageService, never()).delete(keptKey);
        }
    }

    @Nested
    @DisplayName("정리 안전장치")
    class CleanupSafety {

        /*
         * 다른 환경에서 만들어진 주소나 외부 URL은 우리 버킷의 어느 키인지 알 수 없다.
         * 억지로 지우려 들면 엉뚱한 객체를 건드릴 수 있으므로 건너뛰는 것이 맞다.
         */
        @Test
        @DisplayName("객체 키를 알 수 없는 URL은 삭제를 건너뛴다")
        void skipsUnresolvableUrl() {
            String foreignUrl = "https://other.example/uploads/document/foreign.pdf";
            given(noticeMapper.selectById(NOTICE_ID)).willReturn(notice());
            given(noticeMapper.selectAttachments(NOTICE_ID)).willReturn(List.of(NoticeAttachment.builder()
                    .attachmentId(10L)
                    .noticeId(NOTICE_ID)
                    .fileUrl(foreignUrl)
                    .originalName("외부.pdf")
                    .fileSize(1024L)
                    .sortOrder(0)
                    .build()));
            given(storageService.toObjectKey(foreignUrl)).willReturn(Optional.empty());

            noticeAdminService.delete(NOTICE_ID);

            verify(storageService, never()).delete(anyString());
        }

        /*
         * 이 시점엔 이미 커밋이 끝나 관리자 화면에서는 삭제가 완료된 상태다. 여기서 예외를
         * 올리면 "지웠는데 실패했다"는 모순된 응답이 되므로 로그만 남기고 넘어가야 한다.
         */
        @Test
        @DisplayName("스토리지 삭제가 실패해도 공지 삭제는 성공으로 끝난다")
        void survivesStorageFailure() {
            String objectKey = "uploads/document/2026/08/23/first.pdf";
            given(noticeMapper.selectById(NOTICE_ID)).willReturn(notice());
            given(noticeMapper.selectAttachments(NOTICE_ID)).willReturn(List.of(attachment(10L, objectKey)));
            given(storageService.toObjectKey(BASE_URL + "/" + objectKey)).willReturn(Optional.of(objectKey));
            willThrow(new CommonException(ErrorCode.STORAGE_UNAVAILABLE)).given(storageService).delete(objectKey);

            assertThatCode(() -> noticeAdminService.delete(NOTICE_ID)).doesNotThrowAnyException();

            verify(noticeMapper).delete(NOTICE_ID);
        }
    }
}
