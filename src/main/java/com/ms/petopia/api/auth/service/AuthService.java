package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.EmailCheckResponse;
import com.ms.petopia.api.auth.dto.EmailLoginRequest;
import com.ms.petopia.api.auth.dto.EmailSignupRequest;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    //탈퇴한 이메일 재가입 제한 기간
    private static final int WITHDRAWAL_COOLDOWN_DAYS = 15;

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final AuditLogService auditLogService;
    private final LoginAttemptStore loginAttemptStore;

    //회원가입
    @Transactional
    public void signup(EmailSignupRequest request){

        if(authMapper.existsVerifiedByEmail(request.getEmail())){
            throw new CommonException(ErrorCode.DUPLICATED_EMAIL);
        }

        if(authMapper.existsWithdrawnEmailWithinCooldown(request.getEmail(), WITHDRAWAL_COOLDOWN_DAYS)){
            throw new CommonException(ErrorCode.WITHDRAWN_EMAIL_COOLDOWN);
        }

        //인증 안 끝내고 이탈한 기존 row가 있으면 재사용, 없으면 새로 생성
        User existing = authMapper.selectUserByEmail(request.getEmail());

        //비밀번호를 해시값으로 변경
        String passwordHash = passwordEncoder.encode(request.getPassword());
        String normalizedPhone = request.getPhone().replaceAll("[^0-9]", "");
        User user = User.builder()
                .userId(existing != null ? existing.getUserId() : null)
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .nickname(request.getNickname())
                .birthDate(request.getBirthDate())
                .phone(normalizedPhone)
                .gender(request.getGender())
                .address(request.getAddress())
                .agreedTerms(request.getAgreedTerms())
                .agreedPrivacy(request.getAgreedPrivacy())
                .role("USER")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        try {
            if (existing != null) {
                int updated = authMapper.updateUnverifiedUser(user);
                if (updated == 0) {
                    throw new CommonException(ErrorCode.DUPLICATED_EMAIL);
                }
            } else {
                authMapper.insertUser(user);
            }
        } catch (DuplicateKeyException e) {
            //동시 가입 요청이 겹쳐 email UNIQUE 제약에 걸린 경우 - 500 대신 409로 응답
            throw new CommonException(ErrorCode.DUPLICATED_EMAIL, e);
        }
        emailVerificationService.issueAndSend(user);

    }

    //이메일이 이미 존재하는지 확인
    public EmailCheckResponse isEmailAvailable(String email){
        boolean exists = authMapper.existsVerifiedByEmail(email);
        boolean withinCooldown = authMapper.existsWithdrawnEmailWithinCooldown(email, WITHDRAWAL_COOLDOWN_DAYS);
        return new EmailCheckResponse(!exists && !withinCooldown);
    }

    //이메일 인증 재전송
    public void resendVerification(String email){
        emailVerificationService.resend(email);
    }

    //이메일 인증 코드 검증
    public void verifyEmail(String email, String token) {
        emailVerificationService.verify(email, token);
    }

    //로그인
    public TokenPair login(EmailLoginRequest request) {
        //이미 5회 이상 틀린 이메일이면 비밀번호 검사도 하지 않고 차단
        if (loginAttemptStore.isBlocked(request.getEmail())) {
            throw new CommonException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }

        User user = authMapper.selectUserByEmail(request.getEmail());

        //아이디/비밀번호 검사
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            try {
                auditLogService.record(
                        user != null ? user.getUserId() : null,
                        ActorType.USER,
                        user != null ? user.getRole() : null,
                        ActionType.LOGIN_FAIL,
                        TargetType.ACCOUNT,
                        user != null ? user.getUserId() : null,
                        null, null
                );
            } catch (Exception e) {
                log.error("LOGIN_FAIL 감사 로그 저장 실패", e);
            }
            loginAttemptStore.recordFailure(request.getEmail());
            throw new CommonException(ErrorCode.INVALID_LOGIN);
        }

        //비밀번호가 맞았으니 실패 카운트 리셋
        loginAttemptStore.reset(request.getEmail());

        //정지된 계정인지 확인
        if(user.getStatus().equals("INACTIVE")) {
            throw new CommonException(ErrorCode.ACCOUNT_INACTIVE);
        }

        //이메일 인증 여부 확인
        if(!user.isEmailVerified()) {
            throw new CommonException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        //JwtTokenProvider로 accessToken, refreshToken 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        //refreshToken을 해시로 저장
        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);
        refreshTokenStore.save(refreshTokenHash, user.getUserId(), Duration.ofDays(14));

        return new TokenPair(accessToken, refreshToken);
    }

}
