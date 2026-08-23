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
     * fairName은 조회 실패 등으로 없으면 "-"로 표시한다.
     */
    public void sendFairApprovalEmail(String to, String fairName, Long openingFeeAmount,
                                      LocalDateTime paymentDueAt, String paymentLink) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String safePaymentLink = escape(paymentLink);
        String amount = openingFeeAmount == null ? "-" : String.format("%,d원", openingFeeAmount);
        String dueDate = paymentDueAt == null ? "-" : paymentDueAt.toLocalDate().toString();
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              행사 신청이 승인되어 현재 로그인 계정에 행사 관리 권한이 추가되었습니다.<br>
              아래 내용을 확인한 뒤 기한 내에 개설비를 결제해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">개설비 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">결제 기한 <strong style="float:right;color:#111827;">%s까지</strong></div>
            </div>
            <div style="margin:26px 0;text-align:center;">
              <a href="%s" style="display:inline-block;padding:13px 26px;border-radius:10px;background:#d94848;color:#ffffff;font-size:14px;font-weight:800;text-decoration:none;">개설비 결제하기</a>
            </div>
            """.formatted(escape(safeFairName), escape(amount), escape(dueDate), safePaymentLink);

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "행사 신청이 승인되었습니다",
                "FAIR APPLICATION APPROVED", "행사 신청 승인 안내", content);
    }

    /**
     * 행사 반려 사유도 다른 인증 메일과 동일한 PETOPIA HTML 디자인으로 발송한다.
     */
    public void sendFairRejectionEmail(String to, String fairName, String rejectReason) {
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

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "행사 신청이 반려되었습니다",
                "FAIR APPLICATION REJECTED", "행사 신청 반려 안내", content);
    }

    /** 행사 취소 신청 승인 안내. fairName은 조회 실패 등으로 없으면 "-"로 표시한다. */
    public void sendFairCancelRequestApprovedEmail(String to, String fairName) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              신청하신 행사 취소가 승인되어 취소가 확정되었습니다.<br>
              결제된 금액은 환불 절차가 진행되는 대로 별도로 안내드립니다.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(safeFairName));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "취소 신청이 승인되었습니다",
                "FAIR CANCEL REQUEST APPROVED", "행사 취소 신청 승인 안내", content);
    }

    /** 행사 취소 신청 반려 안내. fairName은 조회 실패 등으로 없으면 "-"로 표시한다. */
    public void sendFairCancelRequestRejectedEmail(String to, String fairName, String rejectReason) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              신청하신 행사 취소가 반려되었습니다.<br>
              아래 사유를 확인해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:7px;color:#9ca3af;font-size:12px;font-weight:700;">반려 사유</div>
              <div style="color:#374151;font-size:14px;line-height:1.7;white-space:pre-wrap;">%s</div>
            </div>
            """.formatted(escape(safeFairName), escape(rejectReason));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "취소 신청이 반려되었습니다",
                "FAIR CANCEL REQUEST REJECTED", "행사 취소 신청 반려 안내", content);
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

        sendHtmlEmailWithInlineQr(to, "[PETOPIA] " + fairNamePrefix(fairName) + "예약이 확정되었습니다",
                "RESERVATION CONFIRMED", "예약 확정 안내", content, qrToken);
    }

    /**
     * 결제 완료 안내(참가비/개설비 등 — 예약금은 sendReservationConfirmedEmail이 대신 보낸다).
     * fairName은 조회 실패 등으로 없으면 "-"로 표시한다.
     */
    public void sendPaymentCompletedEmail(String to, String fairName, long amount, String paymentMethodLabel, LocalDateTime paidAt) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String amountLabel = String.format("%,d원", amount);
        String paidAtLabel = paidAt == null ? "-" : paidAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              결제가 정상적으로 완료되었습니다.<br>
              아래 결제 내역을 확인해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">결제 금액 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">결제 수단 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">결제 일시 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(safeFairName), escape(amountLabel),
                escape(paymentMethodLabel == null ? "-" : paymentMethodLabel), escape(paidAtLabel));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "결제가 완료되었습니다", "PAYMENT COMPLETED", "결제 완료 안내", content);
    }

    /** 환불 완료 안내. fairName은 어느 행사의 결제였는지 표시하는 용도 - 조회 실패 등으로 없으면 "-"로 표시한다. */
    public void sendRefundCompletedEmail(String to, String fairName, long refundAmount) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              요청하신 환불이 정상적으로 처리되었습니다.<br>
              카드사에 따라 영업일 기준 3~5일 이내 반영됩니다.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">환불 금액 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(safeFairName), escape(String.format("%,d원", refundAmount)));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "환불이 완료되었습니다", "REFUND COMPLETED", "환불 완료 안내", content);
    }

    /**
     * 정산 확정 안내. settlementLabel은 "정산" 또는 "최종정산"처럼 SettlementService/FairSettlementService가
     * 구분해서 넘긴다. fairName은 조회 실패 등으로 없으면 "-"로 표시한다.
     */
    public void sendSettlementCompletedEmail(String to, String fairName, String settlementLabel, Long settlementId,
                                             long grossAmount, long refundAmount, long commissionAmount, long netAmount) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              담당하시는 <strong style="color:#111827;">%s</strong> 행사의 %s이(가) 확정 처리되었습니다.<br>
              아래 정산 내역을 확인해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">정산 ID <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">총 결제액 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">환불액 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">수수료 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">정산액 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(safeFairName), escape(settlementLabel), escape(safeFairName), escape(String.valueOf(settlementId)),
                escape(String.format("%,d원", grossAmount)), escape(String.format("%,d원", refundAmount)),
                escape(String.format("%,d원", commissionAmount)), escape(String.format("%,d원", netAmount)));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + settlementLabel + "이 확정되었습니다",
                "SETTLEMENT COMPLETED", settlementLabel + " 확정 안내", content);
    }

    /**
     * 행사 취소 안내 — 취소 승인 직후, 아직 환불이 처리되기 전에 보낸다(환불 자체는 배치로
     * 순차 처리되어 시간이 걸릴 수 있어서 "예정" 표현을 쓴다). 실제 환불이 끝나면
     * sendRefundCompletedEmail이 별도로 완료를 알린다.
     */
    public void sendFairCanceledEmail(String to, String fairName, long paidAmount) {
        String safeFairName = fairName == null || fairName.isBlank() ? "참가하신 행사" : fairName;
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              <strong style="color:#111827;">%s</strong> 행사가 부득이한 사정으로 취소되었습니다.<br>
              결제하신 금액은 순차적으로 환불될 예정이며, 환불이 완료되면 별도로 다시 안내드립니다.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">환불 예정 금액 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(safeFairName), escape(safeFairName), escape(String.format("%,d원", paidAmount)));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "행사가 취소되었습니다", "FAIR CANCELED", "행사 취소 안내", content);
    }

    /** 예약 방문일 변경 안내. fairName은 조회 실패 등으로 없으면 "-"로 표시한다. */
    public void sendReservationChangedEmail(String to, String fairName, LocalDate oldVisitDate, LocalDate newVisitDate,
                                            LocalTime entryStartTime, LocalTime entryEndTime) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy.M.d(E)", java.util.Locale.KOREAN);
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              예약하신 방문일이 아래와 같이 변경되었습니다.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">기존 방문일 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">변경된 방문일 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">입장 시간 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(safeFairName), escape(oldVisitDate.format(dateFormat)), escape(newVisitDate.format(dateFormat)),
                escape(entryStartTime + " ~ " + entryEndTime));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "예약 날짜가 변경되었습니다",
                "RESERVATION CHANGED", "예약 날짜 변경 안내", content);
    }

    /**
     * 예약 취소 안내(유료 예약은 별도로 sendRefundCompletedEmail도 나간다 — 취소됐다는 사실과 환불됐다는 사실은 서로 다른 안내라 둘 다 유지).
     * fairName은 조회 실패 등으로 없으면 "-"로 표시한다.
     */
    public void sendReservationCanceledEmail(String to, String fairName, LocalDate visitDate, long amount) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String visitLabel = visitDate == null ? "-"
                : visitDate.format(DateTimeFormatter.ofPattern("yyyy.M.d(E)", java.util.Locale.KOREAN));
        String amountLabel = amount <= 0 ? "무료" : String.format("%,d원", amount);
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              예약이 정상적으로 취소 처리되었습니다.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">방문 예정일 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">예약 금액 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(safeFairName), escape(visitLabel), escape(amountLabel));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "예약이 취소되었습니다",
                "RESERVATION CANCELED", "예약 취소 안내", content);
    }

    /**
     * 참가 신청 승인 — 참가비 결제 안내와 함께 내 참가신청 목록(/vendor/participations)으로 가는 버튼을 넣는다.
     * fairName은 조회 실패 등으로 없으면 "-"로 표시한다.
     */
    public void sendVendorApplicationApprovedEmail(String to, String fairName, Long finalPrice, LocalDate paymentDueAt,
                                                    String participationsLink) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String amount = finalPrice == null ? "-" : String.format("%,d원", finalPrice);
        String dueDate = paymentDueAt == null ? "-" : paymentDueAt.toString();
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              참가 신청이 승인되었습니다.<br>
              아래 내용을 확인한 뒤 기한 내에 참가비를 결제해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">참가비 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="color:#6b7280;font-size:13px;">결제 기한 <strong style="float:right;color:#111827;">%s까지</strong></div>
            </div>
            <div style="margin:26px 0;text-align:center;">
              <a href="%s" style="display:inline-block;padding:13px 26px;border-radius:10px;background:#d94848;color:#ffffff;font-size:14px;font-weight:800;text-decoration:none;">참가신청 내역 확인하기</a>
            </div>
            """.formatted(escape(safeFairName), escape(amount), escape(dueDate), escape(participationsLink));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "참가 신청이 승인되었습니다",
                "APPLICATION APPROVED", "참가 신청 승인 안내", content);
    }

    /** 참가 신청 반려. fairName은 조회 실패 등으로 없으면 "-"로 표시한다. */
    public void sendVendorApplicationRejectedEmail(String to, String fairName, String rejectReason) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              제출하신 참가 신청이 검토 결과 반려되었습니다.<br>
              아래 사유를 확인해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:8px;color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
              <div style="margin-bottom:7px;color:#9ca3af;font-size:12px;font-weight:700;">반려 사유</div>
              <div style="color:#374151;font-size:14px;line-height:1.7;white-space:pre-wrap;">%s</div>
            </div>
            """.formatted(escape(safeFairName), escape(rejectReason));

        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "참가 신청이 반려되었습니다",
                "APPLICATION REJECTED", "참가 신청 반려 안내", content);
    }

    /** 참가 취소 요청 승인. fairName은 조회 실패 등으로 없으면 "-"로 표시한다. */
    public void sendVendorApplicationCancelApprovedEmail(String to, String fairName) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              요청하신 참가 취소가 승인되어 신청이 취소 처리되었습니다.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(safeFairName));
        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "참가 취소 요청이 승인되었습니다",
                "CANCEL APPROVED", "참가 취소 승인 안내", content);
    }

    /** 참가 취소 요청 반려. fairName은 조회 실패 등으로 없으면 "-"로 표시한다. */
    public void sendVendorApplicationCancelRejectedEmail(String to, String fairName) {
        String safeFairName = fairName == null || fairName.isBlank() ? "-" : fairName;
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              요청하신 참가 취소가 반려되어 신청이 그대로 유지됩니다.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="color:#6b7280;font-size:13px;">행사명 <strong style="float:right;color:#111827;">%s</strong></div>
            </div>
            """.formatted(escape(safeFairName));
        sendHtmlEmail(to, "[PETOPIA] " + fairNamePrefix(fairName) + "참가 취소 요청이 반려되었습니다",
                "CANCEL REJECTED", "참가 취소 반려 안내", content);
    }

    /** 사업자 등록 승인. */
    public void sendBusinessApprovedEmail(String to) {
        String content = """
            <p style="margin:0;color:#4b5563;font-size:15px;line-height:1.7;">
              사업자 등록이 승인되어 이제 행사 참가 신청이 가능합니다.
            </p>
            """;
        sendHtmlEmail(to, "[PETOPIA] 사업자 등록이 승인되었습니다", "BUSINESS APPROVED", "사업자 등록 승인 안내", content);
    }

    /** 사업자 등록 반려. */
    public void sendBusinessRejectedEmail(String to, String rejectReason) {
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              제출하신 사업자 등록이 검토 결과 반려되었습니다.<br>
              아래 사유를 확인해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:7px;color:#9ca3af;font-size:12px;font-weight:700;">반려 사유</div>
              <div style="color:#374151;font-size:14px;line-height:1.7;white-space:pre-wrap;">%s</div>
            </div>
            """.formatted(escape(rejectReason));

        sendHtmlEmail(to, "[PETOPIA] 사업자 등록이 반려되었습니다", "BUSINESS REJECTED", "사업자 등록 반려 안내", content);
    }

    /** 사업자 등록 취소(박탈). */
    public void sendBusinessRevokedEmail(String to, String revokeReason) {
        String content = """
            <p style="margin:0 0 20px;color:#4b5563;font-size:15px;line-height:1.7;">
              사업자 등록이 취소되었습니다.<br>
              아래 사유를 확인해 주세요.
            </p>
            <div style="margin:22px 0;padding:18px 20px;border:1px solid #eadfd4;border-radius:14px;background:#faf7f3;">
              <div style="margin-bottom:7px;color:#9ca3af;font-size:12px;font-weight:700;">취소 사유</div>
              <div style="color:#374151;font-size:14px;line-height:1.7;white-space:pre-wrap;">%s</div>
            </div>
            """.formatted(escape(revokeReason));

        sendHtmlEmail(to, "[PETOPIA] 사업자 등록이 취소되었습니다", "BUSINESS REVOKED", "사업자 등록 취소 안내", content);
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

    /** 이메일 제목에 "'행사명' " 접두어를 붙인다. 행사명을 모르면(조회 실패 등) 빈 문자열 - 접두어 없이 기존 제목 그대로 나간다. */
    private String fairNamePrefix(String fairName) {
        return fairName == null || fairName.isBlank() ? "" : "'" + fairName + "' ";
    }
}
