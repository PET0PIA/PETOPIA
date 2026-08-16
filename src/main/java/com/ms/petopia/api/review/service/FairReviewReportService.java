package com.ms.petopia.api.review.service;

import com.ms.petopia.api.review.dto.CreateFairReviewReportRequest;
import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewReport;
import com.ms.petopia.api.review.dto.FairReviewReportReason;
import com.ms.petopia.api.review.dto.FairReviewReportResponse;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.api.review.mapper.FairReviewReportMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FairReviewReportService {

    private static final int MAX_REASON_DETAIL_LENGTH = 500;

    private final FairReviewReportMapper fairReviewReportMapper;
    private final FairReviewMapper fairReviewMapper;

    /**
     * 리뷰를 신고한다. 신고 대상 리뷰가 이 fairId 소속인지 확인하고(다르면 REVIEW_NOT_FOUND),
     * 같은 사용자가 같은 리뷰를 이미 신고했으면 REVIEW_ALREADY_REPORTED를 던진다. 신고자가
     * 리뷰 작성자 본인인지는 막지 않는다 - 범위 밖(petopia-review-feature-plan 스킬 참고).
     */
    @Transactional
    public FairReviewReportResponse create(Long fairId, Long reviewId, Long reporterUserId, CreateFairReviewReportRequest request) {
        FairReview review = fairReviewMapper.selectById(reviewId);
        if (review == null || !review.getFairId().equals(fairId)) {
            throw new CommonException(ErrorCode.REVIEW_NOT_FOUND);
        }

        FairReviewReportReason reason = validateReason(request.reason());
        String reasonDetail = validateReasonDetail(reason, request.reasonDetail());

        if (fairReviewReportMapper.existsByReviewIdAndReporterUserId(reviewId, reporterUserId)) {
            throw new CommonException(ErrorCode.REVIEW_ALREADY_REPORTED);
        }

        FairReviewReport report = new FairReviewReport();
        report.setReviewId(reviewId);
        report.setReporterUserId(reporterUserId);
        report.setReason(reason.name());
        report.setReasonDetail(reasonDetail);
        report.setCreatedAt(LocalDateTime.now());

        // 위의 existsByReviewIdAndReporterUserId 조회 이후 동시에 같은 사용자가 같은 리뷰를
        // 중복 신고하면 여기서 unique 제약 위반이 날 수 있다(check-then-insert 경쟁 상태).
        // ReservationService의 중복 예약 처리와 같은 패턴으로 잡아서 같은 에러 코드로 변환한다.
        try {
            fairReviewReportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.REVIEW_ALREADY_REPORTED, e);
        }
        return FairReviewReportResponse.from(report);
    }

    private FairReviewReportReason validateReason(String reason) {
        try {
            return FairReviewReportReason.valueOf(reason);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CommonException(ErrorCode.REVIEW_INVALID_REPORT_REASON);
        }
    }

    private String validateReasonDetail(FairReviewReportReason reason, String reasonDetail) {
        if (reason == FairReviewReportReason.ETC) {
            if (reasonDetail == null || reasonDetail.isBlank()) {
                throw new CommonException(ErrorCode.REVIEW_REPORT_DETAIL_REQUIRED);
            }
            if (reasonDetail.length() > MAX_REASON_DETAIL_LENGTH) {
                throw new CommonException(ErrorCode.REVIEW_REPORT_DETAIL_TOO_LONG);
            }
            return reasonDetail;
        }
        // ETC가 아니면 상세 사유는 저장하지 않는다(V31: reason=ETC일 때만 채워짐).
        return null;
    }
}
