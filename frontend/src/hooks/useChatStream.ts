import { useEffect, useRef, useState } from "react";
import {
  createStreamTicket,
  streamUrl,
  type ChatConversationStatus,
  type ChatLockReason,
  type ChatMessage,
} from "../api/chat";

/** 상태 이벤트 본문. 백엔드 ChatConversationService.ChatStatusPayload와 필드가 같다. */
export interface ChatStatusEvent {
  status: ChatConversationStatus;
  inputLocked: boolean;
  lockReason: ChatLockReason | null;
}

interface UseChatStreamOptions {
  conversationId: number | null;
  /** 연결·재연결 직후 호출된다. 끊긴 동안 놓친 메시지를 커서 조회로 메우는 용도. */
  onSync: () => void;
  onMessage: (message: ChatMessage) => void;
  onStatus: (status: ChatStatusEvent) => void;
}

/** 재연결 대기(ms). 지수 백오프로 늘리다가 상한에서 멈춘다. */
const RETRY_BASE_MS = 1000;
const RETRY_MAX_MS = 15000;

/** 하트비트가 이 시간 동안 없으면 타이핑 표시를 끈다. 서버 TTL(6초)과 맞춘다. */
const TYPING_TIMEOUT_MS = 6000;

/**
 * 상담 실시간 채널.
 *
 * EventSource의 자동 재연결을 쓰지 않고 직접 관리한다. 티켓이 1회용이라 같은 URL로 다시
 * 붙으면 반드시 실패하기 때문이다 - 재연결 때마다 새 티켓을 받아야 한다.
 *
 * @returns typing 상담사가 입력 중인지. connected 스트림이 살아 있는지(false면 폴링으로 강등).
 */
export function useChatStream({ conversationId, onSync, onMessage, onStatus }: UseChatStreamOptions) {
  const [typing, setTyping] = useState(false);
  const [connected, setConnected] = useState(false);

  // 콜백을 ref로 들고 있는다. 의존성 배열에 넣으면 부모가 리렌더될 때마다 스트림이
  // 끊고 다시 붙는다 - 티켓 발급이 매 렌더마다 일어난다.
  const handlers = useRef({ onSync, onMessage, onStatus });
  useEffect(() => {
    handlers.current = { onSync, onMessage, onStatus };
  }, [onSync, onMessage, onStatus]);

  useEffect(() => {
    if (conversationId == null) return;

    let closed = false;
    let source: EventSource | null = null;
    let retryTimer: number | undefined;
    let typingTimer: number | undefined;
    let attempt = 0;

    const clearTypingTimer = () => {
      if (typingTimer) window.clearTimeout(typingTimer);
      typingTimer = undefined;
    };

    /**
     * 타이핑 표시는 이벤트로 끄되, 타이머로도 끈다.
     * 중단 이벤트가 유실되거나 상담사가 브라우저를 그냥 닫으면 "입력 중"이 영원히 남는다.
     */
    const markTyping = (value: boolean) => {
      setTyping(value);
      clearTypingTimer();
      if (value) {
        typingTimer = window.setTimeout(() => setTyping(false), TYPING_TIMEOUT_MS);
      }
    };

    const scheduleRetry = () => {
      if (closed) return;
      const delay = Math.min(RETRY_BASE_MS * 2 ** attempt, RETRY_MAX_MS);
      attempt += 1;
      retryTimer = window.setTimeout(connect, delay);
    };

    const connect = async () => {
      if (closed) return;
      try {
        const ticket = await createStreamTicket(conversationId);
        if (closed) return;

        source = new EventSource(streamUrl(conversationId, ticket));

        source.onopen = () => {
          attempt = 0;
          setConnected(true);
          // 연결이 열린 시점 이후 이벤트만 흐른다. 끊겨 있던 동안의 메시지는
          // 스트림으로 오지 않으므로 커서 조회로 메운다.
          handlers.current.onSync();
        };

        source.addEventListener("MESSAGE", (event) => {
          handlers.current.onMessage(JSON.parse((event as MessageEvent).data) as ChatMessage);
          // 답변이 도착했다는 것은 상담사가 입력을 마쳤다는 뜻이다.
          markTyping(false);
        });

        source.addEventListener("TYPING", (event) => {
          markTyping((JSON.parse((event as MessageEvent).data) as { typing: boolean }).typing);
        });

        source.addEventListener("STATUS", (event) => {
          handlers.current.onStatus(JSON.parse((event as MessageEvent).data) as ChatStatusEvent);
        });

        source.onerror = () => {
          // EventSource는 스스로 재연결하려 들지만 티켓이 이미 소비돼 실패한다.
          // 직접 닫고 새 티켓으로 다시 붙는다.
          source?.close();
          source = null;
          setConnected(false);
          markTyping(false);
          scheduleRetry();
        };
      } catch {
        setConnected(false);
        scheduleRetry();
      }
    };

    void connect();

    return () => {
      closed = true;
      if (retryTimer) window.clearTimeout(retryTimer);
      clearTypingTimer();
      source?.close();
      setConnected(false);
      setTyping(false);
    };
  }, [conversationId]);

  return { typing, connected };
}
