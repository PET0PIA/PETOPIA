import { apiClient, type ApiEnvelope } from "./client";

/**
 * 상담 챗봇 API.
 *
 * 비로그인 사용자가 대부분이라, 대화 소유는 로그인 토큰이 아니라 서버가 발급한
 * 게스트 키로 증명한다. 이 모듈이 키의 보관과 전송을 전담해서 화면 코드가
 * 신경 쓰지 않아도 되게 한다.
 */

/** 게스트 소유 증명 헤더. 백엔드 ChatController.GUEST_KEY_HEADER와 같은 값이어야 한다. */
const GUEST_KEY_HEADER = "X-Chat-Guest-Key";

const GUEST_KEY_STORAGE = "chat-guest-key";

/**
 * 버튼이 무엇으로 답하는지.
 *
 * FIXED는 위젯이 저장된 답변을 즉시 렌더한다(세션을 만들지 않는다).
 * AGENT는 상담 세션을 만들어 상담사 대기열로 보낸다.
 * "AI"는 없다 - AI는 버튼 유형이 아니라 운영시간 외 대체 응대다.
 */
export type ChatAnswerType = "FIXED" | "AGENT";
export type ChatSenderType = "USER" | "BOT" | "AI" | "AGENT" | "SYSTEM";
export type ChatConversationStatus = "BOT" | "WAITING_AGENT" | "AI_HANDLED" | "IN_PROGRESS" | "CLOSED";
/** 입력이 잠기는 경우는 상담 종료 하나뿐이다. */
export type ChatLockReason = "CLOSED";

export interface ChatMenu {
  menuId: number;
  code: string;
  label: string;
  answerType: ChatAnswerType;
  /** FIXED일 때 즉시 렌더할 본문. AGENT면 null이다. */
  fixedAnswer: string | null;
}

export interface ChatMessage {
  messageId: number;
  /** 어느 상담의 메시지인지. 여러 상담을 한 창에 이어 그릴 때 구분선을 놓는 기준. */
  conversationId: number;
  senderType: ChatSenderType;
  content: string;
  createdAt: string;
}

export interface ChatConversation {
  conversationId: number;
  /** 대화를 새로 만들 때만 채워진다. 이후 조회에서는 null. */
  guestKey: string | null;
  status: ChatConversationStatus;
  /**
   * 입력창을 잠글지. **서버가 계산한 값을 그대로 쓴다.**
   * status를 보고 프론트가 다시 판단하면 규칙이 바뀔 때 서버와 어긋나고,
   * 그 순간 사용자는 "보낼 수 있어 보이는데 거부당하는" 화면을 만난다.
   */
  inputLocked: boolean;
  lockReason: ChatLockReason | null;
  messages: ChatMessage[];
}

export interface ChatBootstrap {
  greeting: string;
  menus: ChatMenu[];
  withinBusinessHours: boolean;
  /** 여러 상담에 걸친 최근 메시지(오래된 순). `문의 내역` 화면이 이걸 그대로 이어서 그린다. */
  history: ChatMessage[];
  /**
   * 지난 상담이 있는지. `문의 내역` 버튼 노출 판단에 쓴다.
   * `history.length`로 재현하지 않는다 - 서버가 판정 주체여야 이력 페이지네이션이 붙어도
   * 어긋나지 않는다.
   */
  hasHistory: boolean;
  /**
   * 가장 최근 대화의 **상태만** 담는다(잠금 여부·전송 대상 ID).
   * 메시지 본문은 history에 있으므로 여기 messages는 비어 있다.
   */
  ongoing: ChatConversation | null;
}

/**
 * 게스트 키는 localStorage에 둔다.
 *
 * 대기열 토큰(sessionStorage)과 다른 선택이다. 대기열은 탭 단위로 줄을 서는 게 맞지만,
 * 상담은 탭을 닫았다 다시 열어도 "상담사가 답변했는지" 확인할 수 있어야 한다.
 * 저장이 막힌 환경(시크릿 모드 등)에서도 최소한 이번 세션은 이어지도록 메모리 폴백을 둔다.
 */
let memoryGuestKey: string | null = null;

export function getGuestKey(): string | null {
  try {
    const stored = window.localStorage.getItem(GUEST_KEY_STORAGE);
    if (stored) return stored;
  } catch {
    // 저장소가 막혀 있어도 상담 자체는 동작해야 한다. 아래 메모리 값으로 이어간다.
  }
  return memoryGuestKey;
}

