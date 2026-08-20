package com.ms.petopia.api.review.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.review.dto.BoothFairBusinessRow;
import com.ms.petopia.api.review.dto.BoothFeedback;
import com.ms.petopia.api.review.dto.BoothFeedbackSubmission;
import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.dto.FeedbackTag;
import com.ms.petopia.api.review.dto.MyReviewStatusResponse;
import com.ms.petopia.api.review.dto.SubmitFairReviewRequest;
import com.ms.petopia.api.review.mapper.BoothFeedbackMapper;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.api.review.mapper.FeedbackTagMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 통합 리뷰(태그 기반) 제출 서비스. 마법사 전체(페르소나 → 행사 태그 → 부스 선택·평가 →
 * 재방문의향)를 한 트랜잭션으로 저장한다 - 별점+자유서술 리뷰(V17~V35)를 완전히 대체한다
 * (petopia-review-feature-plan 스킬 참고).
 *
 * <p>수정·삭제 API는 이번 범위에 없다 - 리뷰는 행사당 사용자 1건만 "새로 작성"할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class FairReviewService {

    private static final int MAX_BOOTH_FEEDBACKS = 3;

    private final FairReviewMapper fairReviewMapper;
    private final BoothFeedbackMapper boothFeedbackMapper;
    private final FeedbackTagMapper feedbackTagMapper;
    private final FairMapper fairMapper;

    @Transactional
    public FairReviewResponse submit(Long fairId, Long userId, SubmitFairReviewRequest request) {
        Fair fair = fairMapper.selectById(fairId);
        if (fair == null) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }
        assertFairPublished(fair);
        if (fairReviewMapper.existsByFairIdAndUserId(fairId, userId)) {
            throw new CommonException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        if (!fairReviewMapper.existsEntryRecord(fairId, userId)) {
            throw new CommonException(ErrorCode.REVIEW_VISIT_REQUIRED);
        }
        validateRequiredFields(request);

        List<BoothFeedbackSubmission> booths = request.booths() == null ? List.of() : request.booths();
        if (booths.size() > MAX_BOOTH_FEEDBACKS) {
            throw new CommonException(ErrorCode.BOOTH_FEEDBACK_LIMIT_EXCEEDED);
        }

        Set<Long> fairTagIds = distinctIds(request.fairTagIds());
        assertTagsSelectable(fairTagIds, FeedbackTag.Scope.FAIR);

        LocalDateTime now = LocalDateTime.now();
        FairReview review = new FairReview();
        review.setFairId(fairId);
        review.setUserId(userId);
        review.setCompanionType(request.companionType());
        review.setVisitPurpose(request.visitPurpose());
        review.setWouldRevisit(request.wouldRevisit());
        review.setCreatedAt(now);
        review.setUpdatedAt(now);

        try {
            fairReviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // exists-check와 실제 INSERT 사이 동시 제출이 있었을 경우의 이중 방어
            // (UNIQUE(fair_id,user_id), V39 - 이 프로젝트의 UNIQUE 제약 처리 관례).
            throw new CommonException(ErrorCode.REVIEW_ALREADY_EXISTS, e);
        }

        if (!fairTagIds.isEmpty()) {
            fairReviewMapper.insertTagSelections(review.getReviewId(), List.copyOf(fairTagIds));
        }

        Set<Long> seenBoothIds = new LinkedHashSet<>();
        for (BoothFeedbackSubmission submission : booths) {
            if (submission.boothId() == null || !seenBoothIds.add(submission.boothId())) {
                continue; // 같은 boothId가 중복 제출되면 첫 번째만 반영한다
            }
            submitBoothFeedback(fairId, userId, review.getReviewId(), now, submission);
        }

        return FairReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public MyReviewStatusResponse checkStatus(Long fairId, Long userId) {
        FairReview review = fairReviewMapper.selectByFairIdAndUserId(fairId, userId);
        if (review == null) {
            return new MyReviewStatusResponse(false, null);
        }
        return new MyReviewStatusResponse(true, review.getReviewId());
    }

    private void submitBoothFeedback(Long fairId, Long userId, Long reviewId, LocalDateTime now,
                                      BoothFeedbackSubmission submission) {
        BoothFairBusinessRow boothInfo = boothFeedbackMapper.selectFairAndBusinessByBoothId(submission.boothId());
        if (boothInfo == null) {
            throw new CommonException(ErrorCode.BOOTH_NOT_FOUND);
        }
        if (!fairId.equals(boothInfo.getFairId())) {
            throw new CommonException(ErrorCode.BOOTH_NOT_IN_FAIR);
        }
        if (!boothFeedbackMapper.existsBoothVisit(submission.boothId(), userId)) {
            throw new CommonException(ErrorCode.BOOTH_VISIT_REQUIRED);
        }

        Set<Long> boothTagIds = distinctIds(submission.tagIds());
        assertTagsSelectable(boothTagIds, FeedbackTag.Scope.BOOTH);

        BoothFeedback feedback = new BoothFeedback();
        feedback.setReviewId(reviewId);
        feedback.setBoothId(submission.boothId());
        feedback.setFairId(fairId);
        feedback.setBusinessId(boothInfo.getBusinessId());
        feedback.setUserId(userId);
        feedback.setPurchaseBehavior(submission.purchaseBehavior());
        feedback.setCreatedAt(now);
        feedback.setUpdatedAt(now);

        try {
            boothFeedbackMapper.insert(feedback);
        } catch (DuplicateKeyException e) {
            // UNIQUE(booth_id,user_id) 이중 방어 - 리뷰는 행사당 1건이라 정상 흐름에서는 거의
            // 일어나지 않지만, 동시 제출 레이스에 대비한다.
            throw new CommonException(ErrorCode.REVIEW_ALREADY_EXISTS, e);
        }

        if (!boothTagIds.isEmpty()) {
            boothFeedbackMapper.insertSelections(feedback.getBoothFeedbackId(), List.copyOf(boothTagIds));
        }
    }

    /** 클라이언트가 보낸 tagId들이 실제로 존재·활성·해당 scope인지 검증한다. */
    private void assertTagsSelectable(Set<Long> tagIds, FeedbackTag.Scope scope) {
        if (tagIds.isEmpty()) {
            return;
        }
        List<FeedbackTag> found = feedbackTagMapper.selectByIdsAndScope(List.copyOf(tagIds), scope);
        if (found.size() != tagIds.size()) {
            throw new CommonException(ErrorCode.FEEDBACK_TAG_NOT_FOUND);
        }
        boolean anyInactive = found.stream().anyMatch(tag -> !tag.isActive());
        if (anyInactive) {
            throw new CommonException(ErrorCode.FEEDBACK_TAG_INACTIVE);
        }
    }

    private Set<Long> distinctIds(List<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    /**
     * FairService#getPublicSummary/listPublicFairs와 같은 기준(published_at IS NOT NULL,
     * canceled_at IS NULL)이어야 한다 - 부스 모집중이거나 아직 준비 단계라 공개되지 않은
     * 행사, 혹은 공개 후 취소된 행사에는 리뷰를 달 수 없게 막는다.
     */
    private void assertFairPublished(Fair fair) {
        if (fair.getPublishedAt() == null || fair.getCanceledAt() != null) {
            throw new CommonException(ErrorCode.REVIEW_FAIR_NOT_PUBLISHED);
        }
    }

    private void validateRequiredFields(SubmitFairReviewRequest request) {
        if (request.companionType() == null || request.visitPurpose() == null || request.wouldRevisit() == null) {
            throw new CommonException(ErrorCode.REVIEW_REQUIRED_FIELD_MISSING);
        }
    }
}
