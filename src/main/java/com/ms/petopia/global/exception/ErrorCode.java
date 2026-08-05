package com.ms.petopia.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 예외에 사용할 에러 코드.
 *
 * <p>내부 코드 규칙: [도메인 약어][3자리 일련번호] (예: C001, U001)
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== Common =====
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 내부 오류가 발생했습니다."),

    // ===== User =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "존재하지 않는 사용자입니다."),
    DUPLICATED_EMAIL(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),

    // ===== Auth =====
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "A003", "이메일 혹은 비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "A004", "유효하지 않은 인증 코드입니다."),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "A005", "인증 코드가 만료되었습니다."),
    TOKEN_ALREADY_USED(HttpStatus.BAD_REQUEST, "A006", "이미 사용된 인증 코드입니다."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "A007", "이미 인증된 이메일입니다."),
    TOO_MANY_VERIFY_ATTEMPTS(HttpStatus.BAD_REQUEST, "A008", "인증 시도 횟수를 초과했습니다. 인증 코드를 다시 받아주세요."),
    RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "A009", "인증 코드 재전송은 잠시 후 다시 시도해주세요."),
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "A010", "이메일 인증이 완료되지 않은 이메일입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A011", "유효하지 않거나 만료된 토큰입니다. 다시 로그인해주세요."),

    // ===== Storage =====
    STORAGE_UNSUPPORTED_EXTENSION(HttpStatus.BAD_REQUEST, "S001", "허용하지 않는 파일 확장자입니다."),
    STORAGE_CONTENT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "S002", "파일 확장자와 Content-Type이 일치하지 않습니다."),
    STORAGE_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "S003", "허용 용량을 초과했습니다."),
    STORAGE_INVALID_OBJECT_KEY(HttpStatus.BAD_REQUEST, "S004", "잘못된 임시 파일 키입니다."),
    STORAGE_UPLOAD_NOT_FOUND(HttpStatus.BAD_REQUEST, "S005", "업로드된 파일을 찾을 수 없습니다."),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "S006", "파일 저장소에 일시적으로 연결할 수 없습니다."),
    STORAGE_UPLOAD_CHANGED(HttpStatus.CONFLICT, "S007", "파일이 업로드 후 변경되어 확정할 수 없습니다."),

    // ===== Vendor (참가업체·부스) =====
    BUSINESS_NOT_FOUND(HttpStatus.NOT_FOUND, "V001", "사업자를 찾을 수 없습니다."),
    RECRUIT_NOTICE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "V002", "해당 공고에 대한 권한이 없습니다."),
    RECRUIT_NOTICE_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "V003", "모집 공고 등록에 실패했습니다."),
    RECRUIT_NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "V004", "아직 작성된 모집 공고가 없습니다."),
    NTS_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "V005", "사업자 진위확인 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요."),
    BUSINESS_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "V006", "사업자 정보를 확인할 수 없습니다. 입력하신 정보를 다시 확인해주세요."),
    BUSINESS_DUPLICATE(HttpStatus.CONFLICT, "V007", "이미 등록된 사업자등록번호입니다."),
    APPLICATION_DUPLICATE_ACTIVE(HttpStatus.CONFLICT, "V008", "이미 진행 중인 신청이 존재합니다."),
    BOOTH_SLOT_ALREADY_LOCKED(HttpStatus.CONFLICT, "V009", "이미 다른 신청에서 선택된 부스 슬롯이 포함되어 있습니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "V010", "이용약관에 동의해야 신청서를 제출할 수 있습니다."),
    RECRUIT_CLOSED(HttpStatus.CONFLICT, "V011", "모집이 마감되어 신청할 수 없습니다."),

    // ===== Payment =====
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "존재하지 않는 결제입니다."),
    PAYMENT_TARGET_NOT_PAYABLE(HttpStatus.CONFLICT, "P002", "결제할 수 없는 상태입니다."),
    PAYMENT_APPROVAL_FAILED(HttpStatus.PAYMENT_REQUIRED, "P003", "결제 승인에 실패했습니다."),
    PAYMENT_GATEWAY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "P004", "결제 서비스에 일시적으로 연결할 수 없습니다."),

    // ===== Fair =====
    FAIR_INVALID_VENDOR_RECRUIT_PERIOD(HttpStatus.BAD_REQUEST, "F001", "참가업체 모집 종료일이 시작일보다 빠릅니다."),
    FAIR_INVALID_RESERVATION_PERIOD(HttpStatus.BAD_REQUEST, "F002", "예약 기간 종료일이 시작일보다 빠릅니다."),
    FAIR_INVALID_OPERATION_PERIOD(HttpStatus.BAD_REQUEST, "F003", "행사 운영 종료일이 시작일보다 빠릅니다."),
    FAIR_NOT_FOUND(HttpStatus.NOT_FOUND, "F004", "존재하지 않는 행사입니다."),
    HALL_NOT_FOUND(HttpStatus.NOT_FOUND, "F005", "존재하지 않는 홀이거나 해당 행사의 홀이 아닙니다."),
    FAIR_NOT_PENDING_REVIEW(HttpStatus.CONFLICT, "F006", "심사 대기 중인 신청서만 검토할 수 있습니다."),
    FAIR_REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "F007", "반려 시 반려 사유를 입력해야 합니다."),
    BOOTH_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "F008", "존재하지 않는 부스 슬롯이거나 해당 홀의 슬롯이 아닙니다."),
    BOOTH_SLOT_LOCKED(HttpStatus.CONFLICT, "F009", "이미 신청이 걸려 위치·번호·가격 변경 및 삭제가 제한된 부스 슬롯입니다."),
    BOOTH_SLOT_DUPLICATE_NUMBER(HttpStatus.BAD_REQUEST, "F010", "같은 홀 안에 중복된 부스 번호가 있습니다."),
    BOOTH_LAYOUT_VERSION_CONFLICT(HttpStatus.CONFLICT, "F011", "다른 곳에서 이미 이 홀의 배치를 저장했습니다. 최신 상태를 다시 불러와 주세요."),
    BOOTH_SLOT_DUPLICATE_REFERENCE(HttpStatus.BAD_REQUEST, "F012", "같은 부스 슬롯을 요청 안에서 두 번 이상 참조했습니다."),
    FAIR_DATE_NOT_FOUND(HttpStatus.NOT_FOUND, "F013", "존재하지 않는 운영일이거나 해당 행사의 운영일이 아닙니다."),
    FAIR_DATE_DUPLICATE(HttpStatus.CONFLICT, "F014", "이미 등록된 운영 날짜입니다."),
    FAIR_DATE_OUT_OF_OPERATION_PERIOD(HttpStatus.BAD_REQUEST, "F015", "운영 날짜가 행사 운영 기간을 벗어났습니다."),
    FAIR_DATE_INVALID_ENTRY_TIME(HttpStatus.BAD_REQUEST, "F016", "입장 종료 시간이 입장 시작 시간보다 빠릅니다."),

    // ===== Reservation =====
    RESERVATION_FAIR_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "예약할 행사를 찾을 수 없습니다."),
    RESERVATION_DATE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "R002", "예약할 수 없는 방문 날짜입니다."),
    RESERVATION_NOT_OPEN(HttpStatus.CONFLICT, "R003", "현재 예약을 접수하지 않는 행사입니다."),
    RESERVATION_SOLD_OUT(HttpStatus.CONFLICT, "R004", "선택한 날짜의 예약이 마감되었습니다."),
    DUPLICATED_RESERVATION(HttpStatus.CONFLICT, "R005", "이미 활성 예약이 존재합니다."),
    RESERVATION_TERMS_REQUIRED(HttpStatus.BAD_REQUEST, "R006", "유료 예약의 취소·환불 약관 동의가 필요합니다."),
    ONSITE_RESERVATION_CLOSED(HttpStatus.CONFLICT, "R007", "현재 현장예매를 접수하지 않습니다."),
    ONSITE_RESERVATION_PAUSED(HttpStatus.CONFLICT, "R008", "현장예매가 일시 중지되었습니다."),
    ONSITE_SALES_POLICY_CONFLICT(HttpStatus.CONFLICT, "R009", "현장예매 정책이 다른 관리자에 의해 변경되었습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R010", "예약을 찾을 수 없습니다."),
    RESERVATION_PAYMENT_EXPIRED(HttpStatus.CONFLICT, "R011", "예약의 결제 제한시간이 지났습니다."),
    RESERVATION_PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "R012", "예약금과 결제금액이 일치하지 않습니다."),
    RESERVATION_STATUS_CONFLICT(HttpStatus.CONFLICT, "R013", "현재 예약 상태에서는 요청을 처리할 수 없습니다."),
    RESERVATION_PAYMENT_EVENT_CONFLICT(HttpStatus.CONFLICT, "R014", "이미 다른 결제로 확정된 예약이거나 중복된 결제 이벤트입니다."),
    ENTRY_QR_NOT_FOUND(HttpStatus.NOT_FOUND, "R015", "유효한 입장 QR을 찾을 수 없습니다."),
    ENTRY_QR_NOT_AVAILABLE(HttpStatus.CONFLICT, "R016", "현재 사용할 수 없는 입장 QR입니다."),
    ENTRY_FAIR_MISMATCH(HttpStatus.FORBIDDEN, "R017", "해당 행사의 입장 QR이 아닙니다."),
    RESERVATION_CHANGE_DEADLINE_EXCEEDED(HttpStatus.CONFLICT, "R018", "방문 날짜 변경 가능 시간이 지났습니다."),
    RESERVATION_CANCEL_DEADLINE_EXCEEDED(HttpStatus.CONFLICT, "R019", "예약 취소 가능 시간이 지났습니다."),

    // ===== Notification =====
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "존재하지 않는 알림입니다."),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "N002", "해당 알림에 접근 권한이 없습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
