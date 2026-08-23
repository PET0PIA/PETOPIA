package com.ms.petopia.api.notice.service;

import com.ms.petopia.api.notice.domain.Notice;
import com.ms.petopia.api.notice.domain.NoticeAttachment;
import com.ms.petopia.api.notice.dto.request.NoticeAttachmentRequest;
import com.ms.petopia.api.notice.dto.request.NoticeSaveRequest;
import com.ms.petopia.api.notice.dto.response.NoticeAdminResponse;
import com.ms.petopia.api.notice.mapper.NoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 최고 관리자용 공지 관리. 공개 조회는 {@link NoticeService}가 담당한다.
 *
 * <p>경로가 {@code /api/admin/**}라 SecurityConfig에서 SUPER_ADMIN으로 이미 잠겨 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeAdminService {

    /** 한 공지에 붙일 수 있는 첨부파일 수. */
    private static final int MAX_ATTACHMENTS = 5;

    private final NoticeMapper noticeMapper;
    private final StorageService storageService;

    public List<NoticeAdminResponse> getAll() {
        return noticeMapper.selectAllForAdmin().stream()
                .map(notice -> NoticeAdminResponse.of(notice, noticeMapper.selectAttachments(notice.getNoticeId())))
                .toList();
    }

    public NoticeAdminResponse getById(Long noticeId) {
        Notice notice = findOrThrow(noticeId);
        return NoticeAdminResponse.of(notice, noticeMapper.selectAttachments(noticeId));
    }

    @Transactional
    public NoticeAdminResponse create(Long callerId, NoticeSaveRequest request) {
        Notice notice = Notice.builder()
                .category(request.getCategory())
                .title(request.getTitle().trim())
                .content(request.getContent())
                .fairId(request.getFairId())
                // 생략하면 바로 게시한다(초안으로 저장하려면 published=false를 명시).
                .isPublished(request.getPublished() == null || request.getPublished())
                .isPinned(request.isPinned())
                .createdBy(callerId)
                .build();
        noticeMapper.insert(notice);

        syncAttachments(notice.getNoticeId(), request.getAttachments());
        return getById(notice.getNoticeId());
    }

    @Transactional
    public NoticeAdminResponse update(Long noticeId, NoticeSaveRequest request) {
        findOrThrow(noticeId);

        Notice patch = Notice.builder()
                .noticeId(noticeId)
                .category(request.getCategory())
                .title(request.getTitle().trim())
                .content(request.getContent())
                .fairId(request.getFairId())
                .isPinned(request.isPinned())
                .build();
        noticeMapper.update(patch);
        // 게시 여부는 보냈을 때만 반영한다(안 보냈으면 지금 상태 유지).
        if (request.getPublished() != null) {
            noticeMapper.updatePublished(noticeId, request.getPublished());
        }

        syncAttachments(noticeId, request.getAttachments());
        return getById(noticeId);
    }

    @Transactional
    public void delete(Long noticeId) {
        findOrThrow(noticeId);
        // 첨부 URL을 먼저 확보한다 - 공지를 지우면 notice_attachment 행도 FK CASCADE로 함께
        // 사라져서, 그 뒤에는 어떤 파일을 지워야 하는지 알아낼 방법이 없다.
        List<String> attachmentUrls = noticeMapper.selectAttachments(noticeId).stream()
                .map(NoticeAttachment::getFileUrl)
                .toList();
        noticeMapper.delete(noticeId);
        // 본문 에디터 이미지는 어느 공지 것인지 기록이 없어 여기서는 지울 수 없다(별도 정리 과제).
        deleteStoredObjectsAfterCommit(attachmentUrls);
    }

    @Transactional
    public void setPublished(Long noticeId, boolean published) {
        findOrThrow(noticeId);
        noticeMapper.updatePublished(noticeId, published);
    }

    @Transactional
    public void setPinned(Long noticeId, boolean pinned) {
        findOrThrow(noticeId);
        noticeMapper.updatePinned(noticeId, pinned);
    }

    /**
     * 본문 에디터에 넣을 이미지를 즉시 확정하고 공개 URL을 돌려준다.
     *
     * <p>배너·팝업은 "저장할 때 한꺼번에 확정"하지만, 본문 HTML 안에 섞이는 이미지는 에디터가
     * 지금 당장 보여줄 주소를 필요로 해서 업로드 직후 확정한다. 그래서 공지를 저장하지 않고
     * 창을 닫으면 S3에 파일이 남는다 - 관리자 전용 화면이라 감수하고, 정리는 별도 과제로 둔다.
     */
    public String confirmContentImage(String temporaryObjectKey) {
        String confirmedKey = storageService.confirm(temporaryObjectKey.trim(), UploadPolicy.IMAGE);
        return storageService.toPublicUrl(confirmedKey);
    }

    /**
     * 첨부 목록을 요청한 상태로 맞춘다. 요청에 없는 기존 첨부는 지우고, 새로 올라온 것만 확정해
     * 뒤에 붙인다. 기존 첨부의 순서는 건드리지 않는다(화면에 순서 바꾸기 기능이 없다).
     */
    private void syncAttachments(Long noticeId, List<NoticeAttachmentRequest> requested) {
        List<NoticeAttachmentRequest> items = requested == null ? List.of() : requested;
        if (items.size() > MAX_ATTACHMENTS) {
            throw new CommonException(ErrorCode.NOTICE_ATTACHMENT_LIMIT);
        }

        List<NoticeAttachment> existing = noticeMapper.selectAttachments(noticeId);
        Set<Long> existingIds = existing.stream().map(NoticeAttachment::getAttachmentId).collect(Collectors.toSet());
        Set<Long> keepIds = items.stream()
                .map(NoticeAttachmentRequest::getAttachmentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // 남의 공지 첨부 id를 끼워 넣어 남기려는 요청은 거른다.
        if (!existingIds.containsAll(keepIds)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "이 공지의 첨부파일이 아닙니다.");
        }

        List<NoticeAttachment> removed = existing.stream()
                .filter(attachment -> !keepIds.contains(attachment.getAttachmentId()))
                .toList();
        if (!removed.isEmpty()) {
            noticeMapper.deleteAttachmentsByIds(removed.stream().map(NoticeAttachment::getAttachmentId).toList());
            // 행을 지우기 전에 이미 URL을 들고 있어서 재조회가 필요 없다.
            deleteStoredObjectsAfterCommit(removed.stream().map(NoticeAttachment::getFileUrl).toList());
        }

        int nextOrder = existing.stream()
                .filter(attachment -> keepIds.contains(attachment.getAttachmentId()))
                .mapToInt(NoticeAttachment::getSortOrder)
                .max()
                .orElse(-1) + 1;

        List<NoticeAttachment> added = new ArrayList<>();
        for (NoticeAttachmentRequest item : items) {
            if (item.getAttachmentId() != null) {
                continue;
            }
            String confirmedKey = storageService.confirm(item.getObjectKey().trim(), UploadPolicy.DOCUMENT);
            added.add(NoticeAttachment.builder()
                    .noticeId(noticeId)
                    .fileUrl(storageService.toPublicUrl(confirmedKey))
                    .originalName(item.getOriginalName().trim())
                    .fileSize(item.getFileSize())
                    .sortOrder(nextOrder++)
                    .build());
        }
        if (!added.isEmpty()) {
            noticeMapper.insertAttachments(added);
        }
    }

    /**
     * 커밋이 끝난 뒤 스토리지 객체를 지운다.
     *
     * <p>트랜잭션 안에서 먼저 지우면 뒤늦게 롤백됐을 때 DB에는 첨부가 살아 있는데 파일만
     * 사라진다 - 화면에 깨진 링크가 남고 되돌릴 방법도 없다. 그래서 커밋 후로 미룬다.
     * 트랜잭션 밖에서 호출되면 즉시 지운다(ChatEventPublisher가 쓰는 방식과 같다).
     */
    private void deleteStoredObjectsAfterCommit(List<String> fileUrls) {
        if (fileUrls.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteStoredObjects(fileUrls);
                }
            });
            return;
        }
        deleteStoredObjects(fileUrls);
    }

    /**
     * 첨부 URL에 해당하는 객체를 지운다. 같은 객체를 두 번 지워도 안전하다
     * (S3 DeleteObject는 없는 키에도 성공한다).
     *
     * <p>확정 객체는 첨부 한 건마다 새 UUID 키로 만들어지므로(ObjectKeyGenerator) 두 공지가
     * 같은 객체를 가리키는 일이 없다. 그래서 "아직 다른 공지가 쓰는지" 확인할 필요가 없다.
     */
    private void deleteStoredObjects(List<String> fileUrls) {
        for (String fileUrl : fileUrls) {
            storageService.toObjectKey(fileUrl).ifPresentOrElse(
                    this::deleteQuietly,
                    () -> log.warn("공지 첨부 URL에서 객체 키를 알 수 없어 삭제를 건너뜁니다. fileUrl={}", fileUrl));
        }
    }

    /**
     * 삭제 실패를 예외로 올리지 않는다. 이 시점엔 커밋이 끝나 관리자 화면에서는 이미 삭제가
     * 완료된 상태라, 여기서 터뜨리면 "지웠는데 실패했다"는 모순된 응답이 된다. 남은 객체는
     * 로그로 추적한다(자동 재시도는 별도 과제).
     */
    private void deleteQuietly(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (Exception e) {
            log.error("공지 첨부 객체 삭제에 실패해 스토리지에 남습니다. objectKey={}", objectKey, e);
        }
    }

    private Notice findOrThrow(Long noticeId) {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new CommonException(ErrorCode.NOTICE_NOT_FOUND);
        }
        return notice;
    }
}
