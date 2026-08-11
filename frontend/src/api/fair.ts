import { apiClient } from "./client";

/**
 * 이 파일이 호출하는 백엔드 Fair 도메인은 JWT 인증(@AuthenticationPrincipal)으로 전환됐다 -
 * apiClient가 메모리에 든 액세스 토큰으로 Authorization: Bearer 헤더를 자동으로 붙여주므로,
 * 이 파일의 함수들은 별도로 헤더를 넘기지 않는다.
 *
 * TEMP_USER_ID_HEADER / TEMP_APPLICANT_USER_ID는 이 파일 자체는 더 이상 안 쓰지만,
 * commissionRate.ts/settlement.ts/payment.ts가 아직 이 상수를 가져다 쓰고
 * 있어(그 백엔드 도메인들은 아직 JWT로 전환 안 됨) 그대로 export만 유지한다.
 * TODO 그 도메인들도 JWT로 전환되면 이 export를 제거한다.
 */
export const TEMP_USER_ID_HEADER = "X-User-Id";
export const TEMP_APPLICANT_USER_ID = 1;

export interface AssignedFairSummary {
  fairId: number;
  name: string;
}

/** EVENT_ADMIN 전용 — 자신에게 배정된 행사 목록(fairId + 행사명). */
export function getAssignedFairs() {
  return apiClient.get<AssignedFairSummary[]>("/api/fairs/mine-assigned");
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
  return apiClient.post<CreateFairApplicationResponse>("/api/fairs", payload);
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
  /** 취소 승인 일시(취소 아니면 null). 취소 승인은 status는 그대로 두고 이 필드만 채우므로,
   * 취소 여부는 status가 아니라 이 필드로 판단해야 한다. */
  canceledAt: string | null;
  /** 공개(예약 오픈) 일시(미공개면 null). publish()로만 채워지고 status와는 독립적이다 -
   * 관리자 검토 화면이 "공개하기" 버튼을 보여줄지 이 값으로 판단한다. */
  publishedAt: string | null;
}

// 백엔드 SecurityConfig 기준 SUPER_ADMIN 전용(관리자 검토 화면). 신청자 본인 조회는
// getMyApplications/getMyApplicationDetail을 쓴다. 로그인 여부와 무관하게 누구나 볼 수
// 있어야 하는 화면(티켓 예매 등)은 getFairPublicSummary를 쓴다 - SUPER_ADMIN이 아닌
// 일반 사용자가 이 함수를 부르면 403이 난다.
export function getFairApplication(fairId: number) {
  return apiClient.get<FairApplicationDetail>(`/api/fairs/${fairId}`);
}

/**
 * 신청서 수정(재제출) 요청. RECEIVED(심사 대기) 또는 REJECTED(반려) 상태의 신청서만 수정할 수
 * 있고, REJECTED였다면 이 요청이 성공하는 순간 RECEIVED로 되돌아가 다시 심사 대기열에 선다.
 *
 * PATCH 계약: 키를 아예 안 보내면(undefined - apiClient가 JSON.stringify할 때 자동으로
 * 빠진다) 기존 값을 유지하고, 키를 보내되 값을 null로 보내면 그 필드를 명시적으로 지운다
 * (백엔드가 이 둘을 구분한다). 즉 "생략"과 "빈 값으로 지움"은 서로 다른 의미다 - 이 필드들에
 * undefined 대신 빈 문자열을 넣어 보내면 값이 지워지지 않고 기존 값이 그대로 남으니 주의한다.
 */
export interface UpdateFairApplicationRequest {
  name: string;
  description?: string | null;
  category?: FairCategory | null;
  /** 새로 업로드한 임시 objectKey. 포스터를 새로 첨부하지 않았고 지우지도 않을 거면 이 키
   * 자체를 요청 객체에서 빼서(undefined) 보내야 기존 포스터가 유지된다. null을 보내면
   * 기존 포스터를 삭제한다. */
  posterImageObjectKey?: string | null;
  noticeText?: string | null;
  placeName?: string | null;
  address?: string | null;
  indoorOutdoor?: IndoorOutdoor | null;
  vendorRecruitStartDate?: string | null;
  vendorRecruitEndDate?: string | null;
  reservationStartDate?: string | null;
  reservationEndDate?: string | null;
  operationStartDate?: string | null;
  operationEndDate?: string | null;
  reservationFee?: number | null;
  reservationCancelDeadlineHours?: number | null;
  reservationChangeDeadlineHours?: number | null;
  managerName: string;
  managerPhone?: string | null;
  managerEmail: string;
}

