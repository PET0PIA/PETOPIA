package com.ms.petopia.api.notice.service;

import com.ms.petopia.api.notice.domain.Notice;
import com.ms.petopia.api.notice.domain.NoticeAttachment;
import com.ms.petopia.api.notice.domain.NoticeCategory;
import com.ms.petopia.api.notice.dto.response.NoticeDetailResponse;
import com.ms.petopia.api.notice.dto.response.NoticeListItemResponse;
import com.ms.petopia.api.notice.mapper.NoticeMapper;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;
    private final RecruitNoticeMapper recruitNoticeMapper;

    /**
     * 소식 목록. 공지(notice)와 모집중인 모집공고(recruit_notice)를 한 목록으로 합쳐 돌려준다.
     *
     * <p>모집공고를 공지 테이블에 복사하지 않는 이유는 같은 내용을 두 번 관리하지 않기 위해서다.
     * 대신 여기서 합치므로, 행사 담당자가 공고를 고치면 소식 목록도 자동으로 최신이 된다.
     *
     * <p>건수가 적어 페이징을 두지 않았다. 데이터가 늘면 두 출처를 각각 잘라 와서 합치는 방식으로
     * 바꿔야 한다 - 한쪽만 잘라 오면 목록 뒷부분이 통째로 비는 식으로 어긋난다.
     *
     * @param category null이면 전체
     */
    public List<NoticeListItemResponse> getPublicList(NoticeCategory category) {
        List<NoticeListItemResponse> items = new ArrayList<>();

        if (category == null || category.isStored()) {
            noticeMapper.selectPublishedList(category).stream()
                    .map(NoticeListItemResponse::from)
                    .forEach(items::add);
        }
        if (category == null || category == NoticeCategory.RECRUIT) {
            recruitNoticeMapper.selectNewsItems(LocalDateTime.now()).stream()
                    .map(NoticeListItemResponse::from)
                    .forEach(items::add);
        }

        // 고정 공지 먼저, 그다음 최신순. 두 출처를 합친 뒤라 정렬은 여기서 한 번 더 해야 한다.
        items.sort(Comparator.comparing(NoticeListItemResponse::isPinned).reversed()
                .thenComparing(NoticeListItemResponse::getCreatedAt, Comparator.reverseOrder()));
        return items;
    }

    /**
     * 공지 상세. 모집공고는 이 API로 오지 않는다 - 목록의 linkPath가 기존 모집공고 화면으로 보낸다.
     */
    @Transactional
    public NoticeDetailResponse getPublicDetail(Long noticeId) {
        Notice notice = noticeMapper.selectPublishedById(noticeId);
        if (notice == null) {
            throw new CommonException(ErrorCode.NOTICE_NOT_FOUND);
        }
        noticeMapper.increaseViewCount(noticeId);

        List<NoticeAttachment> attachments = noticeMapper.selectAttachments(noticeId);
        // 방금 +1 한 값이 화면에 바로 반영되도록 응답에서만 더해 준다(재조회 한 번을 아낀다).
        notice.setViewCount(notice.getViewCount() + 1);
        return NoticeDetailResponse.of(notice, attachments);
    }
}
