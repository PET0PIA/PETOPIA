package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.domain.UserToken;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String PURPOSE_EMAIL_VERIFY = "EMAIL_VERIFY";
    private static final long EXPIRATION_MINUTES = 10;

    private final AuthMapper authMapper;
    private final MailService mailService;

    //회원가입 인증 코드 발급
    public void issueAndSend(User user) {
        //TokenHashUtil.generateVerificationCode()로 원본 코드 생성 (6자리, 대문자+숫자)
        String rawToken = TokenHashUtil.generateVerificationCode();

        //TokenHashUtil.sha256(rawToken)으로 해시값 생성
        String tokenHash = TokenHashUtil.sha256(rawToken);

        //UserToken.builder()로 저장할 객체 만들기
        LocalDateTime now = LocalDateTime.now();
        UserToken userToken = UserToken.builder()
                .userId(user.getUserId())
                .tokenHash(tokenHash)
                .purpose(PURPOSE_EMAIL_VERIFY)
                .createdAt(now)
                .expiresAt(now.plusMinutes(EXPIRATION_MINUTES))
                .build();

        //DB에 저장
        authMapper.insertUserToken(userToken);

        mailService.sendVerificationEmail(user.getEmail(), rawToken);
    }


    //이메일 인증 재전송
    //AuthService에서 받아 여기서 판단하고, 통과하면 issueAndSend를 그대로 재사용
    public void resend(String email) {
        User user = authMapper.selectUserByEmail(email);
        if (user == null) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.isEmailVerified()) {
            throw new CommonException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        issueAndSend(user);
    }

    //이메일 인증 코드 검증
    public void verify(String rawToken) {
        String tokenHash = TokenHashUtil.sha256(rawToken);

        UserToken userToken = authMapper.selectUserTokenByHash(tokenHash, PURPOSE_EMAIL_VERIFY);

        if (userToken == null) {
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        }
        if (userToken.getUsedAt() != null) {
            throw new CommonException(ErrorCode.TOKEN_ALREADY_USED);
        }
        if (userToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CommonException(ErrorCode.TOKEN_EXPIRED);
        }

        authMapper.markUserTokenUsed(userToken.getTokenId());
        authMapper.markEmailVerified(userToken.getUserId());
    }
}
