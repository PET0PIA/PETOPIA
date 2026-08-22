import { apiClient } from "./client";

export type ActorType = "USER" | "ADMIN" | "SYSTEM" | "PAYMENT";
export type TargetType = "ACCOUNT" | "FAIR" | "RESERVATION" | "SETTLEMENT" | "FAIR_SETTLEMENT" | "COMMISSION_RATE";
export type ActionType =
  | "LOGIN_FAIL"
  | "ROLE_CHANGE"
  | "ACCOUNT_DEACTIVATE"
  | "FAIR_APPROVE"
  | "FAIR_REJECT"
  | "FAIR_CANCEL_APPROVE"
  | "PAYMENT_COMPLETION_RECEIVED"
  | "SETTLEMENT_CONFIRM"
  | "SETTLEMENT_REOPEN"
  | "SETTLEMENT_RATE_CHANGED"
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
  /** YYYY-MM-DD, 둘 다 포함 범위 */
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}

/**
 * 백엔드가 이제 targetType+targetId / actorUserId / actionType / 기간을 전부 독립적으로
 * 조합해서 받으므로(AND 결합), 채워진 조건을 전부 같이 쿼리스트링에 실어 보낸다.
 * targetType/targetId만 서로 짝으로만 의미가 있어 - 하나만 채워지면 둘 다 보내지 않는다.
 */
export function getAuditLogs(query: AuditLogQuery) {
  const params = new URLSearchParams();
  if (query.targetType && query.targetId !== undefined) {
    params.set("targetType", query.targetType);
    params.set("targetId", String(query.targetId));
  }
  if (query.actorUserId !== undefined) params.set("actorUserId", String(query.actorUserId));
  if (query.actionType) params.set("actionType", query.actionType);
  if (query.startDate) params.set("startDate", query.startDate);
  if (query.endDate) params.set("endDate", query.endDate);
  params.set("page", String(query.page ?? 0));
  params.set("size", String(query.size ?? 20));

  return apiClient.get<AuditLogListResponse>(`/api/admin/audit-logs?${params.toString()}`);
}
