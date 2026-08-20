package com.ms.petopia.api.review.service;

import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.review.dto.BoothFairBusinessRow;
import com.ms.petopia.api.review.dto.BoothFeedback;
import com.ms.petopia.api.review.dto.BoothFeedbackSubmission;
import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewListItemResponse;
import com.ms.petopia.api.review.dto.FairReviewListResponse;
import com.ms.petopia.api.review.dto.FairReviewListRow;
import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.dto.FairReviewSummaryResponse;
import com.ms.petopia.api.review.dto.FeedbackTag;
import com.ms.petopia.api.review.dto.MyReviewStatusResponse;
import com.ms.petopia.api.review.dto.ReviewTagLabelRow;
import com.ms.petopia.api.review.dto.SubmitFairReviewRequest;
import com.ms.petopia.api.review.mapper.BoothFeedbackMapper;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.api.review.mapper.FeedbackTagMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 통합 리뷰(태그 기반) 제출 서비스. 마법사 전체(페르소나 → 행사 태그 → 부스 선택·평가 →
 * 재방문의향)를 한 트랜잭션으로 저장한다 - 별점+자유서술 리뷰(V17~V35)를 완전히 대체한다
 * (petopia-review-feature-plan 스킬 참고).
 *
 * <p>수정 API는 없다 - 리뷰는 행사당 사용자 1건만 "새로 작성"할 수 있다. 관리자 삭제(하드
 * 삭제 + 감사 로그)만 {@link #adminDeleteReview}로 지원한다.
 */
@Service
@RequiredArgsConstructor
public class FairReviewService {

    private static final int MAX_BOOTH_FEEDBACKS = 3;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String SUPER_ADMIN_AUTHORITY = "ROLE_SUPER_ADMIN";

    private final FairReviewMapper fairReviewMapper;
    private final BoothFeedbackMapper boothFeedbackMapper;
    private final FeedbackTagMapper feedbackTagMapper;
    private final FairMapper fairMapper;
    private final FairAdminAccessGuard fairAdminAccessGuard;
    private final AuditLogService auditLogService;

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
        boolean hasVisited = fairReviewMapper.existsEntryRecord(fairId, userId);
        if (review == null) {
            return new MyReviewStatusResponse(false, null, hasVisited);
        }
        return new MyReviewStatusResponse(true, review.getReviewId(), hasVisited);
    }

    /**
     * 공개 리뷰 목록(최신순 페이지네이션). 별점+자유서술 대신 각 리뷰가 고른 scope=FAIR
     * 태그 라벨을 함께 보여준다. 목록 조회 1번 + 태그 라벨 배치 조회 1번, 총 쿼리 2번으로
     * N+1을 피한다(NotificationQueryService의 페이지네이션 방식과 동일).
     */
    @Transactional(readOnly = true)
    public FairReviewListResponse listPublic(Long fairId, int page, int size) {
        if (page < 0 || size <= 0 || size > MAX_PAGE_SIZE) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        long totalElements = fairReviewMapper.countByFairId(fairId);
        int totalPages = (int) ((totalElements + size - 1) / size);
        List<FairReviewListRow> rows = fairReviewMapper.selectListByFairId(fairId, (long) page * size, size);

        List<Long> reviewIds = rows.stream().map(FairReviewListRow::getReviewId).toList();
        Map<Long, List<String>> labelsByReviewId = reviewIds.isEmpty()
                ? Map.of()
                : fairReviewMapper.selectFairTagLabelsByReviewIds(reviewIds).stream()
                        .collect(Collectors.groupingBy(ReviewTagLabelRow::getReviewId,
                                Collectors.mapping(ReviewTagLabelRow::getLabel, Collectors.toList())));

        List<FairReviewListItemResponse> items = rows.stream()
                .map(row -> new FairReviewListItemResponse(
                        row.getReviewId(),
                        row.getNickname(),
                        row.getCompanionType(),
                        row.getVisitPurpose(),
                        row.isWouldRevisit(),
                        labelsByReviewId.getOrDefault(row.getReviewId(), List.of()),
                        row.getCreatedAt()
                ))
                .toList();

        return new FairReviewListResponse(items, page, size, totalElements, totalPages, page + 1 < totalPages);
    }

    /** 공개 리뷰 요약. 별점 평균 자리를 재방문 의향 비율이 대신한다. */
    @Transactional(readOnly = true)
    public FairReviewSummaryResponse getPublicSummary(Long fairId) {
        long reviewCount = fairReviewMapper.countByFairId(fairId);
        double revisitRate = reviewCount == 0 ? 0.0
                : (double) fairReviewMapper.countRevisitByFairId(fairId) / reviewCount;
        return new FairReviewSummaryResponse(fairId, reviewCount, revisitRate);
    }

    /**
     * 행사담당자/최고관리자가 부적절한 리뷰를 삭제한다(하드 삭제). {@code UNIQUE(fair_id,user_id)}
     * 제약이 있어 소프트 삭제로 가면 재작성 시나리오까지 별도로 신경 써야 하는데, 하드 삭제면
     * 작성자가 원할 경우 다시 작성할 수 있어 정책상 더 단순하다 - 삭제 근거는 감사 로그의
     * before 스냅샷으로 남긴다(petopia-review-feature-plan 스킬 참고).
     *
     * <p>딸린 부스 평가({@code booth_feedbacks}/{@code booth_feedback_selections})까지
     * 함께 지운다 - FK가 없는 프로젝트 관례상 참조 순서(선택 → 부스평가 → 태그선택 → 리뷰)
     * 그대로 지워야 한다.
     */
    @Transactional
    public void adminDeleteReview(Long fairId, Long reviewId) {
        fairAdminAccessGuard.checkAssigned(fairId);

        FairReview review = fairReviewMapper.selectById(reviewId);
        if (review == null || !fairId.equals(review.getFairId())) {
            throw new CommonException(ErrorCode.REVIEW_NOT_FOUND);
        }

        List<Long> boothFeedbackIds = boothFeedbackMapper.selectIdsByReviewId(reviewId);
        if (!boothFeedbackIds.isEmpty()) {
            boothFeedbackMapper.deleteSelectionsByBoothFeedbackIds(boothFeedbackIds);
            boothFeedbackMapper.deleteByIds(boothFeedbackIds);
        }
        fairReviewMapper.deleteTagSelectionsByReviewId(reviewId);
        fairReviewMapper.deleteById(reviewId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long adminUserId = (Long) authentication.getPrincipal();
        String actorRole = isSuperAdmin(authentication) ? "SUPER_ADMIN" : "EVENT_ADMIN";

        auditLogService.record(
                adminUserId,
                ActorType.ADMIN,
                actorRole,
                ActionType.REVIEW_DELETE,
                TargetType.REVIEW,
                reviewId,
                Map.of(
                        "fairId", fairId,
                        "authorUserId", review.getUserId(),
                        "companionType", review.getCompanionType(),
                        "visitPurpose", review.getVisitPurpose(),
                        "wouldRevisit", review.isWouldRevisit(),
                        "boothFeedbackCount", boothFeedbackIds.size()
                ),
                null
        );
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> SUPER_ADMIN_AUTHORITY.equals(authority.getAuthority()));
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
