package com.ms.petopia.api.review.mapper;

import com.ms.petopia.api.review.dto.FairReviewReply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * fair_review_replies 테이블 매퍼. XML은 {@code mapper/review/FairReviewReplyMapper.xml}에 있다.
 * 리뷰 하나당 답글은 1개뿐이라(V32 UNIQUE(review_id)) 목록·페이징 메서드가 없다.
 */
@Mapper
public interface FairReviewReplyMapper {

    int insert(FairReviewReply reply);

    FairReviewReply selectByReviewId(@Param("reviewId") Long reviewId);

    /** content·updated_at만 갱신한다. review_id·fair_id·admin_user_id는 수정 대상이 아니다. */
    int update(FairReviewReply reply);

    /** 리뷰 삭제 시 이 리뷰의 답글을 함께 지운다(이 프로젝트는 FK를 쓰지 않는다). */
    int deleteByReviewId(@Param("reviewId") Long reviewId);
}
