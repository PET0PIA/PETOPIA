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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FairReviewReplyServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long OTHER_FAIR_ID = 20L;
    private static final Long REVIEW_ID = 100L;
    private static final Long ADMIN_USER_ID = 1L;

    @Mock
    private FairReviewReplyMapper fairReviewReplyMapper;
    @Mock
    private FairReviewMapper fairReviewMapper;
    @Mock
    private FairAdminAccessGuard fairAdminAccessGuard;

    @InjectMocks
    private FairReviewReplyService fairReviewReplyService;

    // ===== create =====

    @Test
    @DisplayName("담당자면 답글을 작성할 수 있다")
    void create_정상적으로_작성한다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairReviewReplyMapper.selectByReviewId(REVIEW_ID)).willReturn(null);

        FairReviewReplyResponse response = fairReviewReplyService.create(
                FAIR_ID, REVIEW_ID, ADMIN_USER_ID, new FairReviewReplyRequest("감사합니다")
        );

        assertThat(response.content()).isEqualTo("감사합니다");
        verify(fairReviewReplyMapper).insert(any());
    }

    @Test
    @DisplayName("담당자가 아니면 FairAdminAccessGuard의 예외가 그대로 전파되고 저장하지 않는다")
    void create_담당자아니면_예외가_전파된다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED)).given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertErrorCode(
                () -> fairReviewReplyService.create(FAIR_ID, REVIEW_ID, ADMIN_USER_ID, new FairReviewReplyRequest("감사합니다")),
                ErrorCode.ACCESS_DENIED
        );
        verify(fairReviewReplyMapper, never()).insert(any());
    }

    @Test
    @DisplayName("리뷰가 다른 행사 소속이면 REVIEW_NOT_FOUND를 던진다")
    void create_다른행사소속이면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());

        assertErrorCode(
                () -> fairReviewReplyService.create(OTHER_FAIR_ID, REVIEW_ID, ADMIN_USER_ID, new FairReviewReplyRequest("감사합니다")),
                ErrorCode.REVIEW_NOT_FOUND
        );
        verify(fairReviewReplyMapper, never()).insert(any());
    }

    @Test
    @DisplayName("내용이 없으면 REVIEW_CONTENT_REQUIRED를 던진다")
    void create_내용없으면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());

        assertErrorCode(
                () -> fairReviewReplyService.create(FAIR_ID, REVIEW_ID, ADMIN_USER_ID, new FairReviewReplyRequest("  ")),
                ErrorCode.REVIEW_CONTENT_REQUIRED
        );
    }

    @Test
    @DisplayName("내용이 1000자를 초과하면 REVIEW_REPLY_CONTENT_TOO_LONG을 던진다")
    void create_내용이_길면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        String tooLong = "가".repeat(1001);

        assertErrorCode(
                () -> fairReviewReplyService.create(FAIR_ID, REVIEW_ID, ADMIN_USER_ID, new FairReviewReplyRequest(tooLong)),
                ErrorCode.REVIEW_REPLY_CONTENT_TOO_LONG
        );
        verify(fairReviewReplyMapper, never()).insert(any());
    }

    @Test
    @DisplayName("이미 답글이 있으면 REVIEW_REPLY_ALREADY_EXISTS를 던진다")
    void create_이미답글있으면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairReviewReplyMapper.selectByReviewId(REVIEW_ID)).willReturn(existingReply());

        assertErrorCode(
                () -> fairReviewReplyService.create(FAIR_ID, REVIEW_ID, ADMIN_USER_ID, new FairReviewReplyRequest("감사합니다")),
                ErrorCode.REVIEW_REPLY_ALREADY_EXISTS
        );
        verify(fairReviewReplyMapper, never()).insert(any());
    }

    @Test
    @DisplayName("동시 작성으로 unique 제약을 위반하면 REVIEW_REPLY_ALREADY_EXISTS로 변환한다")
    void create_동시작성이면_예외로_변환한다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairReviewReplyMapper.selectByReviewId(REVIEW_ID)).willReturn(null);
        willThrow(new DuplicateKeyException("duplicate")).given(fairReviewReplyMapper).insert(any());

        assertErrorCode(
                () -> fairReviewReplyService.create(FAIR_ID, REVIEW_ID, ADMIN_USER_ID, new FairReviewReplyRequest("감사합니다")),
                ErrorCode.REVIEW_REPLY_ALREADY_EXISTS
        );
    }

    // ===== update =====

    @Test
    @DisplayName("담당자면 답글을 수정할 수 있다")
    void update_정상적으로_수정한다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairReviewReplyMapper.selectByReviewId(REVIEW_ID)).willReturn(existingReply());

        FairReviewReplyResponse response = fairReviewReplyService.update(
                FAIR_ID, REVIEW_ID, new FairReviewReplyRequest("수정한 답글")
        );

        assertThat(response.content()).isEqualTo("수정한 답글");
        verify(fairReviewReplyMapper).update(any());
    }

    @Test
    @DisplayName("답글이 없으면 REVIEW_REPLY_NOT_FOUND를 던진다")
    void update_답글이없으면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairReviewReplyMapper.selectByReviewId(REVIEW_ID)).willReturn(null);

        assertErrorCode(
                () -> fairReviewReplyService.update(FAIR_ID, REVIEW_ID, new FairReviewReplyRequest("수정한 답글")),
                ErrorCode.REVIEW_REPLY_NOT_FOUND
        );
        verify(fairReviewReplyMapper, never()).update(any());
    }

    @Test
    @DisplayName("내용이 1000자를 초과하면 REVIEW_REPLY_CONTENT_TOO_LONG을 던진다")
    void update_내용이_길면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        String tooLong = "가".repeat(1001);

        assertErrorCode(
                () -> fairReviewReplyService.update(FAIR_ID, REVIEW_ID, new FairReviewReplyRequest(tooLong)),
                ErrorCode.REVIEW_REPLY_CONTENT_TOO_LONG
        );
        verify(fairReviewReplyMapper, never()).update(any());
    }

    // ===== get =====

    @Test
    @DisplayName("답글이 있으면 조회할 수 있다")
    void get_정상적으로_조회한다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairReviewReplyMapper.selectByReviewId(REVIEW_ID)).willReturn(existingReply());

        FairReviewReplyResponse response = fairReviewReplyService.get(FAIR_ID, REVIEW_ID);

        assertThat(response.content()).isEqualTo("기존 답글");
    }

    @Test
    @DisplayName("답글이 없으면 REVIEW_REPLY_NOT_FOUND를 던진다")
    void get_답글이없으면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairReviewReplyMapper.selectByReviewId(REVIEW_ID)).willReturn(null);

        assertErrorCode(
                () -> fairReviewReplyService.get(FAIR_ID, REVIEW_ID),
                ErrorCode.REVIEW_REPLY_NOT_FOUND
        );
    }

    @Test
    @DisplayName("리뷰가 다른 행사 소속이면 REVIEW_NOT_FOUND를 던진다")
    void get_다른행사소속이면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());

        assertErrorCode(
                () -> fairReviewReplyService.get(OTHER_FAIR_ID, REVIEW_ID),
                ErrorCode.REVIEW_NOT_FOUND
        );
    }

    // ===== fixtures =====

    private FairReview existingReview() {
        FairReview review = new FairReview();
        review.setReviewId(REVIEW_ID);
        review.setFairId(FAIR_ID);
        review.setUserId(2L);
        review.setRating(5);
        review.setContent("기존 리뷰");
        return review;
    }

    private FairReviewReply existingReply() {
        FairReviewReply reply = new FairReviewReply();
        reply.setReviewReplyId(1000L);
        reply.setReviewId(REVIEW_ID);
        reply.setFairId(FAIR_ID);
        reply.setAdminUserId(ADMIN_USER_ID);
        reply.setContent("기존 답글");
        reply.setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        reply.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        return reply;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
