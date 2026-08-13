package com.ms.petopia.api.review.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.review.dto.CreateFairReviewRequest;
import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
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

    private final FairReviewMapper fairReviewMapper;
    private final FairMapper fairMapper;

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

        fairReviewMapper.insert(review);
        return FairReviewResponse.from(review);
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
    }
}
