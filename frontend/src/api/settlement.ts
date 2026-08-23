import { ApiError, apiClient, getAccessToken, refreshAccessTokenOnce, setAccessToken } from "./client";
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

/** 행사 하나에 속한 정산 목록 조회(행사 관리자/전체관리자). */
export function getSettlementsByFair(fairId: number) {
  return apiClient.get<SettlementResponse[]>(`/api/fairs/${fairId}/settlements`);
}

/** 특정 행사·사업자 조합의 정산 단건 상세 조회(참가업체 본인 조회용). */
export function getVendorSettlement(fairId: number, businessId: number) {
  return apiClient.get<SettlementResponse>(`/api/fairs/${fairId}/vendors/${businessId}/settlement`);
}

/**
 * 정산 통합검색(SUPER_ADMIN 전용). fairId·businessId 둘 다 선택적이고 최소 하나는 필요하다 -
 * 행사만 넘기면 그 행사 전체 사업자 정산, 사업자만 넘기면 그 사업자가 참가한 모든 행사의 정산,
 * 둘 다 넘기면 그 조합 하나만 나온다.
 */
export function getSettlementsByFilter(fairId?: number, businessId?: number) {
  const params = new URLSearchParams();
  if (fairId !== undefined) params.set("fairId", String(fairId));
  if (businessId !== undefined) params.set("businessId", String(businessId));
  return apiClient.get<SettlementResponse[]>(`/api/settlements?${params.toString()}`);
}

/**
 * 특정 행사·사업자의 정산을 계산해서 PENDING으로 만든다. 같은 조합으로 이미 계산된 정산이
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

/**
 * 확정(CONFIRMED)된 정산을 PENDING으로 되돌린다(SUPER_ADMIN 전용). 확정 후 정정 절차의
 * 첫 단계 — 되돌린 뒤 recalculateSettlement로 최신 금액을 반영하고 confirmSettlement로
 * 다시 확정한다. PENDING 상태거나 존재하지 않으면 409/404가 온다.
 */
export function reopenSettlement(settlementId: number) {
  return apiClient.put<SettlementResponse>(`/api/settlements/${settlementId}/reopen`);
}

/**
 * 행사별 매출 요약 한 행(SUPER_ADMIN 정산·수수료 화면 전용). 위 SettlementResponse(정산 1건 =
 * 행사·사업자 조합, 참가비만, 저장됨)와 별개 - 행사 전체를 예약금+참가비 합쳐서 조회 시점에
 * 다시 집계해 보여준다. 저장하지 않으므로 settlementId·status 같은 게 없다.
 */
export interface FairRevenueSummaryResponse {
  fairId: number;
  fairName: string;
  ticketAmount: number;
  vendorFeeAmount: number;
  grossAmount: number;
  commissionRate: number;
  platformAmount: number;
  businessAmount: number;
}

/** 행사별 매출 요약 목록 조회(SUPER_ADMIN 전용). 모든 행사를 대상으로 하며 매출 0원인 행사도 포함된다. */
export function getFairRevenueSummaries() {
  return apiClient.get<FairRevenueSummaryResponse[]>("/api/settlements/revenue-summary");
}

/** 행사 하나의 매출 요약 조회(EVENT_ADMIN/SUPER_ADMIN, 2026-08-22). 담당 행사 정산 화면 전용. */
export function getFairRevenueSummary(fairId: number) {
  return apiClient.get<FairRevenueSummaryResponse>(`/api/fairs/${fairId}/revenue-summary`);
}

/** Content-Disposition 헤더의 filename="..."을 뽑아낸다. 없으면 null. (statistics.ts와 동일 패턴) */
function parseFilename(contentDisposition: string | null): string | null {
  if (!contentDisposition) return null;
  const match = /filename="?([^";]+)"?/.exec(contentDisposition);
  return match ? match[1] : null;
}

/**
 * apiClient.request()와 동일하게 Authorization 헤더·쿠키를 직접 실어 보내야
 * EVENT_ADMIN/SUPER_ADMIN 인증을 통과한다 - 순정 fetch는 이걸 자동으로 안 붙여준다
 * (statistics.ts의 fetchVisitStatsExcel과 동일 패턴).
 */
async function fetchWithAuth(path: string, isRetry = false): Promise<Response> {
  const headers: Record<string, string> = {};
  const accessToken = getAccessToken();
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  const response = await fetch(path, { headers, credentials: "include" });

  if (response.status === 401 && !isRetry) {
    try {
      const newToken = await refreshAccessTokenOnce();
      setAccessToken(newToken);
      return fetchWithAuth(path, true);
    } catch {
      setAccessToken(null);
    }
  }

  return response;
}

/**
 * 행사 전체 정산내역을 엑셀(.xlsx)로 내려받는다. apiClient는 JSON 응답만 다루므로
 * 바이너리 응답을 직접 fetch해 Blob으로 받고 브라우저 다운로드를 트리거한다.
 */
export async function downloadSettlementsExcel(fairId: number): Promise<void> {
  const response = await fetchWithAuth(`/api/fairs/${fairId}/settlements/export`);

  if (!response.ok) {
    let message = "엑셀 파일을 내려받지 못했어요.";
    try {
      const body = await response.json();
      message = body?.message ?? message;
    } catch {
      // 에러 응답이 JSON이 아니면 기본 메시지를 사용한다.
    }
    throw new ApiError(message, response.status);
  }

  const blob = await response.blob();
  const filename = parseFilename(response.headers.get("Content-Disposition")) ?? `settlements-${fairId}.xlsx`;

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** 행사별 매출 요약 전체를 엑셀(.xlsx)로 내려받는다. downloadSettlementsExcel과 동일 패턴. */
export async function downloadFairRevenueSummaryExcel(): Promise<void> {
  const response = await fetchWithAuth("/api/settlements/revenue-summary/export");

  if (!response.ok) {
    let message = "엑셀 파일을 내려받지 못했어요.";
    try {
      const body = await response.json();
      message = body?.message ?? message;
    } catch {
      // 에러 응답이 JSON이 아니면 기본 메시지를 사용한다.
    }
    throw new ApiError(message, response.status);
  }

  const blob = await response.blob();
  const filename = parseFilename(response.headers.get("Content-Disposition")) ?? "settlement-revenue-summary.xlsx";

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
