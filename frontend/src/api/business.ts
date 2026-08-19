import { apiClient, type ApiEnvelope } from "./client";

export type BusinessVerifyStatus = "PENDING" | "VERIFIED" | "INVALID" | "RETRY_NEEDED";
export type BusinessApprovalStatus = "PENDING_REVIEW" | "APPROVED" | "REJECTED" | "REVOKED";

export interface BusinessRegisterRequest {
  name: string;
  ceoName: string;
  bizRegNo: string;
  /** YYYY-MM-DD */
  startDate: string;
  address: string;
  phone: string;
  website?: string;
  businessRegDocKey: string;
  agreedTerms: boolean;
}

export interface Business {
  businessId: number;
  name: string;
  ceoName: string;
  bizRegNo: string;
  startDate: string;
  address: string;
  phone: string;
  website: string | null;
  verifyStatus: BusinessVerifyStatus;
  approvalStatus: BusinessApprovalStatus;
  rejectReason: string | null;
  createdAt: string;
}

// 관리자 심사 목록의 요약 항목
export interface BusinessReviewSummary {
  businessId: number;
  name: string;
  ceoName: string;
  bizRegNo: string;
  verifyStatus: BusinessVerifyStatus;
  createdAt: string;
}

// 관리자 심사 상세 (소유자 체크 없음)
export interface BusinessReviewDetail {
  businessId: number;
  name: string;
  ceoName: string;
  bizRegNo: string;
  startDate: string;
  address: string;
  phone: string;
  website: string | null;
  verifyStatus: BusinessVerifyStatus;
  approvalStatus: BusinessApprovalStatus;
  documentUrl: string | null;
  rejectReason: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  createdAt: string;
}

// 승인/반려/취소 처리 결과
export interface BusinessReviewResult {
  businessId: number;
  approvalStatus: BusinessApprovalStatus;
  rejectReason: string | null;
  reviewedAt: string;
}

// 백엔드가 ApiResponse<T>로 감싸서 응답하므로 data만 꺼내서 돌려준다.
// 사업자 등록(국세청 진위확인 포함)
export async function registerBusiness(payload: BusinessRegisterRequest): Promise<Business> {
  const response = await apiClient.post<ApiEnvelope<Business>>("/api/businesses", payload);
  return response.data;
}

// 내 사업자 목록 조회
export async function getMyBusinesses(): Promise<Business[]> {
  const response = await apiClient.get<ApiEnvelope<Business[]>>("/api/businesses");
  return response.data;
}

// 사업자 상세 조회
export async function getBusiness(businessId: number): Promise<Business> {
  const response = await apiClient.get<ApiEnvelope<Business>>(`/api/businesses/${businessId}`);
  return response.data;
}

// 심사 상태별 사업자 목록 조회 (관리자용)
export async function getBusinessesForReview(status: BusinessApprovalStatus): Promise<BusinessReviewSummary[]> {
  const response = await apiClient.get<ApiEnvelope<BusinessReviewSummary[]>>(`/api/businesses/review?status=${status}`);
  return response.data;
}

// 사업자 심사 상세 조회 (관리자용, 소유자 체크 없음)
export async function getBusinessReviewDetail(businessId: number): Promise<BusinessReviewDetail> {
  const response = await apiClient.get<ApiEnvelope<BusinessReviewDetail>>(`/api/businesses/${businessId}/review`);
  return response.data;
}

// 사업자 승인 (관리자용)
export async function approveBusiness(businessId: number): Promise<BusinessReviewResult> {
  const response = await apiClient.patch<ApiEnvelope<BusinessReviewResult>>(`/api/businesses/${businessId}/approve`);
  return response.data;
}

// 사업자 반려 (관리자용)
export async function rejectBusiness(businessId: number, rejectReason: string): Promise<BusinessReviewResult> {
  const response = await apiClient.patch<ApiEnvelope<BusinessReviewResult>>(`/api/businesses/${businessId}/reject`, { rejectReason });
  return response.data;
}

// 승인된 사업자 취소 처리 (관리자용, 예: 나중에 조작 서류로 밝혀진 경우)
export async function revokeBusiness(businessId: number, revokeReason: string): Promise<BusinessReviewResult> {
  const response = await apiClient.patch<ApiEnvelope<BusinessReviewResult>>(`/api/businesses/${businessId}/revoke`, { revokeReason });
  return response.data;
}