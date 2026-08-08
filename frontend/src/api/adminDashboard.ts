import { apiClient } from "./client";

/** AdminDashboardController는 응답을 ApiResponse<T>({success, status, code, message, data})로 감싸서 내려준다. */
interface ApiEnvelope<T> { data: T; }

async function unwrap<T>(promise: Promise<ApiEnvelope<T>>): Promise<T> {
  const envelope = await promise;
  return envelope.data;
}

/** 심사·결제 대기 단계(RECEIVED/REJECTED/PAYMENT_PENDING/EXPIRED)는 운영 통계에서 제외된다. */
export type FairOperationStatus = "PREPARING" | "IN_PROGRESS" | "ENDED";

export interface AdminDashboardSummary {
  totalFairs: number;
  inProgressFairs: number;
  preparingFairs: number;
  endedFairs: number;
  /** 전체 확정 예약(수) */
  totalReservations: number;
  totalVisitors: number;
}

export interface FairSummary {
  fairId: number;
  fairName: string;
  status: FairOperationStatus;
  /** YYYY-MM-DD */
  operationStartDate: string;
  operationEndDate: string;
  totalReservations: number;
  totalVisitors: number;
}

export function getAdminDashboardSummary() {
  return unwrap(apiClient.get<ApiEnvelope<AdminDashboardSummary>>("/api/admin/dashboard"));
}

export function getAdminDashboardFairs() {
  return unwrap(apiClient.get<ApiEnvelope<FairSummary[]>>("/api/admin/dashboard/fairs"));
}
