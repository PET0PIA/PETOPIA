package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.domain.UserToken;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "test@petopia.com";
    private static final String PURPOSE = "EMAIL_VERIFY";
    private static final Long USER_ID = 1L;
    private static final Long TOKEN_ID = 10L;
    private static final int MAX_ATTEMPTS = 5;

    @Mock
    private AuthMapper authMapper;
    @Mock
    private MailService mailService;
    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Test
    void verify_코드가일치하면_토큰사용처리하고_이메일인증완료한다() {
        String rawToken = "ABC123";
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(user());
        given(authMapper.selectActiveUserToken(USER_ID, PURPOSE))
                .willReturn(activeToken(TokenHashUtil.sha256(rawToken)));
        given(authMapper.markUserTokenUsed(TOKEN_ID)).willReturn(1);

        emailVerificationService.verify(EMAIL, rawToken);

        verify(authMapper).markUserTokenUsed(TOKEN_ID);
        verify(authMapper).markEmailVerified(USER_ID);
        verify(authMapper, never()).recordFailedAttempt(anyLong(), anyInt());
    }

    @Test
    void verify_동시요청으로_먼저처리된경우_TOKEN_ALREADY_USED를던지고_인증완료처리하지않는다() {
        String rawToken = "ABC123";
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(user());
        given(authMapper.selectActiveUserToken(USER_ID, PURPOSE))
                .willReturn(activeToken(TokenHashUtil.sha256(rawToken)));
        //AND used_at IS NULL 가드에 걸려 0건 갱신 - 경쟁에서 진 요청을 흉내
        given(authMapper.markUserTokenUsed(TOKEN_ID)).willReturn(0);

        assertThatThrownBy(() -> emailVerificationService.verify(EMAIL, rawToken))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_ALREADY_USED);

        verify(authMapper, never()).markEmailVerified(anyLong());
    }

    @Test
    void verify_코드가틀리면_실패횟수를기록하고_INVALID_TOKEN을던진다() {
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(user());
        given(authMapper.selectActiveUserToken(USER_ID, PURPOSE))
                .willReturn(activeToken(TokenHashUtil.sha256("ABC123")));

        assertThatThrownBy(() -> emailVerificationService.verify(EMAIL, "WRONG1"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(authMapper).recordFailedAttempt(TOKEN_ID, MAX_ATTEMPTS);
        verify(authMapper, never()).markEmailVerified(anyLong());
    }

    @Test
    void verify_활성토큰이없으면_INVALID_TOKEN을던진다() {
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(user());
        given(authMapper.selectActiveUserToken(USER_ID, PURPOSE)).willReturn(null);

        assertThatThrownBy(() -> emailVerificationService.verify(EMAIL, "ANYCODE"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void verify_토큰이만료됐으면_TOKEN_EXPIRED를던지고_실패횟수는기록하지않는다() {
        UserToken expiredToken = activeToken(TokenHashUtil.sha256("ABC123"));
        expiredToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(user());
        given(authMapper.selectActiveUserToken(USER_ID, PURPOSE)).willReturn(expiredToken);

        assertThatThrownBy(() -> emailVerificationService.verify(EMAIL, "ABC123"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);

        verify(authMapper, never()).recordFailedAttempt(anyLong(), anyInt());
    }

    @Test
    void issueAndSend_새코드발급전에_기존활성토큰을무효화한다() {
        given(authMapper.selectUserByIdForUpdate(USER_ID)).willReturn(user());

        emailVerificationService.issueAndSend(user());

        verify(authMapper).selectUserByIdForUpdate(USER_ID);
        verify(authMapper).invalidateActiveTokens(USER_ID, PURPOSE);
        verify(authMapper).insertUserToken(any(UserToken.class));
    }

    @Test
    void issueAndSend_직전토큰이쿨다운시간내면_RESEND_COOLDOWN을던지고_재발급하지않는다() {
        UserToken recentToken = activeToken(TokenHashUtil.sha256("ABC123"));
        recentToken.setCreatedAt(LocalDateTime.now());
        given(authMapper.selectUserByIdForUpdate(USER_ID)).willReturn(user());
        given(authMapper.selectLatestToken(USER_ID, PURPOSE)).willReturn(recentToken);

        assertThatThrownBy(() -> emailVerificationService.issueAndSend(user()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESEND_COOLDOWN);

        verify(authMapper, never()).invalidateActiveTokens(anyLong(), any());
        verify(authMapper, never()).insertUserToken(any());
    }

    @Test
    void issueAndSend_직전토큰이_시도횟수초과로방금폐기됐어도_쿨다운은유지된다() {
        UserToken justInvalidatedToken = activeToken(TokenHashUtil.sha256("ABC123"));
        justInvalidatedToken.setCreatedAt(LocalDateTime.now());
        justInvalidatedToken.setUsedAt(LocalDateTime.now()); //5회 실패로 방금 폐기된 상태를 흉내
        given(authMapper.selectUserByIdForUpdate(USER_ID)).willReturn(user());
        given(authMapper.selectLatestToken(USER_ID, PURPOSE)).willReturn(justInvalidatedToken);

        assertThatThrownBy(() -> emailVerificationService.issueAndSend(user()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESEND_COOLDOWN);

        verify(authMapper, never()).insertUserToken(any());
    }

    @Test
    void issueAndSend_쿨다운이지난토큰이면_무효화하고_새로발급한다() {
        UserToken oldToken = activeToken(TokenHashUtil.sha256("ABC123"));
        oldToken.setCreatedAt(LocalDateTime.now().minusSeconds(301));
        given(authMapper.selectUserByIdForUpdate(USER_ID)).willReturn(user());
        given(authMapper.selectLatestToken(USER_ID, PURPOSE)).willReturn(oldToken);

        emailVerificationService.issueAndSend(user());

        verify(authMapper).invalidateActiveTokens(USER_ID, PURPOSE);
        verify(authMapper).insertUserToken(any(UserToken.class));
    }

    @Test
    void issueAndSend_잠긴유저row기준으로_토큰과메일을발급한다() {
        given(authMapper.selectUserByIdForUpdate(USER_ID)).willReturn(user());

        emailVerificationService.issueAndSend(user());

        verify(mailService).sendVerificationEmail(eq(EMAIL), anyString());
    }

    private User user() {
        return User.builder()
                .userId(USER_ID)
                .email(EMAIL)
                .emailVerified(false)
                .build();
    }

    private UserToken activeToken(String tokenHash) {
        return UserToken.builder()
                .tokenId(TOKEN_ID)
                .userId(USER_ID)
                .tokenHash(tokenHash)
                .purpose(PURPOSE)
                .attemptCount(0)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
    }
}
