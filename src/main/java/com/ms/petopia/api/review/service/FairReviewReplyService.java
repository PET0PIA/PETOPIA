package com.ms.petopia.api.review.service;

import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewReply;
import com.ms.petopia.api.review.dto.FairReviewReplyRequest;
import com.ms.petopia.api.review.dto.FairReviewReplyResponse;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.api.review.mapper.FairReviewReplyMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 행사 담당자(EVENT_ADMIN/SUPER_ADMIN)의 리뷰 답글. "이 행사 담당자인지"는
 * {@link FairAdminAccessGuard}로 검증한다(HTTP 요청 전용 - 다른 도메인이 빈 주입으로 직접
 * 호출하면 안 된다는 점은 그 클래스의 주석 참고).
 */
@Service
@RequiredArgsConstructor
public class FairReviewReplyService {

    private final FairReviewReplyMapper fairReviewReplyMapper;
    private final FairReviewMapper fairReviewMapper;
    private final FairAdminAccessGuard fairAdminAccessGuard;

    /** 답글을 작성한다. 리뷰당 답글은 1개뿐이라, 이미 있으면 REVIEW_REPLY_ALREADY_EXISTS. */
    @Transactional
    public FairReviewReplyResponse create(Long fairId, Long reviewId, Long adminUserId, FairReviewReplyRequest request) {
        fairAdminAccessGuard.checkAssigned(fairId);
        validateReviewInFair(fairId, reviewId);
        validateContent(request.content());

        if (fairReviewReplyMapper.selectByReviewId(reviewId) != null) {
            throw new CommonException(ErrorCode.REVIEW_REPLY_ALREADY_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now();
        FairReviewReply reply = new FairReviewReply();
        reply.setReviewId(reviewId);
        reply.setFairId(fairId);
        reply.setAdminUserId(adminUserId);
        reply.setContent(request.content());
        reply.setCreatedAt(now);
        reply.setUpdatedAt(now);

        fairReviewReplyMapper.insert(reply);
        return FairReviewReplyResponse.from(reply);
    }

    /** 답글을 수정한다. 작성자가 아니어도 그 행사 담당자면(FairAdminAccessGuard) 수정할 수 있다 -
     * 담당자가 교체되거나 여러 명일 때 답글 하나가 특정 개인에게 묶이지 않게 하기 위해서다. */
    @Transactional
    public FairReviewReplyResponse update(Long fairId, Long reviewId, FairReviewReplyRequest request) {
        fairAdminAccessGuard.checkAssigned(fairId);
        validateReviewInFair(fairId, reviewId);
        validateContent(request.content());

        FairReviewReply reply = fairReviewReplyMapper.selectByReviewId(reviewId);
        if (reply == null) {
            throw new CommonException(ErrorCode.REVIEW_REPLY_NOT_FOUND);
        }

        reply.setContent(request.content());
        reply.setUpdatedAt(LocalDateTime.now());
        fairReviewReplyMapper.update(reply);
        return FairReviewReplyResponse.from(reply);
    }

    /** 답글을 조회한다(공개, 로그인 불필요). 아직 답글이 없으면 REVIEW_REPLY_NOT_FOUND(404) -
     * "답글 없음"을 정상 상태로 보고 프론트가 404를 잡아서 처리한다. */
    @Transactional(readOnly = true)
    public FairReviewReplyResponse get(Long fairId, Long reviewId) {
        validateReviewInFair(fairId, reviewId);
        FairReviewReply reply = fairReviewReplyMapper.selectByReviewId(reviewId);
        if (reply == null) {
            throw new CommonException(ErrorCode.REVIEW_REPLY_NOT_FOUND);
        }
        return FairReviewReplyResponse.from(reply);
    }

    private void validateReviewInFair(Long fairId, Long reviewId) {
        FairReview review = fairReviewMapper.selectById(reviewId);
        if (review == null || !review.getFairId().equals(fairId)) {
            throw new CommonException(ErrorCode.REVIEW_NOT_FOUND);
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new CommonException(ErrorCode.REVIEW_CONTENT_REQUIRED);
        }
    }
}
