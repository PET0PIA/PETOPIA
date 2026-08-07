import { apiClient } from "./client";
import { TEMP_USER_ID_HEADER } from "./fair";

export const TEMP_SUPER_ADMIN_USER_ID = 1;

export type SettlementStatus = "PENDING" | "CONFIRMED" | "PAID";

export interface SettlementResponse {
  settlementId: number;
  fairId: number;
  businessId: number;
  grossAmount: number;
  refundAmount: number;
  commissionRate: number;
  commissionAmount: number;
  netAmount: number;
  status: SettlementStatus;
  confirmedAt: string | null;
  confirmedByUserId: number | null;
}

/** 행사 하나에 속한 정산 목록 조회(박람회관리자/전체관리자). */
export function getSettlementsByFair(fairId: number) {
  return apiClient.get<SettlementResponse[]>(`/api/fairs/${fairId}/settlements`);
}

/** 특정 행사·업체 조합의 정산 단건 상세 조회(참가업체 본인 조회용). */
export function getVendorSettlement(fairId: number, businessId: number) {
  return apiClient.get<SettlementResponse>(`/api/fairs/${fairId}/vendors/${businessId}/settlement`);
}

/**
 * 특정 행사·업체의 정산을 계산해서 PENDING으로 만든다. 같은 조합으로 이미 계산된 정산이
 * 있으면 409(SETTLEMENT_ALREADY_EXISTS)가 온다.
 */
export function calculateSettlement(fairId: number, businessId: number) {
  return apiClient.post<SettlementResponse>(`/api/fairs/${fairId}/vendors/${businessId}/settlements`);
}

/** 정산 확정(SUPER_ADMIN). PENDING이 아니거나 재계산이 필요한 상태면 409가 온다. */
export function confirmSettlement(settlementId: number, userId: number = TEMP_SUPER_ADMIN_USER_ID) {
  return apiClient.put<SettlementResponse>(`/api/settlements/${settlementId}/confirm`, undefined, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}

/**
 * PENDING 정산을 현재 시점 결제·환불 상태로 다시 집계한다. 계산 이후 새로 완료된 결제나
 * (PENDING 정산에 포함된 채로 허용된) 환불을 반영할 때 확정 전에 호출해야 한다.
 */
export function recalculateSettlement(settlementId: number) {
  return apiClient.put<SettlementResponse>(`/api/settlements/${settlementId}/recalculate`);
}
