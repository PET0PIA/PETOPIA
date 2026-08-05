import { apiClient } from "./client";
import { TEMP_USER_ID_HEADER, TEMP_APPLICANT_USER_ID } from "./fair";

export type ActorType = "USER" | "ADMIN" | "SYSTEM" | "PAYMENT";
export type TargetType = "ACCOUNT" | "FAIR" | "RESERVATION" | "SETTLEMENT" | "COMMISSION_RATE";
export type ActionType =
  | "LOGIN_FAIL"
  | "ROLE_CHANGE"
  | "ACCOUNT_DEACTIVATE"
  | "FAIR_APPROVE"
  | "FAIR_REJECT"
  | "FAIR_CANCEL_APPROVE"
  | "PAYMENT_COMPLETION_RECEIVED"
  | "SETTLEMENT_CONFIRM"
  | "COMMISSION_RATE_UPDATE";

export interface AuditLogRow {
  auditId: number;
  userId: number;
  actorType: ActorType;
  actorRole: string | null;
  actionType: ActionType;
  targetType: TargetType;
  targetId: number;
  beforeValue: string | null;
  afterValue: string | null;
  occurredAt: string;
}

export interface AuditLogListResponse {
  items: AuditLogRow[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
}

export interface AuditLogQuery {
  targetType?: TargetType;
  targetId?: number;
  actorUserId?: number;
  actionType?: ActionType;
  page?: number;
  size?: number;
}

/**
 * 백엔드가 targetType+targetId / actorUserId / actionType 중 하나만 단일로 받으므로
 * 여기서도 채워진 조건 하나만 쿼리스트링에 실어 보낸다.
 */
export function getAuditLogs(query: AuditLogQuery, requesterId: number = TEMP_APPLICANT_USER_ID) {
  const params = new URLSearchParams();
  if (query.targetType && query.targetId !== undefined) {
    params.set("targetType", query.targetType);
    params.set("targetId", String(query.targetId));
  } else if (query.actorUserId !== undefined) {
    params.set("actorUserId", String(query.actorUserId));
  } else if (query.actionType) {
    params.set("actionType", query.actionType);
  }
  params.set("page", String(query.page ?? 0));
  params.set("size", String(query.size ?? 20));

  return apiClient.get<AuditLogListResponse>(`/api/admin/audit-logs?${params.toString()}`, {
    headers: { [TEMP_USER_ID_HEADER]: String(requesterId) },
  });
}