/** 본인 신청서를 수정(재제출)한다. 본인 신청서가 아니거나 수정 가능한 상태(RECEIVED/REJECTED)가
 * 아니면 에러가 난다. */
export function updateFairApplication(fairId: number, payload: UpdateFairApplicationRequest) {
  return apiClient.patch<FairApplicationDetail>(`/api/fairs/${fairId}`, payload);
}

export interface FairApplicationSummary {
  fairId: number;
  name: string;
  status: string;
  operationStartDate: string | null;
  operationEndDate: string | null;
  rejectReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
  /** 취소 승인 일시(취소 아니면 null). status와 별개로 채워지므로, 취소 여부는
   * 이 필드로 판단해야 한다({@link FairApplicationDetail.canceledAt} 참고). */
  canceledAt: string | null;
}

/** 마이페이지 "내 신청 현황" 목록. 로그인한 본인이 낸 신청서만 최신순으로 반환한다. */
export function getMyApplications() {
  return apiClient.get<FairApplicationSummary[]>("/api/fairs/mine");
}

export type FairStatus = "RECEIVED" | "REJECTED" | "EXPIRED" | "PAYMENT_PENDING" | "PREPARING" | "IN_PROGRESS" | "ENDED";

/**
 * 관리자 심사 큐(SUPER_ADMIN 전용). status를 생략하면 전체, 주면(예: "RECEIVED") 그 상태만
 * 걸러 오래된 신청 순으로 반환한다 - 기본값 없이 그대로 서버에 위임한다(호출부가 용도에 맞게
 * 지정: 심사 화면은 "RECEIVED"로 큐처럼 쓰고, 필요하면 다른 상태로 이력을 훑어본다).
 */
export function getFairApplications(status?: FairStatus) {
  return apiClient.get<FairApplicationSummary[]>(`/api/fairs${status ? `?status=${status}` : ""}`);
}

