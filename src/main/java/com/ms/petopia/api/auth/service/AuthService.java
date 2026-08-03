package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.EmailCheckResponse;
import com.ms.petopia.api.auth.dto.EmailSignupRequest;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    //회원가입
    public void signup(EmailSignupRequest request){

        if(authMapper.existsByEmail(request.getEmail())){
            throw new CommonException(ErrorCode.DUPLICATED_EMAIL);
        }

        //비밀번호를 해시값으로 변경
        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = User.builder()
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
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        authMapper.insertUser(user);
        emailVerificationService.issueAndSend(user);

    }

    //이메일이 이미 존재하는지 확인
    public EmailCheckResponse isEmailAvailable(String email){
        boolean exists = authMapper.existsByEmail(email);
        return new EmailCheckResponse(!exists);
    }

    //이메일 인증 재전송
    public void resendVerification(String email){
        emailVerificationService.resend(email);
    }

    //이메일 인증 코드 검증
    public void verifyEmail(String token) {
        emailVerificationService.verify(token);
    }



}
