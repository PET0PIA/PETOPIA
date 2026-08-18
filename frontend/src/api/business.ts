import { apiClient, type ApiEnvelope } from "./client";

export type BusinessVerifyStatus = "PENDING" | "VERIFIED" | "INVALID" | "RETRY_NEEDED";

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
  approvalStatus: "PENDING_REVIEW" | "APPROVED" | "REJECTED" | "REVOKED";
  rejectReason: string | null;
  createdAt: string;
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