package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.domain.UserToken;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TOKEN_ID = 100L;
    private static final String EMAIL = "test@petopia.com";
    private static final String FRONTEND_URL = "http://localhost:5173";

    @Mock
    private AuthMapper authMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailService mailService;
    @Mock
    private LoginAttemptStore loginAttemptStore;
    @InjectMocks
    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        //@Value 필드는 Spring이 주입하므로 테스트에서는 ReflectionTestUtils로 세팅
        ReflectionTestUtils.setField(passwordService, "frontendUrl", FRONTEND_URL);
    }

    @Test
    void changePassword_현재비밀번호가틀리면_INVALID_PASSWORD를_던진다() {
        given(authMapper.selectUserById(USER_ID)).willReturn(user());
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        assertThatThrownBy(() -> passwordService.changePassword(USER_ID, "wrong", "newPassword1!"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);

        verify(authMapper, never()).updateUserPassword(any(), any());
    }

    @Test
    void changePassword_유저가없으면_INVALID_PASSWORD를_던진다() {
        given(authMapper.selectUserById(USER_ID)).willReturn(null);

        assertThatThrownBy(() -> passwordService.changePassword(USER_ID, "current", "newPassword1!"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
    }

    @Test
    void changePassword_정상이면_새비밀번호를_BCrypt로_암호화해서_저장한다() {
        given(authMapper.selectUserById(USER_ID)).willReturn(user());
        given(passwordEncoder.matches("current", "hashed")).willReturn(true);
        given(passwordEncoder.encode("newPassword1!")).willReturn("new-hashed");

        passwordService.changePassword(USER_ID, "current", "newPassword1!");

        verify(authMapper).updateUserPassword(USER_ID, "new-hashed");
    }

    @Test
    void issueResetLink_토큰을_저장하고_프론트URL이_포함된_링크를_반환한다() {
        String link = passwordService.issueResetLink(USER_ID);

        ArgumentCaptor<UserToken> captor = ArgumentCaptor.forClass(UserToken.class);
        verify(authMapper).insertUserToken(captor.capture());
        UserToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getPurpose()).isEqualTo("PASSWORD_RESET");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());

        assertThat(link).startsWith(FRONTEND_URL + "/reset-password?token=");
    }

    @Test
    void issueResetLink_쿨다운시간내_재요청이면_RESEND_COOLDOWN을_던지고_기존토큰을_무효화하지않는다() {
        UserToken recentToken = UserToken.builder()
                .tokenId(TOKEN_ID)
                .userId(USER_ID)
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .build();
        given(authMapper.selectLatestToken(USER_ID, "PASSWORD_RESET")).willReturn(recentToken);

        assertThatThrownBy(() -> passwordService.issueResetLink(USER_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESEND_COOLDOWN);

        verify(authMapper, never()).invalidateActiveTokens(any(), any());
        verify(authMapper, never()).insertUserToken(any());
    }

    @Test
    void requestPasswordReset_쿨다운중이면_예외없이_조용히_끝난다() {
        UserToken recentToken = UserToken.builder()
                .tokenId(TOKEN_ID)
                .userId(USER_ID)
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .build();
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(user());
        given(authMapper.selectLatestToken(USER_ID, "PASSWORD_RESET")).willReturn(recentToken);

        passwordService.requestPasswordReset(EMAIL);

        verify(authMapper, never()).insertUserToken(any());
        verify(mailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestPasswordReset_존재하지않는이메일이면_아무것도_하지않는다() {
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(null);

        passwordService.requestPasswordReset(EMAIL);

        verify(authMapper, never()).insertUserToken(any());
        verify(mailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestPasswordReset_존재하는이메일이면_토큰발급하고_메일을_보낸다() {
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(user());

        passwordService.requestPasswordReset(EMAIL);

        verify(authMapper).insertUserToken(any(UserToken.class));
        verify(mailService).sendPasswordResetEmail(eq(EMAIL), anyString());
    }

    @Test
    void resetPassword_토큰이없으면_INVALID_TOKEN을_던진다() {
        given(authMapper.selectUserTokenByHash(anyString(), eq("PASSWORD_RESET"))).willReturn(null);

        assertThatThrownBy(() -> passwordService.resetPassword("raw-token", "newPassword1!"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(authMapper, never()).updateUserPassword(any(), any());
    }

    @Test
    void resetPassword_토큰이만료됐으면_TOKEN_EXPIRED를_던진다() {
        UserToken expired = UserToken.builder()
                .tokenId(TOKEN_ID)
                .userId(USER_ID)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        given(authMapper.selectUserTokenByHash(anyString(), eq("PASSWORD_RESET"))).willReturn(expired);

        assertThatThrownBy(() -> passwordService.resetPassword("raw-token", "newPassword1!"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);

        verify(authMapper, never()).updateUserPassword(any(), any());
    }

    @Test
    void resetPassword_이미사용된토큰이면_TOKEN_ALREADY_USED를_던진다() {
        UserToken token = UserToken.builder()
                .tokenId(TOKEN_ID)
                .userId(USER_ID)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        given(authMapper.selectUserTokenByHash(anyString(), eq("PASSWORD_RESET"))).willReturn(token);
        given(authMapper.markUserTokenUsed(TOKEN_ID)).willReturn(0);

        assertThatThrownBy(() -> passwordService.resetPassword("raw-token", "newPassword1!"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_ALREADY_USED);

        verify(authMapper, never()).updateUserPassword(any(), any());
    }

    @Test
    void resetPassword_정상이면_비밀번호를_BCrypt로_암호화해서_저장한다() {
        UserToken token = UserToken.builder()
                .tokenId(TOKEN_ID)
                .userId(USER_ID)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        given(authMapper.selectUserTokenByHash(anyString(), eq("PASSWORD_RESET"))).willReturn(token);
        given(authMapper.markUserTokenUsed(TOKEN_ID)).willReturn(1);
        given(passwordEncoder.encode("newPassword1!")).willReturn("new-hashed");
        given(authMapper.selectUserById(USER_ID)).willReturn(user());

        passwordService.resetPassword("raw-token", "newPassword1!");

        verify(authMapper).updateUserPassword(USER_ID, "new-hashed");
        verify(loginAttemptStore).reset(EMAIL);
    }

    private User user() {
        return User.builder()
                .userId(USER_ID)
                .email(EMAIL)
                .passwordHash("hashed")
                .build();
    }
}
