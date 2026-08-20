package com.ms.petopia.api.review.mapper;

import com.ms.petopia.api.review.dto.BoothFairBusinessRow;
import com.ms.petopia.api.review.dto.BoothFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * booth_feedbacks / booth_feedback_selections 테이블 매퍼. XML은
 * {@code mapper/review/BoothFeedbackMapper.xml}에 있다.
 */
@Mapper
public interface BoothFeedbackMapper {

    /** booth_id로 그 부스가 속한 fair_id·business_id를 조회한다(booth -> application 조인).
     * 부스가 없으면 null. */
    BoothFairBusinessRow selectFairAndBusinessByBoothId(@Param("boothId") Long boothId);

    /** 이 사용자가 이 부스를 실제로 방문한 기록(booth_visits)이 있는지. */
    boolean existsBoothVisit(@Param("boothId") Long boothId, @Param("userId") Long userId);

    int insert(BoothFeedback feedback);

    void insertSelections(@Param("boothFeedbackId") Long boothFeedbackId, @Param("tagIds") List<Long> tagIds);

    // ===== 관리자 리뷰 삭제(딸린 부스 평가까지 연쇄 삭제) =====

    /** 리뷰 삭제 시 함께 지울 부스 평가 id 목록. 없으면 빈 리스트. */
    List<Long> selectIdsByReviewId(@Param("reviewId") Long reviewId);

    /** boothFeedbackIds가 비어있으면 호출하지 않는다(서비스 계층에서 가드, insertSelections와
     * 동일한 관례). */
    void deleteSelectionsByBoothFeedbackIds(@Param("boothFeedbackIds") List<Long> boothFeedbackIds);

    void deleteByIds(@Param("boothFeedbackIds") List<Long> boothFeedbackIds);
}
