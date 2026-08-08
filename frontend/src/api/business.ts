import { apiClient, type ApiEnvelope } from "./client";

/**
 * 회원·인증 도메인의 X-User-Id 임시 헤더 방식이다.
 * 백엔드 BusinessTemporaryAuthHeaders와 대응된다.
 * TODO 인증 도메인이 이 도메인까지 실제 JWT로 전환되면 제거한다.
 */
export const TEMP_USER_ID_HEADER = "X-User-Id";

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
  createdAt: string;
}

function authHeaders(userId: number): Record<string, string> {
  return { [TEMP_USER_ID_HEADER]: String(userId) };
}

// 백엔드가 ApiResponse<T>로 감싸서 응답하므로 data만 꺼내서 돌려준다.
// 사업자 등록(국세청 진위확인 포함)
export async function registerBusiness(payload: BusinessRegisterRequest, userId: number): Promise<Business> {
  const response = await apiClient.post<ApiEnvelope<Business>>("/api/businesses", payload, { headers: authHeaders(userId) });
  return response.data;
}

// 내 사업자 목록 조회
export async function getMyBusinesses(userId: number): Promise<Business[]> {
  const response = await apiClient.get<ApiEnvelope<Business[]>>("/api/businesses", { headers: authHeaders(userId) });
  return response.data;
}

// 사업자 상세 조회
export async function getBusiness(businessId: number, userId: number): Promise<Business> {
  const response = await apiClient.get<ApiEnvelope<Business>>(`/api/businesses/${businessId}`, { headers: authHeaders(userId) });
  return response.data;
}