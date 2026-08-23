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
  /**
   * 결제 대기 예약의 결제 제한시각(ISO). 남은 시간 카운트다운에 쓴다.
   * 결제 대기가 아니거나 무료 예약이면 null.
   */
  paymentExpiresAt: string | null;
  amount: number;
  /** 예약 확정시각(ISO). 미확정이면 null */
  reservedAt: string | null;
  /** 최초 입장시각(ISO). 입장 전이면 null */
  checkedInAt: string | null;
  /**
   * 예약금 환불 상태. 환불이 없으면 null.
   *
   * "취소됨" 배지만으로는 환불받은 취소·결제 전 취소·무료 예약 취소가 구분되지 않아서
   * 목록 카드의 금액줄 표시를 이 값으로 가른다. 지금 백엔드는 모의 환불이라 접수와 동시에
   * COMPLETED가 되므로 REQUESTED는 실제로 오지 않지만, 실 PG 연동이 붙으면 생기는 상태라
   * 타입에 남겨 둔다.
   */
  refundStatus: "REQUESTED" | "COMPLETED" | "REJECTED" | null;
  /**
   * 주최측 행사 취소로 자동 취소된 예약인지.
   * 내가 직접 취소한 건과 구분해서 안내 문구를 다르게 보여준다.
   */
  canceledByFairCancellation: boolean;
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
 * 목록 필드 + reservationNo·reservationType + 케밥 제어 플래그 2개 + 취소·변경 마감 시각.
 * canChangeVisitDate·canCancel에는 마감까지 반영돼 있지만, 화면을 열어둔 채 마감을 넘기면
 * 여전히 R018/R019가 올 수 있으니 응답 처리는 그대로 해야 한다.
 *
 * refundStatus만 상속에서 뺀다 - 목록 카드가 금액줄 표시를 가르려고 쓰는 값이라
 * 상세 응답(ReservationDetailResponse)에는 없다. 상세 화면은 결제 레코드를 따로 조회해
 * 환불 상태·금액·사유·시각을 "환불 정보" 블록에 전부 보여주므로 이 값이 필요 없다.
 * 상속에 남겨두면 타입은 non-null이라고 하는데 실제로는 undefined가 오는 함정이 된다.
 */
export interface ReservationDetail extends Omit<ReservationListItem, "refundStatus"> {
  /** 예약번호(사람이 읽는 식별자) */
  reservationNo: string;
  /** 방문일 변경 다이얼로그에서 예약 가능 날짜를 조회할 때 쓴다. */
  fairId: number;
  reservationType: ReservationType;
  /** 케밥 "방문일 변경" 노출 여부 */
  canChangeVisitDate: boolean;
  /** 케밥 "예약 취소" 노출 여부 */
  canCancel: boolean;
  /**
   * 방문일 변경 마감 시각(ISO). 행사가 정한 기한(없으면 입장 12시간 전)이다.
   * 계산할 수 없거나 행사 설정이 잘못된 경우 null.
   */
  changeDeadlineAt: string | null;
  /** 예약 취소 마감 시각(ISO). 결제 대기 예약의 취소에는 적용되지 않는다. */
  cancelDeadlineAt: string | null;
  /** 예약금 결제의 ID. 무료 예약(결제 행 없음)이면 null */
  paymentId: number | null;
  /** 표시용 결제수단("카드", "간편결제 (네이버페이)"). 결제 완료 전이면 "결제 전", 무료 예약이면 null */
  paymentMethod: string | null;
  /** 동반 반려동물의 예약 시점 스냅샷. 동반이 없으면 빈 배열. */
  pets: ReservationPet[];
}

/**
 * 예약에 담긴 동반 반려동물 1마리(예약 시점 스냅샷).
 * 원본(마이페이지 반려동물)이 수정·삭제돼도 이 값은 변하지 않는다.
 * 백엔드 ReservationPetResponse에 맞춘다.
 */
export interface ReservationPet {
  reservationPetId: number;
  /** 원본 pets.pet_id. 원본이 지워졌을 수도 있으니 표시는 아래 스냅샷 값으로 한다. */
  petId: number;
  name: string;
  species: string;
  breed: string | null;
  /** YYYY-MM-DD */
  birthDate: string | null;
  /** null=미입력 / false=없음 / true=있음 */
  hasAllergy: boolean | null;
  allergies: ReservationPetAllergy[];
}

