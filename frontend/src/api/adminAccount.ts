import { apiClient } from "./client";

export type AdminAccountStatus = "ACTIVE" | "INACTIVE";

/** GET /api/admin/accounts 응답 - 백엔드 AdminAccountListItemResponse에 맞춘다. 다른 관리자
 * API(AdminDashboardController)와 달리 이 컨트롤러는 목록을 배열로 그대로 반환한다(래핑 없음). */
export interface AdminAccount {
  userId: number;
  email: string;
  nickname: string;
  status: AdminAccountStatus;
  fairId: number | null;
  fairName: string | null;
  /** YYYY-MM-DD */
  operationStartDate: string | null;
  /** YYYY-MM-DD */
  operationEndDate: string | null;
}

export function getAdminAccounts() {
  return apiClient.get<AdminAccount[]>("/api/admin/accounts");
}

export function updateAdminAccountStatus(userId: number, status: AdminAccountStatus) {
  return apiClient.patch<void>(`/api/admin/accounts/${userId}/status`, { status });
}
