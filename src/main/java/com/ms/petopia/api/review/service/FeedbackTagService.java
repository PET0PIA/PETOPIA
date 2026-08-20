package com.ms.petopia.api.review.service;

import com.ms.petopia.api.review.dto.CreateFeedbackTagRequest;
import com.ms.petopia.api.review.dto.FeedbackTag;
import com.ms.petopia.api.review.dto.FeedbackTagResponse;
import com.ms.petopia.api.review.dto.FeedbackTagUsageResponse;
import com.ms.petopia.api.review.dto.UpdateFeedbackTagRequest;
import com.ms.petopia.api.review.mapper.FeedbackTagMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 태그 마스터(feedback_tags) 관리 서비스. 태그는 잠그지 않는다 - 행사가 리뷰를 받기 시작한
 * 뒤에도 카테고리/태그 추가·수정·soft delete가 가능하다. 다만 물리 삭제 API는 없다
 * (active=false로만 내린다) - petopia-review-feature-plan 스킬 참고.
 */
@Service
@RequiredArgsConstructor
public class FeedbackTagService {

    private final FeedbackTagMapper feedbackTagMapper;

    /** 리뷰 마법사·태그 목록 화면에서 쓰는 공개 조회. 활성 태그만 카테고리·정렬순으로 내려준다. */
    @Transactional(readOnly = true)
    public List<FeedbackTagResponse> listActive(FeedbackTag.Scope scope) {
        return feedbackTagMapper.selectActiveByScope(scope).stream()
                .map(FeedbackTagResponse::from)
                .toList();
    }

    /**
     * 태그 신규 등록(SUPER_ADMIN). exists-check로 먼저 걸러내고, 그 사이 동시 등록이 있었을
     * 경우를 대비해 DuplicateKeyException도 한 번 더 잡는다(UK_FEEDBACK_TAGS_SCOPE_LABEL
     * 이중 방어 - booth_visits 등 이 프로젝트의 UNIQUE 제약 처리 관례와 동일).
     */
    @Transactional
    public FeedbackTagResponse create(CreateFeedbackTagRequest request) {
        if (feedbackTagMapper.existsByScopeAndLabel(request.scope(), request.label())) {
            throw new CommonException(ErrorCode.FEEDBACK_TAG_LABEL_DUPLICATE);
        }

        LocalDateTime now = LocalDateTime.now();
        FeedbackTag tag = new FeedbackTag();
        tag.setScope(request.scope());
        tag.setCategory(request.category());
        tag.setSentiment(request.sentiment());
        tag.setLabel(request.label());
        tag.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        tag.setActive(true);
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);

        try {
            feedbackTagMapper.insert(tag);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.FEEDBACK_TAG_LABEL_DUPLICATE, e);
        }
        return FeedbackTagResponse.from(tag);
    }

    /**
     * label·sort_order·active만 갱신한다(요청에서 null인 필드는 기존 값 유지 - 부분 수정).
     * scope·category·sentiment는 태그의 정체성이라 수정 대상이 아니다.
     */
    @Transactional
    public FeedbackTagResponse update(Long tagId, UpdateFeedbackTagRequest request) {
        FeedbackTag tag = feedbackTagMapper.selectById(tagId);
        if (tag == null) {
            throw new CommonException(ErrorCode.FEEDBACK_TAG_NOT_FOUND);
        }
        if (request.label() != null) {
            tag.setLabel(request.label());
        }
        if (request.sortOrder() != null) {
            tag.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            tag.setActive(request.active());
        }
        tag.setUpdatedAt(LocalDateTime.now());

        try {
            feedbackTagMapper.update(tag);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.FEEDBACK_TAG_LABEL_DUPLICATE, e);
        }
        return FeedbackTagResponse.from(tag);
    }

    /** 수정·비활성화 전 경고용 - 이 태그가 이미 몇 건의 리뷰/부스 평가에 쓰였는지. */
    @Transactional(readOnly = true)
    public FeedbackTagUsageResponse getUsage(Long tagId) {
        FeedbackTag tag = feedbackTagMapper.selectById(tagId);
        if (tag == null) {
            throw new CommonException(ErrorCode.FEEDBACK_TAG_NOT_FOUND);
        }
        long usageCount = feedbackTagMapper.countUsage(tagId);
        return new FeedbackTagUsageResponse(tag.getTagId(), tag.getLabel(), tag.isActive(), usageCount);
    }
}
