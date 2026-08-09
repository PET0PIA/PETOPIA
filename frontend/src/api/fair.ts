import { apiClient } from "./client";

/**
 * 백엔드 Fair 도메인이 실제 JWT 인증(@AuthenticationPrincipal)으로 전환돼서, 이 파일의 API는
 * 더 이상 X-User-Id로 사용자를 식별하지 않는다 - 아래 authHeaders()가 붙이는
 * Authorization: Bearer 토큰만 본다(reservation.ts와 동일 패턴).
 *
 * TEMP_USER_ID_HEADER / TEMP_APPLICANT_USER_ID는 이 파일 자체는 더 이상 안 쓰지만,
 * audit.ts/commissionRate.ts/notification.ts/settlement.ts가 아직 이 상수를 가져다 쓰고
 * 있어(해당 백엔드 도메인은 이번에 JWT로 전환 안 됨) 그대로 export만 유지한다.
 * TODO 그 도메인들도 JWT로 전환되면 이 export를 제거한다.
 */
export const TEMP_USER_ID_HEADER = "X-User-Id";
export const TEMP_APPLICANT_USER_ID = 1;

// TODO 인증 도메인(로그인/토큰 발급) 완성 후 client.ts가 토큰을 자동 주입하면 이 헬퍼는 불필요하다.
const DEV_ACCESS_TOKEN_KEY = "accessToken";

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem(DEV_ACCESS_TOKEN_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export type FairCategory = "DOG" | "CAT" | "ETC";
export type IndoorOutdoor = "INDOOR" | "OUTDOOR";

export interface CreateFairApplicationRequest {
  name: string;
  description?: string;
  category?: FairCategory;
  /** presigned 업로드로 받은 임시 객체 키(파일 자체가 아니라 objectKey를 보낸다). */
  posterImageObjectKey?: string;
  noticeText?: string;
  placeName?: string;
  address?: string;
  indoorOutdoor?: IndoorOutdoor;
  vendorRecruitStartDate?: string;
  vendorRecruitEndDate?: string;
  reservationStartDate?: string;
  reservationEndDate?: string;
  operationStartDate?: string;
  operationEndDate?: string;
  reservationFee?: number;
  reservationCancelDeadlineHours?: number;
  reservationChangeDeadlineHours?: number;
  managerName: string;
  managerPhone?: string;
  managerEmail: string;
}

export interface CreateFairApplicationResponse {
  fairId: number;
  name: string;
  status: string;
  createdAt: string;
}

export function createFairApplication(payload: CreateFairApplicationRequest) {
  return apiClient.post<CreateFairApplicationResponse>("/api/fairs", payload, {
    headers: authHeaders(),
  });
}

export interface FairApplicationDetail {
  fairId: number;
  applicantUserId: number;
  name: string;
  description: string | null;
  category: FairCategory | null;
  posterImageUrl: string | null;
  noticeText: string | null;
  placeName: string | null;
  address: string | null;
  indoorOutdoor: IndoorOutdoor | null;
  vendorRecruitStartDate: string | null;
  vendorRecruitEndDate: string | null;
  reservationStartDate: string | null;
  reservationEndDate: string | null;
  operationStartDate: string | null;
  operationEndDate: string | null;
  reservationFee: number | null;
  reservationCancelDeadlineHours: number | null;
  reservationChangeDeadlineHours: number | null;
  managerName: string;
  managerPhone: string | null;
  managerEmail: string;
  status: string;
  rejectReason: string | null;
  reviewedAt: string | null;
  paymentDueAt: string | null;
  createdAt: string;
}

// 백엔드 SecurityConfig 기준 SUPER_ADMIN 전용(관리자 검토 화면). 신청자 본인 조회는
// 별도 마이페이지 API(GET /api/fairs/mine, /api/fairs/{fairId}/mine, 아직 FE 미연동)를 쓴다.
// 로그인 여부와 무관하게 누구나 볼 수 있어야 하는 화면(티켓 예매 등)은 이 함수 대신
// getFairPublicSummary를 쓴다 - SUPER_ADMIN이 아닌 일반 사용자가 부르면 403이 난다.
export function getFairApplication(fairId: number) {
  return apiClient.get<FairApplicationDetail>(`/api/fairs/${fairId}`, {
    headers: authHeaders(),
  });
}

export interface FairPublicSummary {
  fairId: number;
  name: string;
  description: string | null;
  category: FairCategory | null;
  posterImageUrl: string | null;
  noticeText: string | null;
  placeName: string | null;
  address: string | null;
  indoorOutdoor: IndoorOutdoor | null;
  operationStartDate: string | null;
  operationEndDate: string | null;
  status: string;
}

/**
 * 공개(publish)된 행사의 요약 정보를 인증 없이 조회한다(티켓 예매 화면 등). managerPhone/
 * managerEmail 같은 PII는 응답에 없다 - getFairApplication과 달리 로그인 여부와 무관하게
 * 누구나 호출할 수 있다. 공개되지 않은 행사는 404로 응답한다.
 */
export function getFairPublicSummary(fairId: number) {
  return apiClient.get<FairPublicSummary>(`/api/fairs/${fairId}/public`);
}

export type FairReviewDecision = "APPROVE" | "REJECT";

export interface ReviewFairApplicationRequest {
  decision: FairReviewDecision;
  rejectReason?: string;
}

export interface ReviewFairApplicationResponse {
  fairId: number;
  status: string;
  reviewedAt: string;
  paymentDueAt: string | null;
  rejectReason: string | null;
}

export function reviewFairApplication(fairId: number, payload: ReviewFairApplicationRequest) {
  return apiClient.patch<ReviewFairApplicationResponse>(`/api/fairs/${fairId}/review`, payload, {
    headers: authHeaders(),
  });
}

export interface Hall {
  hallId: number;
  fairId: number;
  name: string;
  floorPlanImageUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface HallInput {
  name: string;
  /** presigned 업로드로 받은 임시 객체 키. 이미지를 바꾸지 않으면 생략한다(기존 이미지 유지). */
  floorPlanImageObjectKey?: string;
}

export function getHalls(fairId: number) {
  return apiClient.get<Hall[]>(`/api/fairs/${fairId}/halls`, {
    headers: authHeaders(),
  });
}

export function createHall(fairId: number, payload: HallInput) {
  return apiClient.post<Hall>(`/api/fairs/${fairId}/halls`, payload, {
    headers: authHeaders(),
  });
}

export function updateHall(fairId: number, hallId: number, payload: HallInput) {
  return apiClient.put<Hall>(`/api/fairs/${fairId}/halls/${hallId}`, payload, {
    headers: authHeaders(),
  });
}

export function deleteHall(fairId: number, hallId: number) {
  return apiClient.delete<void>(`/api/fairs/${fairId}/halls/${hallId}`, {
    headers: authHeaders(),
  });
}

export interface BoothSlot {
  boothSlotId: number;
  hallId: number;
  slotNumber: string;
  posX: number;
  posY: number;
  width: number;
  height: number;
  price: number;
  active: boolean;
  memo: string | null;
  lockedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BoothSlotItem {
  boothSlotId?: number;
  slotNumber: string;
  posX: number;
  posY: number;
  width: number;
  height: number;
  price: number;
  memo?: string;
}

export interface BulkSaveBoothSlotsRequest {
  slots: BoothSlotItem[];
  /**
   * 이 홀을 조회했을 때 받은 boothLayoutVersion. 서버가 halls.booth_layout_version과
   * 비교해서 다르면(그 사이 다른 곳에서 먼저 저장했으면) 409로 거부한다.
   */
  expectedVersion: number;
}

export interface BoothLayoutResponse {
  slots: BoothSlot[];
  boothLayoutVersion: number;
}

export function getBoothSlots(fairId: number, hallId: number) {
  return apiClient.get<BoothLayoutResponse>(`/api/fairs/${fairId}/halls/${hallId}/booth-slots`, {
    headers: authHeaders(),
  });
}

export function bulkSaveBoothSlots(fairId: number, hallId: number, payload: BulkSaveBoothSlotsRequest) {
  return apiClient.put<BoothLayoutResponse>(`/api/fairs/${fairId}/halls/${hallId}/booth-slots`, payload, {
    headers: authHeaders(),
  });
}

export interface FairDate {
  fairDateId: number;
  fairId: number;
  /** YYYY-MM-DD */
  operationDate: string;
  capacity: number;
  /** HH:mm 또는 HH:mm:ss */
  entryStartTime: string;
  entryEndTime: string;
  /** 유효 상태(PENDING_PAYMENT/CONFIRMED/CHECKED_IN)의 사전예약 건수. 정원 축소·삭제 전 경고용 참고값 */
  reservedCount: number;
  /** 이 운영일에 현장예매 정책이 설정돼 있는지. 삭제 전 경고용 참고값 */
  onsiteSalesConfigured: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFairDateRequest {
  operationDate: string;
  capacity: number;
  entryStartTime: string;
  entryEndTime: string;
}

export interface UpdateFairDateRequest {
  capacity: number;
  entryStartTime: string;
  entryEndTime: string;
}

export function getFairDates(fairId: number) {
  return apiClient.get<FairDate[]>(`/api/fairs/${fairId}/fair-dates`, {
    headers: authHeaders(),
  });
}

export function createFairDate(fairId: number, payload: CreateFairDateRequest) {
  return apiClient.post<FairDate>(`/api/fairs/${fairId}/fair-dates`, payload, {
    headers: authHeaders(),
  });
}

export function updateFairDate(fairId: number, fairDateId: number, payload: UpdateFairDateRequest) {
  return apiClient.put<FairDate>(`/api/fairs/${fairId}/fair-dates/${fairDateId}`, payload, {
    headers: authHeaders(),
  });
}

export function deleteFairDate(fairId: number, fairDateId: number) {
  return apiClient.delete<void>(`/api/fairs/${fairId}/fair-dates/${fairDateId}`, {
    headers: authHeaders(),
  });
}