/** 예약 시점 알레르기 1건. requiresText=true인 항목만 otherText가 채워져 있다. */
export interface ReservationPetAllergy {
  allergyTypeId: number;
  code: string;
  category: string;
  label: string;
  requiresText: boolean;
  otherText: string | null;
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

// ── 사전예약(2단계): 예약 가능 날짜 조회 + 생성 ─────────────────────────────

/**
 * 유료 사전예약의 약관 버전.
 *
 * 백엔드(ReservationService.validateTerms)는 버전 문자열이 비어있지만 않으면 통과시키고
 * 그대로 원장에 기록한다 — 즉 어떤 값을 보냈는지가 그대로 증적으로 남는다.
 * 화면의 동의 문구가 바뀌면 이 상수도 같이 올린다.
 */
export const ADVANCE_TERMS_VERSION = "advance-paid-v1";

/** 예약 가능 운영일 한 건. 백엔드 ReservationAvailabilityDateResponse에 맞춘다. */
export interface ReservationAvailabilityDate {
  /** YYYY-MM-DD */
  visitDate: string;
  /** HH:mm:ss */
  entryStartTime: string;
  entryEndTime: string;
  remainingCapacity: number;
  /**
   * 잔여석 기준 예약 가능 여부(마감이면 false).
   * "내가 이미 예약한 날"인지는 여기 섞여 있지 않다 - 아래 myReservationId로 따로 판단한다.
   */
  available: boolean;
  /**
   * 로그인 사용자가 이 날짜에 이미 잡아둔 예약의 ID. 없거나 비로그인이면 null.
   * 값이 있으면 같은 날짜로 또 예약할 수 없다(서버가 R005로 막는다).
   */
  myReservationId: number | null;
  /** 그 예약의 상태. 결제 대기면 상세에서 결제를 이어갈 수 있다. */
  myReservationStatus: ReservationStatus | null;
}

/**
 * 한 행사의 예약 가능 정보. (GET /fairs/{fairId}/reservation-availability)
 * 예약 도메인이 아는 값만 준다 — 행사 이름·장소는 여기 없고 fair 도메인에서 가져온다.
 */
export interface ReservationAvailability {
  fairId: number;
  reservationFee: number;
  /** 반려동물 동반 가능 여부. false면 예약 화면이 반려동물 선택 UI를 아예 띄우지 않는다. */
  petAllowed: boolean;
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
  /**
   * 함께 갈 반려동물의 petId 목록. 안 보내거나 빈 배열이면 동반 없이 예약한다.
   * 동반 금지 행사에 값을 담아 보내면 R024, 남의 반려동물이면 R025.
   * 반려동물은 인원이 아니라 예약 매수·정원에 영향을 주지 않는다.
   */
  petIds?: number[];
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

/**
 * 예약 가능 날짜·잔여석을 조회한다. 접수 중이 아니면 R003.
 *
 * 인증은 선택이다(공개 API). 다만 토큰을 실어 보내면 서버가 "내가 이미 예약한 날짜"까지
 * 표시해 주므로, 로그인 상태에서는 항상 붙여 보낸다 - 그래야 이미 예약한 날짜를 눌러
 * 결제까지 진행한 뒤에 R005로 막히는 일이 없다.
 */
export function getReservationAvailability(fairId: number) {
  return apiClient.get<ReservationAvailability>(
    `/api/v1/fairs/${fairId}/reservation-availability`,
    { headers: authHeaders() },
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
  /** 사전예약과 같은 규칙. 현장예매도 동반 반려동물을 기록한다(방문 통계 재료). */
  petIds?: number[];
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
 * 마감(행사가 정한 기한, 기본 입장 12시간 전) 초과면 R018, 대상일 불가/마감이면 R002/R004.
 *
 * petIds를 함께 보내면 동반 반려동물 목록을 그 값으로 교체한다(빈 배열 = 동반 해제).
 * 생략하면 기존 동반 정보를 그대로 둔다. 같은 날짜로 요청하면 정원·QR은 건드리지 않고
 * 동반 정보만 바뀐다.
 */
export function changeVisitDate(reservationId: number, visitDate: string, petIds?: number[]) {
  return apiClient.patch<ChangeVisitDateResult>(
    `/api/v1/reservations/${reservationId}/visit-date`,
    // petIds를 안 넘기면 필드 자체를 빼서 보낸다 - 서버는 "없으면 기존 동반 정보 유지"로 읽는다.
    // 빈 배열은 "동반 해제"라는 다른 뜻이므로 undefined와 구분해서 다뤄야 한다.
    petIds === undefined ? { visitDate } : { visitDate, petIds },
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

/**
 * 관리자(EVENT_ADMIN/SUPER_ADMIN)가 관람객 대신 예약을 취소한다(대행 취소).
 *
 * 본인 취소(cancelReservation)와 달리 취소 마감이 지났어도, 현장예매 건이어도 처리된다.
 * 대신 사유가 필수다 - 예약 이력·감사 로그·관람객 알림에 그대로 실린다.
 * 유료 확정 예약이면 전액 환불이 함께 나간다.
 *
 * 실패 코드: A002 담당 행사 아님 / R010 예약 없음 / R013 취소 불가 상태(이미 입장·취소·만료) /
 * R020 환불할 결제 없음 / R021 결제 진행 중(일시적 - 잠시 후 재시도).
 */
export function cancelReservationByAdmin(fairId: number, reservationId: number, reason: string) {
  return apiClient.patch<CancelReservationResult>(
    `/api/v1/admin/fairs/${fairId}/reservations/${reservationId}/cancel`,
    { reason },
    { headers: authHeaders() },
  );
}

// ── 관리자용 예약자 목록(fair-admin 콘솔) ─────────────────────────────────────

/** 관리자용 예약자 목록 한 건. 백엔드 AdminReservationItemResponse에 맞춘다. */
export interface AdminReservationItem {
  reservationId: number;
  reservationNo: string;
  reserverName: string;
  /** YYYY-MM-DD */
  visitDate: string;
  /** HH:mm:ss */
  entryStartTime: string;
  entryEndTime: string;
  reservationStatus: ReservationStatus;
  amount: number;
  /**
   * 예약 확정 시각(ISO). **null일 수 있다** - reservations.reserved_at은 "확정" 시각이라
   * 결제 대기·만료·결제 전 취소 건은 비어 있다(스키마도 NULL 허용).
   */
  reservedAt: string | null;
}

/** 관리자용 예약자 목록 응답(페이징). 백엔드 AdminReservationListResponse에 맞춘다. */
export interface AdminReservationListResponse {
  items: AdminReservationItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * 담당 행사(EVENT_ADMIN) 또는 전체(SUPER_ADMIN)의 예약자별 상세 목록을 조회한다.
 * visitDate·status는 선택 필터(안 넘기면 전체).
 */
export function getFairReservationsForAdmin(
  fairId: number,
  params?: { visitDate?: string; status?: ReservationStatus; page?: number; size?: number },
) {
  const query = new URLSearchParams();
  if (params?.visitDate) query.set("visitDate", params.visitDate);
  if (params?.status) query.set("status", params.status);
  query.set("page", String(params?.page ?? 0));
  query.set("size", String(params?.size ?? 20));
  return apiClient.get<AdminReservationListResponse>(
    `/api/v1/fairs/${fairId}/reservations?${query.toString()}`,
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
  /** 현장예매 전용 정원. null이면 제한 없음(사전예약 정원과는 별개다). */
  capacity: number | null;
  /** 그 정원을 점유 중인 현장예매 수. 읽기 전용 — 예약·취소가 움직인다. */
  reservedCount: number;
  status: OnsiteSalesStatus;
  /** 낙관적 락 버전. 미설정이면 0. */
  version: number;
  updatedAt: string | null;
}

/** 현장예매 정책 저장 요청. 백엔드 UpdateOnsiteSalesPolicyRequest에 맞춘다. */
export interface SaveOnsiteSalesPolicyRequest {
  price: number;
  /** 현장예매 전용 정원. null로 보내면 "제한 없음"으로 저장된다. */
  capacity: number | null;
  status: OnsiteSalesStatus;
  /** 조회 때 받은 version. 신규(미설정)면 0 또는 null. 불일치 시 R009. */
  expectedVersion: number | null;
}

/** 운영일의 현장예매 정책을 조회한다. 미설정이면 price=0·정원 없음·CLOSED·version=0. */
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
