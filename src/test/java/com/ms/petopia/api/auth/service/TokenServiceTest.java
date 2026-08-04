package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final Long USER_ID = 1L;
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";

    @Mock
    private AuthMapper authMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @InjectMocks
    private TokenService tokenService;

    @Test
    void refresh_유효한토큰이면_새토큰쌍을반환하고_기존토큰을무효화한다() {
        String hash = TokenHashUtil.sha256(RAW_REFRESH_TOKEN);
        given(refreshTokenStore.findUserId(hash)).willReturn(USER_ID);
        given(authMapper.selectUserById(USER_ID)).willReturn(user());
        given(jwtTokenProvider.generateAccessToken(USER_ID, "USER")).willReturn("new-access");
        given(jwtTokenProvider.generateRefreshToken(USER_ID)).willReturn("new-refresh");

        TokenPair result = tokenService.refresh(RAW_REFRESH_TOKEN);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenStore).revoke(hash);
        verify(refreshTokenStore).save(eq(TokenHashUtil.sha256("new-refresh")), eq(USER_ID), any());
    }

    @Test
    void refresh_Redis에없는토큰이면_INVALID_REFRESH_TOKEN을던진다() {
        given(refreshTokenStore.findUserId(any())).willReturn(null);

        assertThatThrownBy(() -> tokenService.refresh(RAW_REFRESH_TOKEN))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        verify(authMapper, never()).selectUserById(any());
    }

    @Test
    void refresh_토큰주인유저가없으면_INVALID_REFRESH_TOKEN을던진다() {
        String hash = TokenHashUtil.sha256(RAW_REFRESH_TOKEN);
        given(refreshTokenStore.findUserId(hash)).willReturn(USER_ID);
        given(authMapper.selectUserById(USER_ID)).willReturn(null);

        assertThatThrownBy(() -> tokenService.refresh(RAW_REFRESH_TOKEN))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        verify(jwtTokenProvider, never()).generateAccessToken(any(), any());
    }

    @Test
    void logout_토큰을해시해서revoke한다() {
        String hash = TokenHashUtil.sha256(RAW_REFRESH_TOKEN);

        tokenService.logout(RAW_REFRESH_TOKEN);

        verify(refreshTokenStore).revoke(hash);
    }

    private User user() {
        return User.builder()
                .userId(USER_ID)
                .role("USER")
                .build();
    }
}
