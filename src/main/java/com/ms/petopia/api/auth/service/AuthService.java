package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.EmailCheckResponse;
import com.ms.petopia.api.auth.dto.EmailSignupRequest;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

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
        User user = User.builder()
                .userId(existing != null ? existing.getUserId() : null)
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .nickname(request.getNickname())
                .birthDate(request.getBirthDate())
                .phone(request.getPhone())
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



}
