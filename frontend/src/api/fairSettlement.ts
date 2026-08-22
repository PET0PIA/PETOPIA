import { apiClient } from "./client";
import { type SettlementStatus } from "./settlement";

/**
 * 행사별 최종정산(플랫폼 ↔ 행사, 2026-08-22) - 업체 구분 없이 그 행사에 참가한 모든 업체의
 * 참가비를 합쳐 행사 하나당 1건만 갖는다. 기존 settlement.ts(업체별 정산)와는 완전히 별개
 * API - 그쪽은 백엔드에 그대로 남아있지만 화면에서는 안 쓴다(2026-08-22 팀 결정).
 */
export interface FairSettlementResponse {
  fairSettlementId: number;
  fairId: number;
  grossAmount: number;
  refundAmount: number;
  commissionRate: number;
  commissionAmount: number;
  netAmount: number;
  status: SettlementStatus;
  confirmedAt: string | null;
  confirmedByUserId: number | null;
}

/** 행사 하나의 최종정산 단건 조회. 계산된 적 없으면(204) undefined. */
export function getFairSettlement(fairId: number) {
  return apiClient.get<FairSettlementResponse | undefined>(`/api/fairs/${fairId}/settlements/final`);
}

/** 행사 최종정산을 계산해서 PENDING으로 만든다. 이미 계산된 정산이 있으면 409가 온다. */
export function calculateFairSettlement(fairId: number) {
  return apiClient.post<FairSettlementResponse>(`/api/fairs/${fairId}/settlements/final`);
}

/**
 * 정산 확정(SUPER_ADMIN 전용, 2026-08-22). PENDING이 아니거나 재계산이 필요한 상태면 409가
 * 온다. 인증은 JWT 기반 @AuthenticationPrincipal이 처리하므로 별도 헤더는 필요 없다
 * (CodeRabbit 리뷰 지적, PR #222 - 예전에 남아있던 X-User-Id 임시 헤더 제거).
 */
export function confirmFairSettlement(fairSettlementId: number) {
  return apiClient.put<FairSettlementResponse>(`/api/settlements/final/${fairSettlementId}/confirm`);
}

/** PENDING 정산을 현재 시점 결제·환불 상태로 다시 집계한다. */
export function recalculateFairSettlement(fairSettlementId: number) {
  return apiClient.put<FairSettlementResponse>(`/api/settlements/final/${fairSettlementId}/recalculate`);
}

/** 확정된 정산을 PENDING으로 되돌린다(SUPER_ADMIN 전용). */
export function reopenFairSettlement(fairSettlementId: number) {
  return apiClient.put<FairSettlementResponse>(`/api/settlements/final/${fairSettlementId}/reopen`);
}
