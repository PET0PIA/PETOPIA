package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.domain.UserToken;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String PURPOSE_EMAIL_VERIFY = "EMAIL_VERIFY";
    private static final long EXPIRATION_MINUTES = 10;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final long RESEND_COOLDOWN_SECONDS = 300;

    private final AuthMapper authMapper;
    private final MailService mailService;

    //회원가입 인증 코드 발급
    //users.user_id 행을 잠가서 같은 유저의 동시 요청을 직렬화
    @Transactional
    public void issueAndSend(User user) {
        User lockedUser = authMapper.selectUserByIdForUpdate(user.getUserId());

        //직전 토큰이 쿨다운 시간 내에 발급됐으면 재요청 거부
        UserToken latestToken = authMapper.selectLatestToken(lockedUser.getUserId(), PURPOSE_EMAIL_VERIFY);
        if (latestToken != null
                && latestToken.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            throw new CommonException(ErrorCode.RESEND_COOLDOWN);
        }

        //재전송 등으로 새 코드를 발급하기 전, 기존에 살아있던 코드는 무효화
        authMapper.invalidateActiveTokens(lockedUser.getUserId(), PURPOSE_EMAIL_VERIFY);

        //TokenHashUtil.generateVerificationCode()로 원본 코드 생성 (6자리, 대문자+숫자)
        String rawToken = TokenHashUtil.generateVerificationCode();

        //TokenHashUtil.sha256(rawToken)으로 해시값 생성
        String tokenHash = TokenHashUtil.sha256(rawToken);

        //UserToken.builder()로 저장할 객체 만들기
        LocalDateTime now = LocalDateTime.now();
        UserToken userToken = UserToken.builder()
                .userId(lockedUser.getUserId())
                .tokenHash(tokenHash)
                .purpose(PURPOSE_EMAIL_VERIFY)
                .createdAt(now)
                .expiresAt(now.plusMinutes(EXPIRATION_MINUTES))
                .build();

        //DB에 저장
        authMapper.insertUserToken(userToken);

        //TODO SMTP 응답을 기다리는 동안 users row FOR UPDATE 락을 계속 들고 있음.
        //     outbox/워커로 분리해 커밋 후 비동기 발송하도록 개선 필요 (2차 범위)
        mailService.sendVerificationEmail(lockedUser.getEmail(), rawToken);
    }


    //이메일 인증 재전송
    //AuthService에서 받아 여기서 판단하고, 통과하면 issueAndSend를 그대로 재사용
    @Transactional
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
    //실패 시 recordFailedAttempt로 남긴 시도 횟수까지 롤백되면 시도 제한이 무의미해지므로 CommonException은 롤백 대상에서 제외
    @Transactional(noRollbackFor = CommonException.class)
    public void verify(String email, String rawToken) {
        User user = authMapper.selectUserByEmail(email);
        if (user == null) {
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        }

        //issueAndSend()와 잠금 순서(users -> user_tokens)를 맞춰 데드락을 피함
        authMapper.selectUserByIdForUpdate(user.getUserId());

        //email 기준으로만 조회. 다른 사용자 검증 끼어들기 막음
        UserToken userToken = authMapper.selectActiveUserToken(user.getUserId(), PURPOSE_EMAIL_VERIFY);
        if (userToken == null) {
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        }
        if (userToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CommonException(ErrorCode.TOKEN_EXPIRED);
        }

        String tokenHash = TokenHashUtil.sha256(rawToken);
        if (!tokenHash.equals(userToken.getTokenHash())) {
            //실패 횟수 증가 + maxAttempts 도달 시
            authMapper.recordFailedAttempt(userToken.getTokenId(), MAX_VERIFY_ATTEMPTS);
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        }

        //AND used_at IS NULL 가드로 인해 동시 요청 중 한쪽만 갱신되게 함
        int updated = authMapper.markUserTokenUsed(userToken.getTokenId());
        if (updated == 0) {
            throw new CommonException(ErrorCode.TOKEN_ALREADY_USED);
        }
        authMapper.markEmailVerified(userToken.getUserId());
    }
}
