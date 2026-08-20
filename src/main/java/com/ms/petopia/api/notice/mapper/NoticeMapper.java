package com.ms.petopia.api.notice.mapper;

import com.ms.petopia.api.notice.domain.Notice;
import com.ms.petopia.api.notice.domain.NoticeAttachment;
import com.ms.petopia.api.notice.domain.NoticeCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoticeMapper {

    // 게시된 공지 목록 (공개 API용). category가 null이면 전체.
    List<Notice> selectPublishedList(@Param("category") NoticeCategory category);

    // 게시된 공지 단건 (공개 API용). 비공개 글은 없는 것으로 취급한다.
    Notice selectPublishedById(@Param("noticeId") Long noticeId);

    // 공지에 달린 첨부파일
    List<NoticeAttachment> selectAttachments(@Param("noticeId") Long noticeId);

    // 조회수 +1
    void increaseViewCount(@Param("noticeId") Long noticeId);

    // ===== 관리자용 (게시 여부와 무관하게 전부 본다) =====

    List<Notice> selectAllForAdmin();

    Notice selectById(@Param("noticeId") Long noticeId);

    void insert(Notice notice);

    // 폼 전체를 덮어쓴다(부분 수정이 아니다).
    void update(Notice notice);

    // 첨부는 FK ON DELETE CASCADE로 함께 지워진다.
    void delete(@Param("noticeId") Long noticeId);

    void updatePublished(@Param("noticeId") Long noticeId, @Param("isPublished") boolean isPublished);

    void updatePinned(@Param("noticeId") Long noticeId, @Param("isPinned") boolean isPinned);

    void insertAttachments(@Param("attachments") List<NoticeAttachment> attachments);

    void deleteAttachmentsByIds(@Param("attachmentIds") List<Long> attachmentIds);
}
