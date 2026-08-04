package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final AuthMapper authMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    public TokenPair refresh(String refreshToken) {
        //refreshToken 해시
        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);

        //조회+삭제를 원자적으로(GETDEL) - 동시 요청이 같은 토큰을 중복으로 소비 못 하게 함
        Long userId = refreshTokenStore.consumeUserId(refreshTokenHash);
        if(userId == null){
            throw new CommonException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        //토큰 발급 후 유저유무 확인
        User user = authMapper.selectUserById(userId);
        if(user == null) {
            throw new CommonException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        //새 accessToken, 새 refreshToken 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        //기존 refresh token은 consumeUserId 시점에 이미 삭제됨(GETDEL)

        //새 refresh token 저장
        String newRefreshTokenHash = TokenHashUtil.sha256(newRefreshToken);
        refreshTokenStore.save(newRefreshTokenHash, user.getUserId(), Duration.ofDays(14));

        return new TokenPair(newAccessToken, newRefreshToken);
    }

    //로그아웃
    public void logout(String refreshToken) {
        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);
        refreshTokenStore.revoke(refreshTokenHash);
    }
}
