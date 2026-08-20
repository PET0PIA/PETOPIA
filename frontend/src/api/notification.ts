import { apiClient } from "./client";


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
  | "BUSINESS_APPROVED"
  | "BUSINESS_REJECTED"
  | "BUSINESS_REVOKED"
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

/**
 * NotificationController는 다른 컨트롤러와 달리 응답을 ApiResponse<T>({success, status, code, data})로
 * 한 번 더 감싸서 내려준다. apiClient는 이 래핑을 모르므로 여기서 data만 꺼내 돌려준다.
 */
interface ApiEnvelope<T> { data: T; }

async function unwrap<T>(promise: Promise<ApiEnvelope<T>>): Promise<T> {
  const envelope = await promise;
  return envelope.data;
}

export function getMyNotifications(page = 0, size = 20) {
  return unwrap(
    apiClient.get<ApiEnvelope<NotificationListResponse>>(`/api/notifications?page=${page}&size=${size}`),
  );
}

export function getUnreadNotificationCount() {
  return unwrap(apiClient.get<ApiEnvelope<number>>("/api/notifications/unread-count"));
}

export function markNotificationAsRead(notificationId: number) {
  return apiClient.put<void>(`/api/notifications/${notificationId}/read`);
}

export function markAllNotificationsAsRead() {
  return apiClient.put<void>("/api/notifications/read-all");
}
