import { apiClient } from "./client";
import { TEMP_USER_ID_HEADER, TEMP_APPLICANT_USER_ID } from "./fair";

export type NotificationType =
  | "FAIR_APPLICATION_APPROVED"
  | "FAIR_APPLICATION_REJECTED"
  | "FAIR_CANCELED"
  | "RESERVATION_CONFIRMED"
  | "RESERVATION_CHANGED"
  | "RESERVATION_CANCELED"
  | "ENTRY_FAILED"
  | "VENDOR_APPLICATION_APPROVED"
  | "VENDOR_APPLICATION_REJECTED"
  | "VENDOR_APPLICATION_CANCEL_APPROVED"
  | "VENDOR_APPLICATION_CANCEL_REJECTED"
  | "PAYMENT_COMPLETED"
  | "REFUND_COMPLETED"
  | "SETTLEMENT_COMPLETED";

export interface NotificationListItem {
  notificationId: number;
  type: NotificationType;
  title: string;
  body: string;
  linkUrl: string | null;
  createdAt: string;
  isRead: boolean;
}

export interface NotificationListResponse {
  items: NotificationListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

function authHeader(userId: number = TEMP_APPLICANT_USER_ID) {
  return { [TEMP_USER_ID_HEADER]: String(userId) };
}

/**
 * NotificationController는 다른 컨트롤러와 달리 응답을 ApiResponse<T>({success, status, code, data})로
 * 한 번 더 감싸서 내려준다. apiClient는 이 래핑을 모르므로 여기서 data만 꺼내 돌려준다.
 */
interface ApiEnvelope<T> { data: T; }

async function unwrap<T>(promise: Promise<ApiEnvelope<T>>): Promise<T> {
  const envelope = await promise;
  return envelope.data;
}

export function getMyNotifications(page = 0, size = 20, userId?: number) {
  return unwrap(
    apiClient.get<ApiEnvelope<NotificationListResponse>>(`/api/notifications?page=${page}&size=${size}`, {
      headers: authHeader(userId),
    }),
  );
}

export function getUnreadNotificationCount(userId?: number) {
  return unwrap(
    apiClient.get<ApiEnvelope<number>>("/api/notifications/unread-count", {
      headers: authHeader(userId),
    }),
  );
}

export function markNotificationAsRead(notificationId: number, userId?: number) {
  return apiClient.put<void>(`/api/notifications/${notificationId}/read`, undefined, {
    headers: authHeader(userId),
  });
}

export function markAllNotificationsAsRead(userId?: number) {
  return apiClient.put<void>("/api/notifications/read-all", undefined, {
    headers: authHeader(userId),
  });
}
