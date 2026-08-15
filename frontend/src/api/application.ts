import { apiClient, type ApiEnvelope } from "./client";

// 신청 처리 상태 (백엔드 Application.Status와 1:1 대응)
export type ApplicationStatus =
  | "PENDING_REVIEW" // 심사 대기
  | "PAYMENT_PENDING" // 승인됨, 결제 대기
  | "CONFIRMED" // 결제 완료, 부스 확정
  | "REJECTED" // 반려
  | "CANCELED"; // 취소(자진취소/자동취소 포함)

// 취소 요청 처리 상태 — 요청한 적 없으면 응답에서 null로 옴
export type CancelRequestStatus = "REQUESTED" | "APPROVED" | "REJECTED";

// GET /api/fairs/{fairId}/booth-slots 응답 항목 하나.
// hallId/hallName/floorPlanImageUrl은 배치도를 홀 단위로 그리기 위한 정보
// (RecruitNoticeBoothSlot과 동일한 이유 — HallBoothMap 재사용).
export interface BoothSlotLockStatus {
  boothSlotsId: number;
  slotNumber: string;
  posX: number;
  posY: number;
  width: number;
  height: number;
  price: number;
  locked: boolean; // 활성 신청이 이미 선점했으면 true
  hallId: number;
  hallName: string;
  floorPlanImageUrl: string | null;
}

// POST /api/fairs/{fairId}/applications 요청 바디. 필드별 길이 제한은 백엔드
// ApplicationSubmitRequest의 @Size와 동일 — 여기서 어겨도 서버가 다시 검증한다.
export interface ApplicationSubmitRequest {
  businessId: number;
  boothSlotIds: number[]; // 1~3개, 최대 3개
  purpose: string; // ≤200자
  itemsDesc: string; // ≤500자
  managerName: string; // ≤50자
  managerPhone: string; // ≤20자
  managerEmail: string; // ≤100자
  agreedTerms: boolean; // 제출 시 true여야 서버가 통과시킴
  attachmentObjectKey?: string; // presigned-upload로 받은 임시 객체 키, 선택 입력
}

// 신청서 제출 응답
export interface ApplicationResponse {
  applicationId: number;
  fairId: number;
  businessId: number;
  status: ApplicationStatus;
  boothSlotIds: number[];
  purpose: string;
  itemsDesc: string;
  managerName: string;
  managerPhone: string;
  managerEmail: string;
  attachmentUrl: string | null;
  submittedAt: string;
}

// GET /api/applications 응답 항목 하나(목록용 — 상세보다 필드가 적음)
export interface ApplicationSummary {
  applicationId: number;
  fairName: string;
  status: ApplicationStatus;
  finalPrice: number | null; // 승인 전이면 null
  rejectReason: string | null; // 반려 아니면 null
  cancelRequestStatus: CancelRequestStatus | null;
}

// 신청 상세 응답의 slots 배열 항목 하나
export interface ApplicationSlotDetail {
  boothSlotsId: number;
  slotNumber: string;
  priceAtSelection: number; // 신청 시점 가격 스냅샷 (지금 가격이랑 다를 수 있음)
}

// GET /api/applications/{applicationId} 응답
export interface ApplicationDetail {
  applicationId: number;
  fairId: number;
  businessId: number;
  businessName: string;
  status: ApplicationStatus;
  purpose: string;
  itemsDesc: string;
  managerName: string;
  managerPhone: string;
  managerEmail: string;
  finalPrice: number | null; // 승인 전이면 null — CONFIRMED 전환 시 결제 버튼 노출 판단에 씀
  rejectReason: string | null;
  attachmentUrl: string | null;
  slots: ApplicationSlotDetail[];
  submittedAt: string;
  reviewedAt: string | null; // 심사 전이면 null
  cancelRequestStatus: CancelRequestStatus | null;
  cancelReason: string | null;
  cancelDecidedAt: string | null;
  cancelable: boolean; // 취소 요청 가능 여부 (프론트 버튼 활성화 판단용, 서버가 계산해서 내려줌)
}

// PUT /api/applications/{applicationId} 요청 바디. 심사 대기 상태에서만 반영되고,
// 부스 슬롯은 수정 범위에 없다(businessId/boothSlotIds/agreedTerms 없음).
export interface ApplicationUpdateRequest {
  purpose: string;
  itemsDesc: string;
  managerName: string;
  managerPhone: string;
  managerEmail: string;
  attachmentObjectKey?: string;
}

// GET /api/fairs/{fairId}/applications 응답 항목 하나 (관리자 목록용)
export interface ApplicationReviewSummary {
  applicationId: number;
  businessId: number;
  businessName: string;
  status: ApplicationStatus;
  submittedAt: string;
  finalPrice: number | null;
  rejectReason: string | null;
}

// PUT .../approve, .../reject 공용 응답
export interface ApplicationReviewResult {
  applicationId: number;
  status: ApplicationStatus;
  finalPrice: number | null;
  paymentDueAt: string | null;
  rejectReason: string | null;
  reviewedAt: string;
}

// 취소 요청 처리 상태(진행 중/승인/반려) 기준으로 필터할 때 재사용
export interface CancelRequestSummary {
  cancelRequestId: number;
  applicationId: number;
  businessId: number;
  businessName: string;
  reason: string;
  status: CancelRequestStatus;
  requestedAt: string;
  decidedAt: string | null;
}

