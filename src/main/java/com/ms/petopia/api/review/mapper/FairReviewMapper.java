package com.ms.petopia.api.review.mapper;

import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.LabeledCountRow;
import com.ms.petopia.api.review.dto.TagCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * fair_reviews / fair_review_tag_selections 테이블 매퍼. XML은
 * {@code mapper/review/FairReviewMapper.xml}에 있다.
 */
@Mapper
public interface FairReviewMapper {

    int insert(FairReview review);

    /** 행사당 리뷰 1건 제한(UNIQUE(fair_id,user_id)) 사전 확인용. */
    boolean existsByFairIdAndUserId(@Param("fairId") Long fairId, @Param("userId") Long userId);

    /** 이 사용자가 이 행사에 실제로 입장(게이트 스캔)한 기록이 있는지. 예약만 하고 오지 않은
     * 경우를 걸러내기 위해 reservations가 아니라 entry_records를 본다 - entry_records는
     * QR이 게이트에서 실제로 스캔됐을 때만 생긴다. */
    boolean existsEntryRecord(@Param("fairId") Long fairId, @Param("userId") Long userId);

    /** "이미 작성했는지" 확인 API가 reviewId까지 내려주기 위해 쓴다. 없으면 null. */
    FairReview selectByFairIdAndUserId(@Param("fairId") Long fairId, @Param("userId") Long userId);

    /** 리뷰가 고른 scope=FAIR 태그들을 한 번에 저장한다(다중행 INSERT). tagIds가 비어있으면
     * 호출하지 않는다(서비스 계층에서 가드). */
    void insertTagSelections(@Param("reviewId") Long reviewId, @Param("tagIds") List<Long> tagIds);

    // ===== 행사관리자 통계(Phase 6) =====

    long countByFairId(@Param("fairId") Long fairId);

    long countRevisitByFairId(@Param("fairId") Long fairId);

    List<LabeledCountRow> selectCompanionTypeDistribution(@Param("fairId") Long fairId);

    List<LabeledCountRow> selectVisitPurposeDistribution(@Param("fairId") Long fairId);

    /** scope=FAIR 태그별 선택 건수. 카테고리·긍정부정 TOP5 계산 재료 - 정렬·자르기는
     * FairReviewStatsService에서 한다. */
    List<TagCountRow> selectFairTagCounts(@Param("fairId") Long fairId);
}
