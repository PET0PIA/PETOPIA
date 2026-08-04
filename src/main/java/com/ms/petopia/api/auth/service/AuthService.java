package com.ms.petopia.api.auth.service;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    //회원가입
    @Transactional
    public void signup(EmailSignupRequest request){

        if(authMapper.existsVerifiedByEmail(request.getEmail())){
            throw new CommonException(ErrorCode.DUPLICATED_EMAIL);
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
        return new EmailCheckResponse(!exists);
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
        User user = authMapper.selectUserByEmail(request.getEmail());

        //아이디/비밀번호 검사
        if(user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())){
            throw new CommonException(ErrorCode.INVALID_LOGIN);
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
