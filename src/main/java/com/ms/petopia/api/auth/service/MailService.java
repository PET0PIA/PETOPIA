package com.ms.petopia.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    //이메일 인증
    public void sendVerificationEmail(String to, String verificationCode){
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(fromEmail);
        simpleMailMessage.setTo(to);
        simpleMailMessage.setSubject("[PETOPIA] 이메일 인증코드");
        simpleMailMessage.setText("""
            안녕하세요. PETOPIA 입니다.

            PETOPIA에 가입해 주셔서 감사합니다.
            아래 인증 코드를 입력해 이메일 인증을 완료해주세요.

            [인증코드] %s

            인증코드는 발급 후 10분 동안 유효합니다.

            감사합니다.
            PETOPIA 드림.
            """.formatted(verificationCode));
        javaMailSender.send(simpleMailMessage);
    }

    //관리자 계정 발급 메일 (개설비 청구내역 + 결제 링크 포함)
    public void sendAdminAccountIssueEmail(String to, String tempPassword,
                                            Long openingFeeAmount, LocalDateTime paymentDueAt, String paymentLink){
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(fromEmail);
        simpleMailMessage.setTo(to);
        simpleMailMessage.setSubject("[PETOPIA] 행사 관리자 계정이 발급되었습니다");
        simpleMailMessage.setText("""
            안녕하세요. PETOPIA 입니다.

            행사 승인이 완료되어 행사 관리자 계정이 발급되었습니다.

            [아이디] %s
            [임시 비밀번호] %s

            로그인 후 반드시 비밀번호를 변경해 주세요.

            [개설비 청구내역]
            결제 금액: %s원
            결제 기한: %s까지

            아래 링크에서 개설비를 결제해 주세요.
            [개설비 결제 링크] %s

            감사합니다.
            PETOPIA 드림.
            """.formatted(
                to, tempPassword,
                String.format("%,d", openingFeeAmount),
                paymentDueAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                paymentLink
        ));
        javaMailSender.send(simpleMailMessage);
    }

    //비밀번호 재설정 메일
    public void sendPasswordResetEmail(String to, String resetLink){
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(fromEmail);
        simpleMailMessage.setTo(to);
        simpleMailMessage.setSubject("[PETOPIA] 비밀번호 재설정");
        simpleMailMessage.setText("""
            안녕하세요. PETOPIA 입니다.

            비밀번호 재발급 링크를 전해드립니다.
            링크를 클릭해 비밀번호를 변경해주세요.

            [비밀번호 재발급 링크] %s

            해당 링크는 발급 후 10분 동안만 유효합니다.

            감사합니다.
            PETOPIA 드림.
            """.formatted(resetLink));
        javaMailSender.send(simpleMailMessage);
    }
}