// PUT .../cancel-requests/approve, /reject 공용 응답
export interface CancelRequestResult {
  cancelRequestId: number;
  applicationId: number;
  status: CancelRequestStatus; // APPROVED 또는 REJECTED
  applicationStatus: ApplicationStatus; // 처리 후 신청 상태(승인 시 CANCELED, 반려 시 기존 상태 유지)
  decidedAt: string;
  boothDeleted: boolean; // 승인 시 이전 상태가 CONFIRMED였다면 true(부스도 같이 삭제됨)
}

// 부스 슬롯 목록 + 잠금 상태 조회 (비회원도 조회 가능, 인증 불필요)
export async function getBoothSlots(fairId: number): Promise<BoothSlotLockStatus[]> {
  const response = await apiClient.get<ApiEnvelope<BoothSlotLockStatus[]>>(`/api/fairs/${fairId}/booth-slots`);
  return response.data;
}

// 참가 신청서 제출
export async function submitApplication(
  fairId: number,
  payload: ApplicationSubmitRequest
): Promise<ApplicationResponse> {
  const response = await apiClient.post<ApiEnvelope<ApplicationResponse>>(
    `/api/fairs/${fairId}/applications`,
    payload
  );
  return response.data;
}

// 내 신청 현황 목록 조회. businessId를 생략하면 로그인 사용자(JWT ownerId) 소유의
// 모든 사업자에 걸친 신청을 전부 조회한다 — 서버가 @RequestParam(required = false)로 처리.
export async function getMyApplications(businessId?: number): Promise<ApplicationSummary[]> {
  const query = businessId ? `?businessId=${businessId}` : "";
  const response = await apiClient.get<ApiEnvelope<ApplicationSummary[]>>(`/api/applications${query}`);
  return response.data;
}

// 신청 상세 조회 (사업자 본인 또는 담당 행사 관리자만 조회 가능 — 권한 체크는 서버가 함)
export async function getApplicationDetail(applicationId: number): Promise<ApplicationDetail> {
  const response = await apiClient.get<ApiEnvelope<ApplicationDetail>>(`/api/applications/${applicationId}`);
  return response.data;
}

// 참가 신청서 수정 (본인 소유, 심사 대기 상태에서만 가능 — 서버가 검증)
export async function updateApplication(
  applicationId: number,
  payload: ApplicationUpdateRequest
): Promise<ApplicationDetail> {
  const response = await apiClient.put<ApiEnvelope<ApplicationDetail>>(
    `/api/applications/${applicationId}`,
    payload
  );
  return response.data;
}

// 담당 행사의 신청 목록 조회 (행사 담당자용). status 생략하면 전체 상태.
export async function getApplicationsForFair(fairId: number, status?: ApplicationStatus): Promise<ApplicationReviewSummary[]> {
  const query = status ? `?status=${status}` : "";
  const response = await apiClient.get<ApiEnvelope<ApplicationReviewSummary[]>>(`/api/fairs/${fairId}/applications${query}`);
  return response.data;
}

// 참가 신청서 승인 (행사 담당자용). finalPrice 생략하면 슬롯 가격 합계로 서버가 자동 계산한다.
export async function approveApplication(applicationId: number, finalPrice?: number): Promise<ApplicationReviewResult> {
  const response = await apiClient.put<ApiEnvelope<ApplicationReviewResult>>(
    `/api/applications/${applicationId}/approve`,
    finalPrice !== undefined ? { finalPrice } : undefined
  );
  return response.data;
}

// 참가 신청서 반려 (행사 담당자용)
export async function rejectApplication(applicationId: number, rejectReason: string): Promise<ApplicationReviewResult> {
  const response = await apiClient.put<ApiEnvelope<ApplicationReviewResult>>(
    `/api/applications/${applicationId}/reject`,
    { rejectReason }
  );
  return response.data;
}

// 참가 취소 요청 제출 (신청자용)
export async function submitCancelRequest(applicationId: number, reason: string): Promise<void> {
  await apiClient.post<ApiEnvelope<null>>(`/api/applications/${applicationId}/cancel-requests`, { reason });
}

// 담당 행사의 취소 요청 목록 조회 (행사 담당자용). status 생략하면 전체 상태.
export async function getCancelRequestsForFair(fairId: number, status?: CancelRequestStatus): Promise<CancelRequestSummary[]> {
  const query = status ? `?status=${status}` : "";
  const response = await apiClient.get<ApiEnvelope<CancelRequestSummary[]>>(`/api/fairs/${fairId}/cancel-requests${query}`);
  return response.data;
}

// 참가 취소 요청 승인 (행사 담당자용)
export async function approveCancelRequest(applicationId: number): Promise<CancelRequestResult> {
  const response = await apiClient.put<ApiEnvelope<CancelRequestResult>>(
    `/api/applications/${applicationId}/cancel-requests/approve`,
    undefined
  );
  return response.data;
}

// 참가 취소 요청 반려 (행사 담당자용)
export async function rejectCancelRequest(applicationId: number): Promise<CancelRequestResult> {
  const response = await apiClient.put<ApiEnvelope<CancelRequestResult>>(
    `/api/applications/${applicationId}/cancel-requests/reject`,
    undefined
  );
  return response.data;
}