/** 마이페이지 "내 신청 현황" 상세. 본인 신청서가 아니면 403이 난다. */
export function getMyApplicationDetail(fairId: number) {
  return apiClient.get<FairApplicationDetail>(`/api/fairs/${fairId}/mine`);
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

export type PublicFairListFilter = "UPCOMING" | "PAST";

export interface FairPublicListItem {
  fairId: number;
  name: string;
  category: FairCategory | null;
  posterImageUrl: string | null;
  placeName: string | null;
  operationStartDate: string | null;
  operationEndDate: string | null;
}

/**
 * 공개된(published) 행사 중 취소되지 않은 것만 목록으로 조회한다(인증 없이 누구나 호출 가능).
 * UPCOMING은 아직 끝나지 않은(또는 일정 미정) 행사를 임박한 순으로, PAST는 이미 끝난 행사를
 * 최근에 끝난 순으로 반환한다. 상세 화면(getFairPublicSummary)보다 필드가 적다 - 목록에서
 * 카드로 훑어보는 용도라 description/noticeText/address 등은 내려오지 않는다.
 */
export function getPublicFairs(filter: PublicFairListFilter) {
  return apiClient.get<FairPublicListItem[]>(`/api/fairs/public?filter=${filter}`);
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
  return apiClient.patch<ReviewFairApplicationResponse>(`/api/fairs/${fairId}/review`, payload);
}

export interface PublishFairResponse {
  fairId: number;
  status: string;
  publishedAt: string;
}

/**
 * 행사를 공개해 예약을 받을 수 있게 한다(fairs.published_at 설정). 심사 승인 이후
 * (PAYMENT_PENDING~IN_PROGRESS) 상태에서만 가능하고, 취소된 행사는 공개할 수 없다.
 * 이미 공개된 행사를 다시 호출해도 에러 없이 최초 공개 결과를 그대로 반환한다(멱등).
 */
export function publishFair(fairId: number) {
  return apiClient.patch<PublishFairResponse>(`/api/fairs/${fairId}/publish`);
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
  return apiClient.get<Hall[]>(`/api/fairs/${fairId}/halls`);
}

export function createHall(fairId: number, payload: HallInput) {
  return apiClient.post<Hall>(`/api/fairs/${fairId}/halls`, payload);
}

export function updateHall(fairId: number, hallId: number, payload: HallInput) {
  return apiClient.put<Hall>(`/api/fairs/${fairId}/halls/${hallId}`, payload);
}

export function deleteHall(fairId: number, hallId: number) {
  return apiClient.delete<void>(`/api/fairs/${fairId}/halls/${hallId}`);
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
  return apiClient.get<BoothLayoutResponse>(`/api/fairs/${fairId}/halls/${hallId}/booth-slots`);
}

export function bulkSaveBoothSlots(fairId: number, hallId: number, payload: BulkSaveBoothSlotsRequest) {
  return apiClient.put<BoothLayoutResponse>(`/api/fairs/${fairId}/halls/${hallId}/booth-slots`, payload);
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
  return apiClient.get<FairDate[]>(`/api/fairs/${fairId}/fair-dates`);
}

export function createFairDate(fairId: number, payload: CreateFairDateRequest) {
  return apiClient.post<FairDate>(`/api/fairs/${fairId}/fair-dates`, payload);
}

export function updateFairDate(fairId: number, fairDateId: number, payload: UpdateFairDateRequest) {
  return apiClient.put<FairDate>(`/api/fairs/${fairId}/fair-dates/${fairDateId}`, payload);
}

export function deleteFairDate(fairId: number, fairDateId: number) {
  return apiClient.delete<void>(`/api/fairs/${fairId}/fair-dates/${fairDateId}`);
}

// ===== 행사 취소 신청/검토 =====

export type FairCancelRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface FairCancelRequestItem {
  fairCancelRequestId: number;
  fairId: number;
  requestedBy: number;
  reason: string;
  status: FairCancelRequestStatus;
  rejectReason: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  createdAt: string;
}

/** 취소를 신청한다(EVENT_ADMIN, 자기 담당 행사만). PENDING 상태로 등록되고 SUPER_ADMIN 검토를 기다린다. */
export function createFairCancelRequest(fairId: number, reason: string) {
  return apiClient.post<FairCancelRequestItem>(`/api/fairs/${fairId}/fair-cancel-requests`, { reason });
}

/** 특정 행사의 취소 신청 이력을 최신순으로 조회한다(그 행사 담당 EVENT_ADMIN 또는 SUPER_ADMIN). */
export function getFairCancelRequests(fairId: number) {
  return apiClient.get<FairCancelRequestItem[]>(`/api/fairs/${fairId}/fair-cancel-requests`);
}

export interface ReviewFairCancelRequestPayload {
  decision: FairReviewDecision;
  rejectReason?: string;
}

export interface ReviewFairCancelRequestResult {
  fairCancelRequestId: number;
  fairId: number;
  status: string;
  reviewedAt: string;
  rejectReason: string | null;
  canceledAt: string | null;
}

/** 취소 신청을 승인/반려한다(SUPER_ADMIN 전용). 승인하면 그 행사가 즉시 취소 처리된다. */
export function reviewFairCancelRequest(
  fairId: number,
  cancelRequestId: number,
  payload: ReviewFairCancelRequestPayload,
) {
  return apiClient.patch<ReviewFairCancelRequestResult>(
    `/api/fairs/${fairId}/fair-cancel-requests/${cancelRequestId}/review`,
    payload,
  );
}

export interface FairCancelRequestQueueItem {
  fairCancelRequestId: number;
  fairId: number;
  /** 어느 행사의 취소 신청인지. 전체 행사를 가로질러 보여주는 목록이라 fairId만으로는
   * 바로 알아보기 어려워 함께 내려온다. */
  fairName: string;
  requestedBy: number;
  reason: string;
  status: FairCancelRequestStatus;
  createdAt: string;
}

/**
 * 관리자 취소 신청 큐(SUPER_ADMIN 전용, 특정 행사에 갇히지 않고 전체를 가로질러 조회).
 * status를 생략하면 전체, 주면(예: "PENDING") 그 상태만 걸러 오래된 신청 순으로 반환한다.
 * {@link getFairCancelRequests}는 fairId를 이미 아는 상태에서 그 행사 이력만 보는 용도라,
 * "지금 심사해야 할 취소 신청이 뭐가 있는지" 찾을 때는 이 함수를 쓴다.
 */
export function getFairCancelRequestQueue(status?: FairCancelRequestStatus) {
  return apiClient.get<FairCancelRequestQueueItem[]>(`/api/fair-cancel-requests${status ? `?status=${status}` : ""}`);
}
