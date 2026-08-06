import { apiClient } from "./client";
import { TEMP_USER_ID_HEADER } from "./fair";
import { TEMP_SUPER_ADMIN_USER_ID } from "./settlement";

export type CommissionRateScope = "GLOBAL" | "FAIR";

export interface CommissionRateResponse {
  scope: CommissionRateScope;
  fairId: number | null;
  rate: number;
  updatedByUserId: number | null;
  updatedAt: string | null;
}

export interface UpdateCommissionRateRequest {
  scope: CommissionRateScope;
  fairId?: number;
  rate: number;
}

/**
 * 현재 적용 요율을 조회한다. fairId를 주면 행사별 override -> 전역 기본값 순으로 해석,
 * 안 주면 전역값만 본다. 한 번도 설정된 적 없으면 updatedAt이 null로 온다(부트스트랩 기본값).
 */
export function getCommissionRate(fairId?: number) {
  const params = fairId !== undefined ? `?fairId=${fairId}` : "";
  return apiClient.get<CommissionRateResponse>(`/api/settlements/commission-rate${params}`);
}

/** 새 요율을 설정한다(SUPER_ADMIN). 기존 값은 이력으로 남고 새로 계산되는 정산부터 적용된다. */
export function setCommissionRate(payload: UpdateCommissionRateRequest, userId: number = TEMP_SUPER_ADMIN_USER_ID) {
  return apiClient.put<CommissionRateResponse>("/api/settlements/commission-rate", payload, {
    headers: { [TEMP_USER_ID_HEADER]: String(userId) },
  });
}
