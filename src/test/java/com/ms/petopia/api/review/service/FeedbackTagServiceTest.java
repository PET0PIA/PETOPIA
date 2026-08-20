package com.ms.petopia.api.review.service;

import com.ms.petopia.api.review.dto.CreateFeedbackTagRequest;
import com.ms.petopia.api.review.dto.FeedbackTag;
import com.ms.petopia.api.review.dto.FeedbackTagResponse;
import com.ms.petopia.api.review.dto.FeedbackTagUsageResponse;
import com.ms.petopia.api.review.dto.UpdateFeedbackTagRequest;
import com.ms.petopia.api.review.mapper.FeedbackTagMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * FeedbackTagService 단위 테스트. Mapper는 Mock으로 대체하고, 등록·수정·사용현황 조회의
 * 서비스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackTagServiceTest {

    private static final Long TAG_ID = 1L;

    @Mock
    private FeedbackTagMapper feedbackTagMapper;

    @InjectMocks
    private FeedbackTagService feedbackTagService;

    @Test
    @DisplayName("활성 태그 목록을 scope로 조회해 응답으로 변환한다")
    void listActive_활성태그를_응답으로_변환한다() {
        given(feedbackTagMapper.selectActiveByScope(FeedbackTag.Scope.FAIR))
                .willReturn(List.of(tag(1L, "안내가 친절해요", true)));

        List<FeedbackTagResponse> result = feedbackTagService.listActive(FeedbackTag.Scope.FAIR);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).label()).isEqualTo("안내가 친절해요");
    }

    @Test
    @DisplayName("라벨이 중복되지 않으면 태그를 등록한다")
    void create_정상등록이면_저장한다() {
        given(feedbackTagMapper.existsByScopeAndLabel(FeedbackTag.Scope.FAIR, "새 태그")).willReturn(false);

        FeedbackTagResponse response = feedbackTagService.create(
                new CreateFeedbackTagRequest(FeedbackTag.Scope.FAIR, "GUIDE_OPERATION", FeedbackTag.Sentiment.POSITIVE, "새 태그", 1)
        );

        assertThat(response.label()).isEqualTo("새 태그");
        verify(feedbackTagMapper).insert(any());
    }

    @Test
    @DisplayName("같은 scope에 이미 있는 라벨이면 FEEDBACK_TAG_LABEL_DUPLICATE를 던지고 저장하지 않는다")
    void create_라벨중복이면_예외를_던진다() {
        given(feedbackTagMapper.existsByScopeAndLabel(FeedbackTag.Scope.FAIR, "안내가 친절해요")).willReturn(true);

        assertErrorCode(
                () -> feedbackTagService.create(
                        new CreateFeedbackTagRequest(FeedbackTag.Scope.FAIR, "GUIDE_OPERATION", FeedbackTag.Sentiment.POSITIVE, "안내가 친절해요", 1)
                ),
                ErrorCode.FEEDBACK_TAG_LABEL_DUPLICATE
        );
        verify(feedbackTagMapper, never()).insert(any());
    }

    @Test
    @DisplayName("exists 확인 이후 동시 등록으로 유니크 제약을 위반하면 같은 에러코드로 변환한다")
    void create_동시등록으로_유니크제약위반되면_예외로_변환한다() {
        given(feedbackTagMapper.existsByScopeAndLabel(FeedbackTag.Scope.FAIR, "새 태그")).willReturn(false);
        willThrow(new DuplicateKeyException("UK_FEEDBACK_TAGS_SCOPE_LABEL")).given(feedbackTagMapper).insert(any());

        assertErrorCode(
                () -> feedbackTagService.create(
                        new CreateFeedbackTagRequest(FeedbackTag.Scope.FAIR, "GUIDE_OPERATION", FeedbackTag.Sentiment.POSITIVE, "새 태그", 1)
                ),
                ErrorCode.FEEDBACK_TAG_LABEL_DUPLICATE
        );
    }

    @Test
    @DisplayName("존재하지 않는 태그를 수정하면 FEEDBACK_TAG_NOT_FOUND를 던진다")
    void update_존재하지않으면_예외를_던진다() {
        given(feedbackTagMapper.selectById(TAG_ID)).willReturn(null);

        assertErrorCode(
                () -> feedbackTagService.update(TAG_ID, new UpdateFeedbackTagRequest("새 라벨", null, null)),
                ErrorCode.FEEDBACK_TAG_NOT_FOUND
        );
        verify(feedbackTagMapper, never()).update(any());
    }

    @Test
    @DisplayName("요청에서 null인 필드는 기존 값을 유지한 채 부분 수정한다")
    void update_null인필드는_기존값을_유지한다() {
        FeedbackTag existing = tag(TAG_ID, "기존 라벨", true);
        existing.setSortOrder(3);
        given(feedbackTagMapper.selectById(TAG_ID)).willReturn(existing);

        // label만 바꾸고 sortOrder·active는 요청에서 비운다
        feedbackTagService.update(TAG_ID, new UpdateFeedbackTagRequest("바뀐 라벨", null, null));

        ArgumentCaptor<FeedbackTag> captor = ArgumentCaptor.forClass(FeedbackTag.class);
        verify(feedbackTagMapper).update(captor.capture());
        assertThat(captor.getValue().getLabel()).isEqualTo("바뀐 라벨");
        assertThat(captor.getValue().getSortOrder()).isEqualTo(3); // 유지
        assertThat(captor.getValue().isActive()).isTrue(); // 유지
    }

    @Test
    @DisplayName("active=false로 수정하면 soft delete로 반영된다")
    void update_active를_false로_내리면_반영된다() {
        given(feedbackTagMapper.selectById(TAG_ID)).willReturn(tag(TAG_ID, "라벨", true));

        feedbackTagService.update(TAG_ID, new UpdateFeedbackTagRequest(null, null, false));

        ArgumentCaptor<FeedbackTag> captor = ArgumentCaptor.forClass(FeedbackTag.class);
        verify(feedbackTagMapper).update(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    @DisplayName("같은 scope에 이미 있는 라벨로 수정하면 FEEDBACK_TAG_LABEL_DUPLICATE로 변환한다")
    void update_라벨중복이면_예외로_변환한다() {
        given(feedbackTagMapper.selectById(TAG_ID)).willReturn(tag(TAG_ID, "기존 라벨", true));
        willThrow(new DuplicateKeyException("UK_FEEDBACK_TAGS_SCOPE_LABEL")).given(feedbackTagMapper).update(any());

        assertErrorCode(
                () -> feedbackTagService.update(TAG_ID, new UpdateFeedbackTagRequest("이미 있는 라벨", null, null)),
                ErrorCode.FEEDBACK_TAG_LABEL_DUPLICATE
        );
    }

    @Test
    @DisplayName("존재하지 않는 태그의 사용현황을 조회하면 FEEDBACK_TAG_NOT_FOUND를 던진다")
    void getUsage_존재하지않으면_예외를_던진다() {
        given(feedbackTagMapper.selectById(TAG_ID)).willReturn(null);

        assertErrorCode(() -> feedbackTagService.getUsage(TAG_ID), ErrorCode.FEEDBACK_TAG_NOT_FOUND);
    }

    @Test
    @DisplayName("사용 건수를 함께 조회해 응답으로 반환한다")
    void getUsage_사용건수를_반환한다() {
        given(feedbackTagMapper.selectById(TAG_ID)).willReturn(tag(TAG_ID, "라벨", true));
        given(feedbackTagMapper.countUsage(TAG_ID)).willReturn(12L);

        FeedbackTagUsageResponse response = feedbackTagService.getUsage(TAG_ID);

        assertThat(response.usageCount()).isEqualTo(12L);
        assertThat(response.active()).isTrue();
    }

    private FeedbackTag tag(Long tagId, String label, boolean active) {
        FeedbackTag tag = new FeedbackTag();
        tag.setTagId(tagId);
        tag.setScope(FeedbackTag.Scope.FAIR);
        tag.setCategory("GUIDE_OPERATION");
        tag.setSentiment(FeedbackTag.Sentiment.POSITIVE);
        tag.setLabel(label);
        tag.setSortOrder(1);
        tag.setActive(active);
        tag.setCreatedAt(LocalDateTime.now());
        tag.setUpdatedAt(LocalDateTime.now());
        return tag;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
