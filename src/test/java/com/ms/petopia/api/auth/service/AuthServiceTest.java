package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.EmailLoginRequest;
import com.ms.petopia.api.auth.dto.EmailSignupRequest;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "test@petopia.com";
    private static final Long USER_ID = 1L;

    @Mock
    private AuthMapper authMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @InjectMocks
    private AuthService authService;

    @Test
    void signup_새이메일이면_insertUser로_새로_생성한다() {
        given(authMapper.existsVerifiedByEmail(EMAIL)).willReturn(false);
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(null);
        given(passwordEncoder.encode(any())).willReturn("hashed");

        authService.signup(buildRequest());

        verify(authMapper).insertUser(any(User.class));
        verify(authMapper, never()).updateUnverifiedUser(any());
        verify(emailVerificationService).issueAndSend(any(User.class));
    }

    @Test
    void signup_미인증기존row가있으면_updateUnverifiedUser로_같은유저를_재사용한다() {
        User existing = User.builder()
                .userId(1L)
                .email(EMAIL)
                .emailVerified(false)
                .build();
        given(authMapper.existsVerifiedByEmail(EMAIL)).willReturn(false);
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(existing);
        given(passwordEncoder.encode(any())).willReturn("hashed");
        given(authMapper.updateUnverifiedUser(any(User.class))).willReturn(1);

        authService.signup(buildRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(authMapper).updateUnverifiedUser(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        verify(authMapper, never()).insertUser(any());
    }

    @Test
    void signup_이미인증된이메일이면_DUPLICATED_EMAIL을_던지고_아무것도_하지않는다() {
        given(authMapper.existsVerifiedByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> authService.signup(buildRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATED_EMAIL);

        verify(authMapper, never()).selectUserByEmail(any());
        verify(authMapper, never()).insertUser(any());
        verify(authMapper, never()).updateUnverifiedUser(any());
        verify(emailVerificationService, never()).issueAndSend(any());
    }

    @Test
    void signup_동시가입으로_UNIQUE제약에걸리면_DUPLICATED_EMAIL로변환한다() {
        given(authMapper.existsVerifiedByEmail(EMAIL)).willReturn(false);
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(null);
        given(passwordEncoder.encode(any())).willReturn("hashed");
        given(authMapper.insertUser(any(User.class)))
                .willThrow(new DuplicateKeyException("UK_USERS_EMAIL"));

        assertThatThrownBy(() -> authService.signup(buildRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATED_EMAIL);

        verify(emailVerificationService, never()).issueAndSend(any());
    }

    @Test
    void signup_재가입중_상대가먼저인증을끝내면_DUPLICATED_EMAIL을던지고_인증메일을보내지않는다() {
        User existing = User.builder()
                .userId(1L)
                .email(EMAIL)
                .emailVerified(false)
                .build();
        given(authMapper.existsVerifiedByEmail(EMAIL)).willReturn(false);
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(existing);
        given(passwordEncoder.encode(any())).willReturn("hashed");
        //AND email_verified = FALSE 가드에 걸려 0건 갱신되는 경우를 흉내
        given(authMapper.updateUnverifiedUser(any(User.class))).willReturn(0);

        assertThatThrownBy(() -> authService.signup(buildRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATED_EMAIL);

        verify(emailVerificationService, never()).issueAndSend(any());
    }

    @Test
    void login_이메일과비밀번호가맞고인증된유저면_토큰을발급한다() {
        User user = loginUser();
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(user);
        given(passwordEncoder.matches("password1!", "hashed")).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(USER_ID, "USER")).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

        TokenPair result = authService.login(loginRequest());

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenStore).save(any(String.class), eq(USER_ID), any());
    }

    @Test
    void login_이메일이없으면_INVALID_LOGIN을던진다() {
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(null);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_LOGIN);

        verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), any());
    }

    @Test
    void login_비밀번호가틀리면_INVALID_LOGIN을던진다() {
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(loginUser());
        given(passwordEncoder.matches("password1!", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_LOGIN);
    }

    @Test
    void login_이메일미인증이면_EMAIL_NOT_VERIFIED를던진다() {
        User user = User.builder()
                .userId(USER_ID)
                .email(EMAIL)
                .passwordHash("hashed")
                .role("USER")
                .emailVerified(false)
                .build();
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(user);
        given(passwordEncoder.matches("password1!", "hashed")).willReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), any());
    }

    private User loginUser() {
        return User.builder()
                .userId(USER_ID)
                .email(EMAIL)
                .passwordHash("hashed")
                .role("USER")
                .emailVerified(true)
                .build();
    }

    private EmailLoginRequest loginRequest() {
        EmailLoginRequest request = new EmailLoginRequest();
        request.setEmail(EMAIL);
        request.setPassword("password1!");
        return request;
    }

    private EmailSignupRequest buildRequest() {
        EmailSignupRequest request = new EmailSignupRequest();
        request.setEmail(EMAIL);
        request.setPassword("password1!");
        request.setPasswordConfirm("password1!");
        request.setNickname("나경");
        request.setBirthDate(LocalDate.of(2000, 1, 1));
        request.setPhone("010-1234-5678");
        request.setGender("F");
        request.setAddress("서울시 강남구");
        request.setAgreedTerms(true);
        request.setAgreedPrivacy(true);
        return request;
    }
}
