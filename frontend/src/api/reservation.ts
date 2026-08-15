import { apiClient } from "./client";
import { waitingTokenHeader } from "./waitingRoom";

/**
 * 예약/입장 도메인 API.
 *
 * 이 도메인의 관람객 API는 fair/payment 도메인의 임시 X-User-Id 헤더를 받지 않고,
 * 진짜 JWT(Authorization: Bearer)로만 사용자를 식별한다(백엔드 @AuthenticationPrincipal).
 * 아직 프론트에 로그인/토큰 배선이 없어, 개발 중에는 아래 authHeaders()가 붙이는
 * 토큰으로 호출한다.
 */

// TODO 인증 도메인 완성 후 제거. client.ts가 토큰을 자동 주입하면 이 헤더는 불필요하다.
const DEV_ACCESS_TOKEN_KEY = "accessToken";

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem(DEV_ACCESS_TOKEN_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 예약 상태 값 */
export type ReservationStatus =
  | "PENDING_PAYMENT"
  | "CONFIRMED"
  | "CHECKED_IN"
  | "CANCELED"
  | "EXPIRED";

/** 예약 유형: 사전예약 / 현장 직접예매 */
export type ReservationType = "ADVANCE" | "ONSITE_DIRECT";

/**
 * 내 예약 목록의 한 건. (GET /reservations/me)
 * 필드는 백엔드 ReservationListItemResponse에 1:1로 맞춘다.
 * 목록 응답에는 reservationNo·reservationType이 없다(상세 응답에만 있음).
 */
export interface ReservationListItem {
  reservationId: number;
  fairName: string;
  fairPosterImageUrl: string | null;
  /** YYYY-MM-DD */
  visitDate: string;
  /** HH:mm:ss */
  entryStartTime: string;
  entryEndTime: string;
  reservationStatus: ReservationStatus;
  /** 방문일 입장 종료시각이 지났는지 */
  isEnded: boolean;
  /** 입장 QR을 보여줄 수 있는 상태인지 */
  qrAvailable: boolean;
  /** 결제 대기 예약에서 "결제 계속하기"를 보여줄지 */
  paymentAvailable: boolean;
  amount: number;
  /** 예약 확정시각(ISO). 미확정이면 null */
  reservedAt: string | null;
  /** 최초 입장시각(ISO). 입장 전이면 null */
  checkedInAt: string | null;
}

/** 내 예약 목록 응답(페이징). 백엔드 ReservationListResponse에 맞춘다. */
export interface ReservationListResponse {
  items: ReservationListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

/**
 * 예약 단건 상세. (GET /reservations/{id})
 * 목록 필드 + reservationNo·reservationType + 케밥 제어 플래그 2개.
 * canChangeVisitDate·canCancel은 상태·유형 기준의 대략 판단이며,
 * 12시간 마감은 반영돼 있지 않다 → 버튼을 눌러도 R018/R019가 올 수 있으니 응답 처리를 해야 한다.
 */
export interface ReservationDetail extends ReservationListItem {
  /** 예약번호(사람이 읽는 식별자) */
  reservationNo: string;
  /** 방문일 변경 다이얼로그에서 예약 가능 날짜를 조회할 때 쓴다. */
  fairId: number;
  reservationType: ReservationType;
  /** 케밥 "방문일 변경" 노출 여부 */
  canChangeVisitDate: boolean;
  /** 케밥 "예약 취소" 노출 여부 */
  canCancel: boolean;
}

/** 입장 QR 토큰. (GET /reservations/{id}/entry-qr) */
export interface EntryQr {
  reservationId: number;
  qrToken: string;
}

/** 내 예약 목록을 페이징으로 조회한다. size는 최대 50. */
export function getMyReservations(page = 0, size = 20) {
  return apiClient.get<ReservationListResponse>(
    `/api/v1/reservations/me?page=${page}&size=${size}`,
    { headers: authHeaders() },
  );
}

/** 본인 예약 1건의 상세를 조회한다. 없거나 남의 것이면 R010(404). */
export function getReservationDetail(reservationId: number) {
  return apiClient.get<ReservationDetail>(
    `/api/v1/reservations/${reservationId}`,
    { headers: authHeaders() },
  );
}

/** 입장 QR 토큰을 발급/조회한다. 몇 번 호출해도 같은 값(멱등). */
export function getEntryQr(reservationId: number) {
  return apiClient.get<EntryQr>(
    `/api/v1/reservations/${reservationId}/entry-qr`,
    { headers: authHeaders() },
  );
}

// ── 사전예약(2단계): 예매 가능 날짜 조회 + 생성 ─────────────────────────────

/**
 * 유료 사전예약의 약관 버전.
 *
 * 백엔드(ReservationService.validateTerms)는 버전 문자열이 비어있지만 않으면 통과시키고
 * 그대로 원장에 기록한다 — 즉 어떤 값을 보냈는지가 그대로 증적으로 남는다.
 * 화면의 동의 문구가 바뀌면 이 상수도 같이 올린다.
 */
export const ADVANCE_TERMS_VERSION = "advance-paid-v1";

/** 예매 가능 운영일 한 건. 백엔드 ReservationAvailabilityDateResponse에 맞춘다. */
export interface ReservationAvailabilityDate {
  /** YYYY-MM-DD */
  visitDate: string;
  /** HH:mm:ss */
  entryStartTime: string;
  entryEndTime: string;
  remainingCapacity: number;
  /** 예매 가능 여부(마감이면 false) */
  available: boolean;
}

/**
 * 한 행사의 예약 가능 정보. (GET /fairs/{fairId}/reservation-availability)
 * 예약 도메인이 아는 값만 준다 — 행사 이름·장소는 여기 없고 fair 도메인에서 가져온다.
 */
export interface ReservationAvailability {
  fairId: number;
  reservationFee: number;
  dates: ReservationAvailabilityDate[];
}

/**
 * 사전예약 생성 요청. (POST /fairs/{fairId}/reservations)
 * 매수는 1매 고정, 금액은 서버 계산이라 보내지 않는다.
 * 약관 동의는 유료 행사에서만 필수다.
 */
export interface CreateAdvanceReservationRequest {
  /** YYYY-MM-DD */
  visitDate: string;
  reservationTermsAgreed?: boolean;
  reservationTermsVersion?: string;
}

/** 예약 생성 결과. 백엔드 CreateReservationResponse에 맞춘다. */
export interface CreateReservationResult {
  reservationId: number;
  reservationNo: string;
  reservationType: ReservationType;
  reservationStatus: ReservationStatus;
  amount: number;
  /** 후속 결제가 필요한지(유료면 true) */
  paymentRequired: boolean;
  /** 유료 예약의 결제 제한시각(ISO). 무료면 null */
  paymentExpiresAt: string | null;
  /** 무료 예약에서 즉시 발급된 입장 QR 토큰. 유료면 null */
  entryQrToken: string | null;
}

/** 예약 가능 날짜·잔여석을 조회한다. 인증 불필요(공개). 접수 중이 아니면 R003. */
export function getReservationAvailability(fairId: number) {
  return apiClient.get<ReservationAvailability>(
    `/api/v1/fairs/${fairId}/reservation-availability`,
  );
}

/**
 * 사전예약을 생성한다. 무료면 즉시 CONFIRMED+QR, 유료면 PENDING_PAYMENT.
 *
 * 대기열을 켠 행사면 X-Waiting-Token 없이는 429(R022)로 막힌다. 토큰이 없을 때는
 * 헤더가 아예 붙지 않으므로, 대기열을 끈 평소에는 요청 모양이 그대로다.
 */
export function createAdvanceReservation(fairId: number, payload: CreateAdvanceReservationRequest) {
  return apiClient.post<CreateReservationResult>(
    `/api/v1/fairs/${fairId}/reservations`,
    payload,
    { headers: { ...authHeaders(), ...waitingTokenHeader(fairId) } },
  );
}

// ── 현장 직접예매(5단계) ──────────────────────────────────────────────────────

/** 유료 현장예매의 약관 버전(취소·환불 불가). 백엔드 ONSITE_TERMS_VERSION과 일치해야 한다. */
export const ONSITE_TERMS_VERSION = "onsite-no-refund-v1";

/**
 * 현장예매 생성 요청. 무료면 본문 없이도 되지만,
 * 유료면 reservationTermsAgreed=true + reservationTermsVersion=onsite-no-refund-v1이 필수다.
 */
export interface CreateOnsiteReservationRequest {
  reservationTermsAgreed?: boolean;
  reservationTermsVersion?: string;
}

/** 현장예매 생성 결과. 백엔드 CreateOnsiteReservationResponse에 맞춘다. */
export interface CreateOnsiteReservationResult {
  reservationId: number;
  reservationNo: string;
  reservationType: ReservationType;
  /** 오늘 날짜 YYYY-MM-DD (서버가 정한다) */
  visitDate: string;
  reservationStatus: ReservationStatus;
  amount: number;
  paymentRequired: boolean;
  paymentExpiresAt: string | null;
  /** 무료면 즉시 발급된 입장 QR 토큰, 유료면 null */
  entryQrToken: string | null;
}

/**
 * 현장 직접예매를 생성한다(오늘 방문, 정원 없음).
 * 접수 아님/시간 지남 R007, 일시중지 R008, 활성 예약 중복 R005.
 */
export function createOnsiteReservation(fairId: number, payload?: CreateOnsiteReservationRequest) {
  return apiClient.post<CreateOnsiteReservationResult>(
    `/api/v1/fairs/${fairId}/onsite-reservations`,
    payload,
    { headers: authHeaders() },
  );
}

// ── 변경·취소(3단계) ────────────────────────────────────────────────────────

/** 방문일 변경 결과. 백엔드 UpdateReservationVisitDateResponse에 맞춘다. */
export interface ChangeVisitDateResult {
  reservationId: number;
  /** 변경 전 방문일 YYYY-MM-DD */
  previousVisitDate: string;
  /** 변경 후 방문일 YYYY-MM-DD */
  visitDate: string;
  /** HH:mm:ss */
  entryStartTime: string;
  entryEndTime: string;
  reservationStatus: ReservationStatus;
}

/**
 * 예약 취소 결과. 백엔드 CancelReservationResponse에 맞춘다.
 *
 * 환불 필드는 "결제까지 끝난 유료 예약을 취소한 경우"에만 채워진다.
 * 무료 예약이나 결제 전(PENDING_PAYMENT) 취소는 환불할 돈이 없어 refunded=false + 나머지 null이다
 * (결제 전 예약에 걸린 결제는 서버가 함께 취소하지만 환불 원장은 만들지 않는다).
 */
export interface CancelReservationResult {
  reservationId: number;
  reservationStatus: ReservationStatus;
  /** 취소시각(ISO) */
  canceledAt: string;
  /** 예약금 환불이 함께 처리됐는지 */
  refunded: boolean;
  /** 환불 PK. 환불이 없으면 null */
  refundId: number | null;
  /** 환불 금액(원). MVP는 전액환불이라 결제금액과 같다. 환불이 없으면 null */
  refundAmount: number | null;
  /** 환불 상태. 모의 환불이라 접수와 동시에 COMPLETED다. 환불이 없으면 null */
  refundStatus: string | null;
}

/**
 * 방문일을 변경한다. ADVANCE·CONFIRMED만 가능.
 * 마감(입장 12시간 전) 초과면 R018, 대상일 불가/마감이면 R002/R004.
 */
export function changeVisitDate(reservationId: number, visitDate: string) {
  return apiClient.patch<ChangeVisitDateResult>(
    `/api/v1/reservations/${reservationId}/visit-date`,
    { visitDate },
    { headers: authHeaders() },
  );
}

/**
 * 예약을 취소한다. reason은 선택(최대 500자).
 *
 * 유료 확정 예약이면 서버가 예약금을 전액 환불하고 CANCELED로 전환한다
 * (응답 refunded=true + refundAmount). 현장예매(ONSITE_DIRECT)는 자진취소 대상이 아니라 R013.
 *
 * 실패 코드: R019 취소 마감 초과 / R013 취소 불가 상태 / R020 환불할 결제 없음 /
 * R021 결제 진행 중(일시적 — 잠시 후 재시도하면 풀린다).
 */
export function cancelReservation(reservationId: number, reason?: string) {
  return apiClient.patch<CancelReservationResult>(
    `/api/v1/reservations/${reservationId}/cancel`,
    reason ? { reason } : undefined,
    { headers: authHeaders() },
  );
}

// ── 현장예매 정책 관리(4단계, 관리자) ────────────────────────────────────────
// /api/v1/admin/** 는 EVENT_ADMIN·SUPER_ADMIN 역할의 JWT가 필요하다(authHeaders 사용).

/** 현장예매 판매 상태. OPEN=판매중 / PAUSED=일시중지 / CLOSED=마감 */
export type OnsiteSalesStatus = "OPEN" | "PAUSED" | "CLOSED";

/** 운영일별 현장예매 정책. 백엔드 OnsiteSalesPolicyResponse에 맞춘다. */
export interface OnsiteSalesPolicy {
  fairId: number;
  fairDateId: number;
  /** YYYY-MM-DD */
  operationDate: string;
  price: number;
  status: OnsiteSalesStatus;
  /** 낙관적 락 버전. 미설정이면 0. */
  version: number;
  updatedAt: string | null;
}

/** 현장예매 정책 저장 요청. 백엔드 UpdateOnsiteSalesPolicyRequest에 맞춘다. */
export interface SaveOnsiteSalesPolicyRequest {
  price: number;
  status: OnsiteSalesStatus;
  /** 조회 때 받은 version. 신규(미설정)면 0 또는 null. 불일치 시 R009. */
  expectedVersion: number | null;
}

/** 운영일의 현장예매 정책을 조회한다. 미설정이면 price=0·CLOSED·version=0. */
export function getOnsiteSalesPolicy(fairId: number, fairDateId: number) {
  return apiClient.get<OnsiteSalesPolicy>(
    `/api/v1/admin/fairs/${fairId}/dates/${fairDateId}/onsite-sales-policy`,
    { headers: authHeaders() },
  );
}

/** 현장예매 정책을 저장한다. 버전 불일치(다른 관리자가 먼저 변경)면 R009. */
export function saveOnsiteSalesPolicy(
  fairId: number,
  fairDateId: number,
  payload: SaveOnsiteSalesPolicyRequest,
) {
  return apiClient.put<OnsiteSalesPolicy>(
    `/api/v1/admin/fairs/${fairId}/dates/${fairDateId}/onsite-sales-policy`,
    payload,
    { headers: authHeaders() },
  );
}

// ── 게이트 입장 스캔(6단계, 운영자) ──────────────────────────────────────────

/** 게이트 스캔 결과 코드. 실패도 예외가 아니라 이 코드로 온다(스캔 감사 로그 보존 목적). */
export type GateScanResultCode =
  | "FIRST_ENTRY" // 최초 입장 성공
  | "ALREADY_CHECKED_IN" // 이미 입장(재스캔)
  | "NOT_FOUND" // 토큰에 해당하는 QR 없음
  | "FAIR_MISMATCH" // 다른 행사 QR
  | "NOT_AVAILABLE" // 입장 가능 시간 밖
  | "INVALID_RESERVATION_STATUS"; // 예약이 취소/만료 등

/** 게이트 스캔 결과. 백엔드 GateScanResponse에 맞춘다. */
export interface GateScanResult {
  resultCode: GateScanResultCode;
  firstEntry: boolean;
  /** 입장 경로 ADVANCE / ONSITE_DIRECT 등. 실패 시 null일 수 있음 */
  entrySource: string | null;
  /** 최초 입장시각(ISO). 실패 시 null */
  firstCheckedInAt: string | null;
}

/** 입장 QR을 스캔해 입장 처리한다(운영자). 실패도 200 + resultCode로 온다. */
export function scanGateEntry(fairId: number, qrToken: string, deviceInfo?: string) {
  return apiClient.post<GateScanResult>(
    `/api/v1/admin/fairs/${fairId}/gate-entries/scan`,
    { qrToken, deviceInfo },
    { headers: authHeaders() },
  );
}

// ── 부스 방문 스캔(7단계, 참가업체 VENDOR) ────────────────────────────────────
// /api/v1/vendor/** 는 VENDOR 역할의 JWT가 필요하다(authHeaders 사용).

/** 부스 스캔 결과 코드. 게이트와 동일한 검증에 방문 전용 코드(FIRST_VISIT/ALREADY_VISITED)를 쓴다. */
export type BoothScanResultCode =
  | "FIRST_VISIT" // 최초 방문(사은품 지급 가능)
  | "ALREADY_VISITED" // 이미 방문(중복 수령 차단)
  | "NOT_FOUND"
  | "FAIR_MISMATCH"
  | "NOT_AVAILABLE"
  | "INVALID_RESERVATION_STATUS";

/** 부스 스캔 결과. 관람객 개인정보는 담지 않는다. 백엔드 BoothScanResponse에 맞춘다. */
export interface BoothScanResult {
  resultCode: BoothScanResultCode;
  firstVisit: boolean;
  /** 이 (예약,부스) 조합의 누적 방문 횟수 */
  visitCount: number;
  /** 최초 방문시각(ISO). 실패 시 null */
  firstVisitedAt: string | null;
}

/** 부스 QR을 스캔해 방문 처리한다(참가업체). 실패도 200 + resultCode로 온다. */
export function scanBoothVisit(boothId: number, qrToken: string) {
  return apiClient.post<BoothScanResult>(
    `/api/v1/vendor/booths/${boothId}/booth-visits/scan`,
    { qrToken },
    { headers: authHeaders() },
  );
}
