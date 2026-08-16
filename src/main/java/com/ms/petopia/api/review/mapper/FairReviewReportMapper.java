package com.ms.petopia.api.review.mapper;

import com.ms.petopia.api.review.dto.FairReviewReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * fair_review_reports 테이블 매퍼. XML은 {@code mapper/review/FairReviewReportMapper.xml}에 있다.
 *
 * <p>중복 신고는 insert 전에 {@link #existsByReviewIdAndReporterUserId}로 먼저 확인해서
 * REVIEW_ALREADY_REPORTED로 안내한다(existsActiveReservation·isAssignedEventAdmin과 같은
 * check-then-insert 패턴). UNIQUE(review_id, reporter_user_id) 제약(V31)이 최종 방어선이다.
 */
@Mapper
public interface FairReviewReportMapper {

    int insert(FairReviewReport report);

    boolean existsByReviewIdAndReporterUserId(
            @Param("reviewId") Long reviewId,
            @Param("reporterUserId") Long reporterUserId
    );
}
