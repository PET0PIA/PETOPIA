import { apiClient, type ApiEnvelope } from "./client";
import type { ChatAnswerType } from "./chat";

/** 상담 운영 설정 API - 버튼, 운영시간, 문구, 지표. */

export interface AdminChatMenu {
  menuId: number;
  code: string;
  label: string;
  answerType: ChatAnswerType;
  fixedAnswer: string | null;
  displayOrder: number;
  isActive: boolean;
}

export interface AdminChatMenuPayload {
  code: string;
  label: string;
  answerType: ChatAnswerType;
  fixedAnswer?: string | null;
  displayOrder?: number;
  isActive?: boolean;
}

export interface ChatBusinessHour {
  /** 1=월 ... 7=일. java.time.DayOfWeek와 같은 규칙(JS의 0=일과 다르다). */
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  isActive: boolean;
}

export interface ChatSetting {
  settingKey: string;
  settingValue: string;
  description: string | null;
}

export interface ChatMenuStat {
  menuLabel: string;
  /** 이 유형으로 만들어진 상담 수. 고정형은 세션을 만들지 않으므로 항상 0이다. */
  conversationCount: number;
  /** 버튼이 눌린 횟수. 고정형의 유일한 수요 지표다. */
  clickCount: number;
  waitingCount: number;
  /** 아직 아무도 답하지 않은 유형은 null. 0으로 오지 않는다. */
  avgFirstResponseSeconds: number | null;
  aiAnsweredCount: number;
}

export async function fetchAdminMenus(): Promise<AdminChatMenu[]> {
  const response = await apiClient.get<ApiEnvelope<AdminChatMenu[]>>("/api/admin/chat/menus");
  return response.data;
}

export async function createAdminMenu(payload: AdminChatMenuPayload): Promise<AdminChatMenu> {
  const response = await apiClient.post<ApiEnvelope<AdminChatMenu>>("/api/admin/chat/menus", payload);
  return response.data;
}

export async function updateAdminMenu(
  menuId: number,
  payload: AdminChatMenuPayload,
): Promise<AdminChatMenu> {
  const response = await apiClient.put<ApiEnvelope<AdminChatMenu>>(
    `/api/admin/chat/menus/${menuId}`,
    payload,
  );
  return response.data;
}

/** 삭제가 아니라 비활성이다 - 과거 상담이 이 버튼을 참조한다. */
export async function deactivateAdminMenu(menuId: number): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/api/admin/chat/menus/${menuId}`);
}

export async function fetchBusinessHours(): Promise<ChatBusinessHour[]> {
  const response = await apiClient.get<ApiEnvelope<ChatBusinessHour[]>>("/api/admin/chat/business-hours");
  return response.data;
}

export async function saveBusinessHours(hours: ChatBusinessHour[]): Promise<void> {
  await apiClient.put<ApiEnvelope<null>>("/api/admin/chat/business-hours", hours);
}

export async function fetchChatSettings(): Promise<ChatSetting[]> {
  const response = await apiClient.get<ApiEnvelope<ChatSetting[]>>("/api/admin/chat/settings");
  return response.data;
}

export async function saveChatSettings(settings: ChatSetting[]): Promise<void> {
  await apiClient.put<ApiEnvelope<null>>("/api/admin/chat/settings", settings);
}

export async function fetchChatStats(): Promise<ChatMenuStat[]> {
  const response = await apiClient.get<ApiEnvelope<ChatMenuStat[]>>("/api/admin/chat/stats");
  return response.data;
}
