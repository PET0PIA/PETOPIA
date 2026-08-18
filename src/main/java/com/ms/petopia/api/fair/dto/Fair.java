package com.ms.petopia.api.fair.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * fairs 테이블 매핑 객체.
 *
 * <p>신청+운영+장소를 하나로 통합한 테이블이다. 컬럼 구성과 통합 배경은
 * {@code PETOPIA_도메인2_ERD.sql}을 따른다.
 *
 * <p>record가 아니라 세터가 있는 클래스인 이유는 {@code HealthCheckMapper}의 {@code DbHealth}와
 * 동일하다 - MyBatis 기본 매핑이 세터 기반이라서다. API 요청/응답 DTO에는 이 객체를 그대로
 * 노출하지 않는다(reject_reason, submitted_snapshot 등 내부 전용 컬럼이 섞여 있음).
 */
@Getter
@Setter
@ToString
public class Fair {

    private Long fairId;

    // ===== 신청 =====
    /** 신청한 일반 사용자 - users.user_id */
    private Long applicantUserId;
    private String name;
    private String description;
    /** DOG / CAT / ETC */
    private String category;
    private String posterImageUrl;
    private String noticeText;

    // ===== 장소 =====
    /** 마스터 테이블 없이 행사에 직접 종속 */
    private String placeName;
    private String address;
    /** INDOOR / OUTDOOR */
    private String indoorOutdoor;

    // ===== 기간 3종 =====
    private LocalDate vendorRecruitStartDate;
    private LocalDate vendorRecruitEndDate;
    private LocalDate reservationStartDate;
    private LocalDate reservationEndDate;
    private LocalDate operationStartDate;
    private LocalDate operationEndDate;

    // ===== 예약 정책 =====
    /** 관람객 예약금(원). 0이면 무료 */
    private Long reservationFee;
    private Integer reservationCancelDeadlineHours;
    private Integer reservationChangeDeadlineHours;

    // ===== 담당자 =====
    private String managerName;
    private String managerPhone;
    /** 승인 시 이 주소로 EVENT_ADMIN 계정 발송 */
    private String managerEmail;

    // ===== 심사/상태 =====
    private FairStatus status;
    private String rejectReason;
    /** 심사한 SUPER_ADMIN - users.user_id */
    private Long reviewedBy;
    private LocalDateTime reviewedAt;

    // ===== 결제/공개/취소 =====
    /** 승인 시 관리자가 정한 개설비 금액(원). 승인 전에는 null */
    private Long openingFeeAmount;
    /** 개설비 결제 기한. 초과 시 EXPIRED */
    private LocalDateTime paymentDueAt;
    private LocalDateTime publicScheduledAt;
    private LocalDateTime publishedAt;
    /** 취소 승인 일시. NULL이면 취소 아님 */
    private LocalDateTime canceledAt;

    /** 신청 당시 내용 원본(JSON 문자열). 분쟁 대비용, 불필요해지면 컬럼째로 제거 가능 */
    private String submittedSnapshot;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== 조회 계산 값(테이블 컬럼 아님) =====
    /**
     * 공개 목록 조회({@link com.ms.petopia.api.fair.mapper.FairMapper#selectPublicFairs}) 전용 계산 값.
     * 사전예약 가능(예매 기간 안 + 정원 남은 미래 운영일 존재)이면 true. 다른 조회에서는 null이다.
     */
    private Boolean reservable;
    /**
     * 공개 목록 조회 전용 계산 값. 참가기업 부스 모집중(모집공고 마감 전 + 행사 종료 아님 + 빈 슬롯
     * 존재)이면 true. 다른 조회에서는 null이다.
     */
    private Boolean recruiting;
}
