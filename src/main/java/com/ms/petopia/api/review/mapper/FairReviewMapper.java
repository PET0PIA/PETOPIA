package com.ms.petopia.api.review.mapper;

import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewListRow;
import com.ms.petopia.api.review.dto.FairReviewSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * fair_reviews 테이블 매퍼. XML은 {@code mapper/review/FairReviewMapper.xml}에 있다.
 *
 * <p>순수 데이터 접근 메서드만 둔다 - "평점 범위 검증", "본인 리뷰인지 확인" 같은 업무
 * 규칙은 여기가 아니라 서비스 계층(FairReviewService, 추후 작업)이 담당한다.
 */
@Mapper
public interface FairReviewMapper {

    int insert(FairReview review);

    FairReview selectById(@Param("reviewId") Long reviewId);

    /** 한 행사의 리뷰 목록. 최신순 정렬·페이징은 XML에서 처리한다. */
    List<FairReview> selectByFairId(@Param("fairId") Long fairId,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** 한 행사의 전체 리뷰 개수(페이징 UI용). */
    long countByFairId(@Param("fairId") Long fairId);

    /** 한 행사의 리뷰 목록을 작성자 닉네임과 함께 조회한다(공개 목록 화면용). users 테이블과
     * JOIN한다는 점이 {@link #selectByFairId}와의 차이다. 최신순 정렬·페이징은 XML에서 처리한다. */
    List<FairReviewListRow> selectListByFairId(@Param("fairId") Long fairId,
                                                @Param("offset") long offset,
                                                @Param("limit") int limit);

    /** 한 행사의 평점 요약(평균·개수). 리뷰가 하나도 없으면 averageRating은 null로 내려온다. */
    FairReviewSummaryRow selectSummaryByFairId(@Param("fairId") Long fairId);

    /** 한 사용자가 쓴 리뷰 목록(마이페이지 "내 리뷰"용). 최신순 정렬·페이징은 XML에서 처리한다. */
    List<FairReview> selectByUserId(@Param("userId") Long userId,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** 한 사용자가 쓴 전체 리뷰 개수(페이징 UI용). */
    long countByUserId(@Param("userId") Long userId);

    /**
     * rating/content만 갱신한다. fair_id·user_id·verified_visit(작성 시점 스냅샷)은
     * 수정 대상이 아니다.
     */
    int update(FairReview review);

    int deleteById(@Param("reviewId") Long reviewId);
}
