package com.ms.petopia.api.auth.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
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

    /**
     * 행사 승인 안내와 개설비 결제 정보를 기존 PETOPIA HTML 디자인으로 발송한다.
     */
    public void sendFairApprovalEmail(String to, Long openingFeeAmount,
                                      LocalDateTime paymentDueAt, String paymentLink) {
        String safePaymentLink = escape(paymentLink);
        String amount = openingFeeAmount == null ? "-" : String.format("%,d원", openingFeeAmount);
        String dueDate = paymentDueAt == null ? "-" : paymentDueAt.toLocalDate().toString();
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              행사 신청이 승인되어 현재 로그인 계정에 행사 관리 권한이 추가되었습니다.<br>
              아래 내용을 확인한 뒤 기한 내에 개설비를 결제해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">개설비 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">결제 기한 <strong style="float:right;color:#111827;">%s까지</strong></div>
            </div>
            <div style="margin:26px 0;text-align:center;">
              <a href="%s" style="display:inline-block;padding:13px 26px;border-radius:10px;background:#d94848;color:#ffffff;font-size:14px;font-weight:800;text-decoration:none;">개설비 결제하기</a>
            </div>
            """.formatted(escape(amount), escape(dueDate), safePaymentLink);

        sendHtmlEmail(to, "[PETOPIA] 행사 신청이 승인되었습니다",
                "FAIR APPLICATION APPROVED", "행사 신청 승인 안내", content);
    }

    /**
     * 행사 반려 사유도 다른 인증 메일과 동일한 PETOPIA HTML 디자인으로 발송한다.
     */
    public void sendFairRejectionEmail(String to, String rejectReason) {
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              제출하신 행사 신청이 검토 결과 반려되었습니다.<br>
              아래 사유를 확인한 뒤 신청 내용을 보완해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:7px;color:#9ca3af;font-size:12px;font-weight:700;">반려 사유</div>
              <div style="color:#374151;font-size:14px;line-height:1.7;white-space:pre-wrap;">%s</div>
            </div>
            """.formatted(escape(rejectReason));

        sendHtmlEmail(to, "[PETOPIA] 행사 신청이 반려되었습니다",
                "FAIR APPLICATION REJECTED", "행사 신청 반려 안내", content);
    }

    /**
     * 예약확정 안내를 입장 QR 이미지와 함께 발송한다. QR은 프론트엔드가 화면에 그리는 것과
     * 동일한 토큰(reservationId를 결정적으로 서명한 값)을 그대로 이미지로 인코딩한 것이라
     * 이메일 QR과 화면 QR은 완전히 같은 QR이다.
     */
    public void sendReservationConfirmedEmail(String to, String reservationNo, String fairName,
                                               String reservationTypeLabel, LocalDate visitDate,
                                               LocalTime entryStartTime, LocalTime entryEndTime,
                                               long amount, LocalDateTime reservedAt, String qrToken) {
        String amountLabel = amount <= 0 ? "무료" : String.format("%,d원", amount);
        String visitDateLabel = visitDate.format(DateTimeFormatter.ofPattern("yyyy.M.d(E)", java.util.Locale.KOREAN));
        String entryTimeLabel = entryStartTime + " ~ " + entryEndTime;
        String reservedAtLabel = reservedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              결제가 완료되어 예약이 확정되었습니다.<br>
              아래 QR코드를 현장 입장 스캐너에 보여주세요.
            </p>
            <div style="margin:22px 0;text-align:center;">
              <img src="cid:entryQrImage" width="176" height="176" alt="입장 QR" style="display:inline-block;border:1px solid #eadfd4;border-radius:14px;padding:10px;background:#ffffff;">
            </div>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">%s</div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">예약번호 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">예약 유형 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">방문일 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">입장 시간 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">결제 금액 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">예약 확정시각 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(fairName), escape(reservationNo), escape(reservationTypeLabel),
                escape(visitDateLabel), escape(entryTimeLabel), escape(amountLabel), escape(reservedAtLabel));

        sendHtmlEmailWithInlineQr(to, "[PETOPIA] 예약이 확정되었습니다",
                "RESERVATION CONFIRMED", "예약 확정 안내", content, qrToken);
    }

    private void sendHtmlEmailWithInlineQr(String to, String subject, String eyebrow, String title,
                                           String content, String qrToken) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(emailLayout(eyebrow, title, content), true);
            helper.addInline("entryQrImage", new ByteArrayResource(generateQrPng(qrToken)), "image/png");
            javaMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new MailPreparationException("PETOPIA HTML 메일 생성에 실패했습니다.", e);
        }
    }

    private byte[] generateQrPng(String qrToken) {
        try {
            // 프론트(QrCanvas.tsx)가 쓰는 qrcode 라이브러리의 기본 옵션(errorCorrectionLevel: M,
            // margin: 2모듈)과 맞춘다 — 인코딩되는 값은 어차피 동일한 토큰이라 스캔 결과는 같지만,
            // 레벨이 다르면 QR 모듈 패턴 자체가 달라져 화면 QR과 이메일 QR이 다르게 보인다.
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2
            );
            BitMatrix matrix = new QRCodeWriter().encode(qrToken, BarcodeFormat.QR_CODE, 352, 352, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("입장 QR 이미지 생성 실패", e);
            throw new MailPreparationException("입장 QR 이미지 생성에 실패했습니다.", e);
        }
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
