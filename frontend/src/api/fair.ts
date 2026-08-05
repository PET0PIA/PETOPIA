import { apiClient } from "./client";

/**
 * 회원·인증 도메인 연동 전까지 쓰는 임시 사용자 ID다.
 * 백엔드 FairTemporaryAuthHeaders와 대응된다.
 * TODO 인증 도메인 완성 후 로그인 사용자 정보로 교체한다.
 */
export const TEMP_USER_ID_HEADER = "X-User-Id";
export const TEMP_APPLICANT_USER_ID = 1;

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

export function createFairApplication(payload: CreateFairApplicationRequest, userId: number = TEMP_APPLICANT_USER_ID) {
  return apiClient.post<CreateFairApplicationResponse>("/api/fairs", payload, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
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

export function getFairApplication(fairId: number) {
  return apiClient.get<FairApplicationDetail>(`/api/fairs/${fairId}`);
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

// TODO 인증 도메인 완성 전까지 SUPER_ADMIN 대신 임시 사용자 ID를 검토자로 보낸다.
export const TEMP_REVIEWER_USER_ID = 1;

export function reviewFairApplication(fairId: number, payload: ReviewFairApplicationRequest, reviewerId: number = TEMP_REVIEWER_USER_ID) {
  return apiClient.patch<ReviewFairApplicationResponse>(`/api/fairs/${fairId}/review`, payload, {
    headers: { [TEMP_USER_ID_HEADER]: String(reviewerId) },
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
