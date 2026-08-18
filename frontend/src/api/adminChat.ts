import { apiClient, type ApiEnvelope } from "./client";
import type { ChatConversationStatus, ChatMessage, ChatSenderType } from "./chat";

/** 상담사 콘솔 API. 인증은 관리자 access token(apiClient가 자동으로 싣는다)으로 한다. */

export interface AdminChatConversationSummary {
  conversationId: number;
  status: ChatConversationStatus;
  menuLabel: string | null;
  requesterType: "MEMBER" | "GUEST";
  lastMessagePreview: string;
  lastMessageSenderType: ChatSenderType | null;
  lastMessageAt: string;
  /** 마지막 메시지 이후 경과 초. 서버가 계산해준다(브라우저 시계에 의존하지 않기 위해). */
  waitingSeconds: number;
  assigned: boolean;
}

export interface AdminChatConversationList {
  items: AdminChatConversationSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface AdminChatConversationDetail {
  conversationId: number;
  status: ChatConversationStatus;
  menuLabel: string | null;
  requesterType: "MEMBER" | "GUEST";
  assignedAdminId: number | null;
  createdAt: string;
  messages: ChatMessage[];
}

/**
 * 대기열 필터. 상태(status)가 아니라 **상담사가 하는 일** 기준이다.
 * 상담사에게 필요한 구분은 "내가 들고 있는 것(OPEN) / 끝난 것(CLOSED)" 둘뿐이라,
 * 상태별로 탭을 쪼개지 않는다. 급한 순서는 목록 정렬과 배지가 알려준다.
 */
export type AdminChatFilter = "OPEN" | "CLOSED" | "ALL";

export async function fetchAdminConversations(
  filter: AdminChatFilter = "OPEN",
  page = 0,
  size = 20,
): Promise<AdminChatConversationList> {
  const params = new URLSearchParams({ page: String(page), size: String(size), filter });
  const response = await apiClient.get<ApiEnvelope<AdminChatConversationList>>(
    `/api/admin/chat/conversations?${params.toString()}`,
  );
  return response.data;
}

export async function fetchAdminConversation(conversationId: number): Promise<AdminChatConversationDetail> {
  const response = await apiClient.get<ApiEnvelope<AdminChatConversationDetail>>(
    `/api/admin/chat/conversations/${conversationId}`,
  );
  return response.data;
}

export async function replyToConversation(
  conversationId: number,
  content: string,
): Promise<AdminChatConversationDetail> {
  const response = await apiClient.post<ApiEnvelope<AdminChatConversationDetail>>(
    `/api/admin/chat/conversations/${conversationId}/messages`,
    { content },
  );
  return response.data;
}

export async function closeAdminConversation(conversationId: number): Promise<void> {
  await apiClient.post<ApiEnvelope<null>>(`/api/admin/chat/conversations/${conversationId}/close`);
}

/**
 * 입력 중 하트비트.
 *
 * 실패해도 무시한다 - 타이핑 표시는 부가 기능이라, 이것 때문에 상담사 화면에 에러를 띄우면
 * 정작 답변 작성이 방해받는다. 신호가 끊기면 서버 TTL(6초)로 알아서 꺼진다.
 */
export function sendTypingHeartbeat(conversationId: number): void {
  void apiClient
    .post<ApiEnvelope<null>>(`/api/admin/chat/conversations/${conversationId}/typing`)
    .catch(() => undefined);
}

export function stopTypingSignal(conversationId: number): void {
  void apiClient
    .delete<ApiEnvelope<null>>(`/api/admin/chat/conversations/${conversationId}/typing`)
    .catch(() => undefined);
}
