package com.ms.petopia.api.review.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.review.dto.CreateFairReviewRequest;
import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.dto.UpdateFairReviewRequest;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.api.review.mapper.FairReviewReplyMapper;
import com.ms.petopia.api.review.mapper.FairReviewReportMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FairReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final FairReviewMapper fairReviewMapper;
    private final FairMapper fairMapper;
    private final FairReviewReportMapper fairReviewReportMapper;
    private final FairReviewReplyMapper fairReviewReplyMapper;

    /**
     * 리뷰를 작성한다. 로그인만 하면 누구나 작성할 수 있다 - 예매·방문 여부로 작성 자체를
     * 막지 않는다(petopia-review-feature-plan 스킬 참고).
     *
     * <p>is_verified_visit은 지금은 항상 false로 저장한다. 실제 예매·방문 이력을 판단하려면
     * Reservation 도메인 데이터가 필요한데, 아직 그쪽에 조회용 내부 계약 API가 없다. 그 API가
     * 준비되면 이 자리에서 실제 값으로 채운다.
     */
    @Transactional
    public FairReviewResponse create(Long fairId, Long userId, CreateFairReviewRequest request) {
        Fair fair = fairMapper.selectById(fairId);
        if (fair == null) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }
        validateRating(request.rating());
        validateContent(request.content());

        LocalDateTime now = LocalDateTime.now();
        FairReview review = new FairReview();
        review.setFairId(fairId);
        review.setUserId(userId);
        review.setRating(request.rating());
        review.setContent(request.content());
        review.setVerifiedVisit(false); // TODO Reservation 내부 계약 API가 준비되면 실제 방문 이력으로 판단
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        review.setVersion(0L); // DB 컬럼 기본값(0)과 맞춘다 - insert 매퍼는 review_id만 채워 돌려주므로 여기서 직접 설정

        fairReviewMapper.insert(review);
        return FairReviewResponse.from(review);
    }

    /**
     * 리뷰를 수정한다. rating·content만 바뀐다 - fair_id·user_id·verified_visit(작성 시점
     * 스냅샷)은 수정 대상이 아니다. 본인이 작성한 리뷰만 수정할 수 있다.
     *
     * <p>낙관적 락(V35): {@code request.version()}은 클라이언트가 조회 시점에 받은 버전이어야
     * 한다. 같은 리뷰를 두 곳에서 동시에 수정하면 먼저 커밋된 쪽만 반영되고, 나중 요청은
     * 버전이 이미 바뀐 상태라 REVIEW_VERSION_CONFLICT(409)로 거부된다.
     */
    @Transactional
    public FairReviewResponse update(Long fairId, Long reviewId, Long userId, UpdateFairReviewRequest request) {
        FairReview review = getOwnedReview(fairId, reviewId, userId);
        validateRating(request.rating());
        validateContent(request.content());
        if (request.version() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        review.setRating(request.rating());
        review.setContent(request.content());
        review.setUpdatedAt(LocalDateTime.now());
        review.setVersion(request.version()); // WHERE 절에 실릴 기대 버전

        int updated = fairReviewMapper.update(review);
        if (updated == 0) {
            throw new CommonException(ErrorCode.REVIEW_VERSION_CONFLICT);
        }
        review.setVersion(request.version() + 1); // 응답에는 반영 후 새 버전을 담아준다
        return FairReviewResponse.from(review);
    }

    /**
     * 리뷰를 삭제한다. 본인이 작성한 리뷰만 삭제할 수 있다.
     *
     * <p>이 프로젝트는 FK 제약을 쓰지 않으므로, 이 리뷰를 참조하는 신고(fair_review_reports)·
     * 답글(fair_review_replies)을 애플리케이션 레이어에서 같은 트랜잭션 안에 먼저 지운다 -
     * 안 그러면 리뷰만 사라지고 참조가 끊긴 신고·답글 행이 고아 데이터로 남는다.
     */
    @Transactional
    public void delete(Long fairId, Long reviewId, Long userId) {
        getOwnedReview(fairId, reviewId, userId);
        fairReviewReportMapper.deleteByReviewId(reviewId);
        fairReviewReplyMapper.deleteByReviewId(reviewId);
        fairReviewMapper.deleteById(reviewId);
    }

    /**
     * reviewId로 리뷰를 조회하고, 그 fairId·userId가 경로·호출자와 일치하는지 확인한다.
     * fairId가 다르면(다른 행사 리뷰를 잘못된 경로로 가리킨 경우) 존재 여부를 굳이 드러내지
     * 않고 NOT_FOUND로 처리한다 - ReservationMapper#selectReservationForOwner가 조회
     * 조건 자체에 소유자 스코프를 거는 것과 같은 취지다. 작성자 본인이 아니면 ACCESS_DENIED.
     */
    private FairReview getOwnedReview(Long fairId, Long reviewId, Long userId) {
        FairReview review = fairReviewMapper.selectById(reviewId);
        if (review == null || !review.getFairId().equals(fairId)) {
            throw new CommonException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (!review.getUserId().equals(userId)) {
            throw new CommonException(ErrorCode.REVIEW_ACCESS_DENIED);
        }
        return review;
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new CommonException(ErrorCode.REVIEW_INVALID_RATING);
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new CommonException(ErrorCode.REVIEW_CONTENT_REQUIRED);
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new CommonException(ErrorCode.REVIEW_CONTENT_TOO_LONG);
        }
    }
}
