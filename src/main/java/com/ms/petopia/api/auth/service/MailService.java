package com.ms.petopia.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailPreparationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
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
        String safeCode = escape(verificationCode);
        String content = """
            <p style="margin:0 0 18px;color:#4b5563;font-size:15px;line-height:1.7;">
              PETOPIA에 가입해 주셔서 감사합니다.<br>
              아래 인증 코드를 회원가입 화면에 입력해 주세요.
            </p>
            <div style="margin:24px 0;padding:22px 16px;border:1px solid #f3c6c6;border-radius:14px;background:#fff7f7;text-align:center;">
              <div style="margin-bottom:8px;color:#9ca3af;font-size:12px;font-weight:700;letter-spacing:1px;">EMAIL VERIFICATION CODE</div>
              <div style="color:#d94848;font-size:32px;font-weight:800;letter-spacing:7px;">%s</div>
            </div>
            <p style="margin:0;color:#6b7280;font-size:13px;line-height:1.7;">
              인증 코드는 발급 후 <strong style="color:#374151;">10분 동안</strong> 유효합니다.<br>
              본인이 요청하지 않은 메일이라면 안전하게 무시해 주세요.
            </p>
            """.formatted(safeCode);

        sendHtmlEmail(to, "[PETOPIA] 이메일 인증코드", "반가워요!", "이메일 인증을 완료해 주세요", content);
    }

    //관리자 계정 발급 메일 (개설비 청구내역 + 결제 링크 포함)
    public void sendAdminAccountIssueEmail(String to, String tempPassword,
                                            Long openingFeeAmount, LocalDateTime paymentDueAt, String paymentLink){
        String safeEmail = escape(to);
        String safePassword = escape(tempPassword);
        String safeAmount = escape(String.format("%,d", openingFeeAmount));
        String safeDueAt = escape(paymentDueAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        String safePaymentLink = escape(paymentLink);
        String content = """
            <p style="margin:0 0 18px;color:#4b5563;font-size:15px;line-height:1.7;">
              행사 승인이 완료되어 PETOPIA 행사 관리자 계정이 발급되었습니다.
            </p>
            <table role="presentation" style="width:100%%;margin:20px 0;border-collapse:separate;border-spacing:0;border:1px solid #e5e7eb;border-radius:14px;background:#f9fafb;">
              <tr><td style="padding:18px 20px 8px;color:#6b7280;font-size:12px;">아이디</td><td style="padding:18px 20px 8px;text-align:right;color:#111827;font-size:14px;font-weight:700;">%s</td></tr>
              <tr><td style="padding:8px 20px 18px;color:#6b7280;font-size:12px;">임시 비밀번호</td><td style="padding:8px 20px 18px;text-align:right;color:#d94848;font-size:16px;font-weight:800;">%s</td></tr>
            </table>
            <p style="margin:0 0 22px;color:#6b7280;font-size:13px;line-height:1.7;">보안을 위해 첫 로그인 후 반드시 비밀번호를 변경해 주세요.</p>
            <div style="margin:0 0 22px;padding:18px 20px;border-radius:14px;background:#fff7e6;">
              <div style="margin-bottom:10px;color:#8a5a00;font-size:13px;font-weight:800;">개설비 청구 내역</div>
              <div style="color:#4b5563;font-size:14px;line-height:1.8;">결제 금액 <strong style="float:right;color:#111827;">%s원</strong><br>결제 기한 <strong style="float:right;color:#111827;">%s까지</strong></div>
            </div>
            <div style="text-align:center;">
              <a href="%s" style="display:inline-block;padding:13px 26px;border-radius:10px;background:#d94848;color:#ffffff;font-size:14px;font-weight:800;text-decoration:none;">개설비 결제하기</a>
            </div>
            """.formatted(safeEmail, safePassword, safeAmount, safeDueAt, safePaymentLink);

        sendHtmlEmail(to, "[PETOPIA] 행사 관리자 계정이 발급되었습니다", "행사 승인이 완료됐어요", "관리자 계정 발급 안내", content);
    }

    //비밀번호 재설정 메일
    public void sendPasswordResetEmail(String to, String resetLink){
        String safeResetLink = escape(resetLink);
        String content = """
            <p style="margin:0 0 22px;color:#4b5563;font-size:15px;line-height:1.7;">
              비밀번호 재설정 요청을 받았습니다.<br>
              아래 버튼을 눌러 새로운 비밀번호를 설정해 주세요.
            </p>
            <div style="margin:26px 0;text-align:center;">
              <a href="%s" style="display:inline-block;padding:13px 26px;border-radius:10px;background:#d94848;color:#ffffff;font-size:14px;font-weight:800;text-decoration:none;">비밀번호 재설정하기</a>
            </div>
            <p style="margin:0 0 14px;color:#6b7280;font-size:13px;line-height:1.7;">
              링크는 발급 후 <strong style="color:#374151;">10분 동안</strong> 유효합니다.<br>
              본인이 요청하지 않았다면 비밀번호는 변경되지 않으니 이 메일을 무시해 주세요.
            </p>
            <p style="margin:0;padding-top:14px;border-top:1px solid #e5e7eb;color:#9ca3af;font-size:11px;line-height:1.6;word-break:break-all;">
              버튼이 동작하지 않으면 아래 주소를 브라우저에 붙여 넣어 주세요.<br>%s
            </p>
            """.formatted(safeResetLink, safeResetLink);

        sendHtmlEmail(to, "[PETOPIA] 비밀번호 재설정", "계정 보안 안내", "비밀번호를 재설정해 주세요", content);
    }

    private void sendHtmlEmail(String to, String subject, String eyebrow, String title, String content) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(emailLayout(eyebrow, title, content), true);
            javaMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new MailPreparationException("PETOPIA HTML 메일 생성에 실패했습니다.", e);
        }
    }

    private String emailLayout(String eyebrow, String title, String content) {
        return """
            <!doctype html>
            <html lang="ko">
              <body style="margin:0;padding:0;background:#f5f2ee;font-family:Arial,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;color:#111827;">
                <table role="presentation" style="width:100%%;border-collapse:collapse;background:#f5f2ee;">
                  <tr><td style="padding:36px 16px;">
                    <table role="presentation" style="width:100%%;max-width:560px;margin:0 auto;border-collapse:separate;border-spacing:0;border-radius:20px;background:#ffffff;box-shadow:0 8px 28px rgba(31,41,55,0.08);overflow:hidden;">
                      <tr><td style="padding:26px 32px;background:#d94848;text-align:center;">
                        <div style="color:#ffffff;font-size:25px;font-weight:900;letter-spacing:2px;">PETOPIA</div>
                        <div style="margin-top:5px;color:#ffe8e8;font-size:11px;letter-spacing:1px;">PET FAIR PLATFORM</div>
                      </td></tr>
                      <tr><td style="padding:34px 32px 30px;">
                        <div style="margin-bottom:8px;color:#d94848;font-size:12px;font-weight:800;letter-spacing:1px;">%s</div>
                        <h1 style="margin:0 0 14px;color:#111827;font-size:24px;line-height:1.4;">%s</h1>
                        <p style="margin:0 0 22px;color:#374151;font-size:15px;font-weight:700;line-height:1.7;">안녕하십니까? 펫페어 관리 사이트 PETOPIA입니다.<br>저희 PETOPIA를 이용해 주셔서 대단히 감사드립니다.</p>
                        %s
                      </td></tr>
                      <tr><td style="padding:20px 32px;border-top:1px solid #f0f0f0;background:#fafafa;text-align:center;color:#9ca3af;font-size:11px;line-height:1.6;">
                        이 메일은 PETOPIA 서비스 이용을 위해 자동으로 발송되었습니다.<br>
                        © 2026 PETOPIA. All rights reserved.
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
              </body>
            </html>
            """.formatted(escape(eyebrow), escape(title), content);
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