export function setGuestKey(key: string) {
  memoryGuestKey = key;
  try {
    window.localStorage.setItem(GUEST_KEY_STORAGE, key);
  } catch {
    // 새로고침에는 못 살아남지만 이번 세션 상담은 끝낼 수 있다.
  }
}

/** 게스트 키가 있을 때만 헤더를 붙인다. 빈 값을 보내면 서버가 헤더 유무로 판단할 수 없다. */
function guestHeaders(): Record<string, string> {
  const key = getGuestKey();
  return key ? { [GUEST_KEY_HEADER]: key } : {};
}

/** 새 대화 응답에 실려 온 게스트 키를 저장한다. 이후 요청은 이 키로 소유를 증명한다. */
function rememberIssuedKey(conversation: ChatConversation): ChatConversation {
  if (conversation.guestKey) {
    setGuestKey(conversation.guestKey);
  }
  return conversation;
}

export async function fetchChatBootstrap(): Promise<ChatBootstrap> {
  const response = await apiClient.get<ApiEnvelope<ChatBootstrap>>("/api/chat/bootstrap", {
    headers: guestHeaders(),
  });
  return response.data;
}

/**
 * 고정형 버튼 클릭을 집계에 남긴다.
 *
 * 답변은 bootstrap이 이미 실어 보냈으므로 이 호출은 화면과 무관하다. 그래서 결과를
 * 기다리지 않고, 실패해도 사용자에게 알리지 않는다(fire-and-forget). 지표가 한 건
 * 비는 것과 답변 화면이 늦게 뜨는 것 중 후자가 훨씬 나쁘다.
 */
export function logMenuClick(menuCode: string): void {
  void apiClient
    .post<void>(`/api/chat/menus/${encodeURIComponent(menuCode)}/clicks`, undefined, {
      headers: guestHeaders(),
    })
    .catch(() => {
      // 집계 실패는 사용자가 할 수 있는 게 없다. 조용히 넘긴다.
    });
}

export async function startConversation(menuCode: string): Promise<ChatConversation> {
  const response = await apiClient.post<ApiEnvelope<ChatConversation>>(
    "/api/chat/conversations",
    { menuCode },
    { headers: guestHeaders() },
  );
  return rememberIssuedKey(response.data);
}

/**
 * @param afterMessageId 마지막으로 받은 메시지 ID. 넘기면 그 이후 메시지만 온다.
 *   offset이 아니라 커서를 쓰는 이유는, 폴링 도중 새 메시지가 끼어들어도
 *   같은 메시지를 두 번 받거나 건너뛰지 않기 때문이다.
 */
export async function fetchConversation(
  conversationId: number,
  afterMessageId?: number,
): Promise<ChatConversation> {
  const query = afterMessageId != null ? `?afterMessageId=${afterMessageId}` : "";
  const response = await apiClient.get<ApiEnvelope<ChatConversation>>(
    `/api/chat/conversations/${conversationId}${query}`,
    { headers: guestHeaders() },
  );
  return response.data;
}

export async function sendChatMessage(
  conversationId: number,
  content: string,
): Promise<ChatConversation> {
  const response = await apiClient.post<ApiEnvelope<ChatConversation>>(
    `/api/chat/conversations/${conversationId}/messages`,
    { content },
    { headers: guestHeaders() },
  );
  return response.data;
}

/**
 * SSE 연결용 1회성 티켓을 받는다.
 *
 * EventSource는 커스텀 헤더를 보낼 수 없어서 게스트 키를 URL에 실을 수밖에 없는데,
 * 그 값은 서버 접근 로그에 그대로 남는다(상담 열람 자격증명이 로그에 박힌다).
 * 그래서 헤더로 인증된 이 요청으로 60초짜리 1회용 티켓을 받아 URL에는 티켓만 싣는다.
 */
export async function createStreamTicket(conversationId: number): Promise<string> {
  const response = await apiClient.post<ApiEnvelope<{ ticket: string }>>(
    `/api/chat/conversations/${conversationId}/stream-ticket`,
    undefined,
    { headers: guestHeaders() },
  );
  return response.data.ticket;
}

export function streamUrl(conversationId: number, ticket: string): string {
  return `/api/chat/conversations/${conversationId}/stream?ticket=${encodeURIComponent(ticket)}`;
}

export async function closeConversation(conversationId: number): Promise<void> {
  await apiClient.post<ApiEnvelope<null>>(
    `/api/chat/conversations/${conversationId}/close`,
    undefined,
    { headers: guestHeaders() },
  );
}
