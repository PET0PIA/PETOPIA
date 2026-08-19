package com.ms.petopia.api.review.mapper;

import com.ms.petopia.api.review.dto.FeedbackTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * feedback_tags 테이블 매퍼. XML은 {@code mapper/review/FeedbackTagMapper.xml}에 있다.
 */
@Mapper
public interface FeedbackTagMapper {

    int insert(FeedbackTag tag);

    FeedbackTag selectById(@Param("tagId") Long tagId);

    /** 같은 scope 안에서 라벨이 이미 쓰이고 있는지(UK_FEEDBACK_TAGS_SCOPE_LABEL 사전 확인용). */
    boolean existsByScopeAndLabel(@Param("scope") FeedbackTag.Scope scope, @Param("label") String label);

    /** 활성 태그만, 카테고리·정렬순으로. 리뷰 마법사·태그 목록 조회(공개 API)에서 쓴다. */
    List<FeedbackTag> selectActiveByScope(@Param("scope") FeedbackTag.Scope scope);

    /** 리뷰 제출 시 클라이언트가 보낸 tagId들이 실제로 존재·활성·해당 scope인지 한 번에
     * 검증하기 위해 조회한다(존재하지 않거나 scope가 다른 id는 결과에서 빠진다 - 서비스
     * 계층이 요청한 개수와 비교해서 차이를 걸러낸다). */
    List<FeedbackTag> selectByIdsAndScope(@Param("tagIds") List<Long> tagIds, @Param("scope") FeedbackTag.Scope scope);

    /** label/sort_order/active만 갱신한다. scope·category·sentiment는 수정 대상이 아니다. */
    int update(FeedbackTag tag);

    /** 이 태그가 fair_review_tag_selections·booth_feedback_selections 어느 쪽이든 이미 몇 건
     * 선택됐는지(수정·비활성화 전 경고용). */
    long countUsage(@Param("tagId") Long tagId);
}
