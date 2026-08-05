package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.domain.UserToken;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
    private static final long RESET_EXPIRATION_MINUTES = 10;
    private static final long RESET_RESEND_COOLDOWN_SECONDS = 300;
    private final MailService mailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;

    //비밀번호 변경(마이페이지용)
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = authMapper.selectUserById(userId);


        if(user == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())){
            throw new CommonException(ErrorCode.INVALID_PASSWORD);
        }
        String newHash = passwordEncoder.encode(newPassword);
        authMapper.updateUserPassword(userId, newHash);
    }

    //비밀번호 재설정(분실용) 이메일 전송용 링크 메소드
    @Transactional
    public String issueResetLink(Long userId) {
        //직전 토큰이 쿨다운 시간 내에 발급됐으면 재요청 거부
        UserToken latestToken = authMapper.selectLatestToken(userId, PURPOSE_PASSWORD_RESET);
        if (latestToken != null
                && latestToken.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(RESET_RESEND_COOLDOWN_SECONDS))) {
            throw new CommonException(ErrorCode.RESEND_COOLDOWN);
        }

        //재요청 전 기존에 살아있던 토큰은 무효화 (동시에 여러 토큰이 유효한 상태 방지)
        authMapper.invalidateActiveTokens(userId, PURPOSE_PASSWORD_RESET);

        String rawToken = TokenHashUtil.generateRawToken();
        String tokenHash = TokenHashUtil.sha256(rawToken);

        LocalDateTime now = LocalDateTime.now();
        UserToken userToken = UserToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .purpose(PURPOSE_PASSWORD_RESET)
                .createdAt(now)
                .expiresAt(now.plusMinutes(RESET_EXPIRATION_MINUTES))
                .build();
        authMapper.insertUserToken(userToken);
        return frontendUrl + "/reset-password?token=" + rawToken;
    }

    //메일 전송용 메소드
    @Transactional
    public void requestPasswordReset(String email) {
        User user = authMapper.selectUserByEmail(email);
        if (user == null) {
            return;
        }
        try {
            String resetLink = issueResetLink(user.getUserId());
            mailService.sendPasswordResetEmail(email, resetLink);
        } catch (CommonException e) {
            if (e.getErrorCode() != ErrorCode.RESEND_COOLDOWN) {
                throw e;
            }
        }
    }

    //비밀번호 재설정(분실용)
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String hashToken = TokenHashUtil.sha256(rawToken);
        UserToken userToken = authMapper.selectUserTokenByHash(hashToken, PURPOSE_PASSWORD_RESET);
        if (userToken == null) {
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        }
        if (userToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CommonException(ErrorCode.TOKEN_EXPIRED);
        }
        if(authMapper.markUserTokenUsed(userToken.getTokenId()) == 0){
            throw new CommonException(ErrorCode.TOKEN_ALREADY_USED);
        }

        String newToken = passwordEncoder.encode(newPassword);
        authMapper.updateUserPassword(userToken.getUserId(), newToken);
    }



}
