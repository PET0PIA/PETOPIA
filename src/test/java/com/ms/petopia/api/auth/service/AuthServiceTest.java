package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.EmailSignupRequest;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "test@petopia.com";

    @Mock
    private AuthMapper authMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailVerificationService emailVerificationService;
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
