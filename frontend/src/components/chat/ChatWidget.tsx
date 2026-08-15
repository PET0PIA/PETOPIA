import { useCallback, useEffect, useRef, useState } from "react";
import { MessageCircle, X } from "lucide-react";
import {
  closeConversation,
  fetchChatBootstrap,
  fetchConversation,
  sendChatMessage,
  startConversation,
  type ChatBootstrap,
  type ChatConversation,
  type ChatMenu,
  type ChatMessage,
} from "../../api/chat";
import { ApiError } from "../../api/client";
import { useChatStream, type ChatStatusEvent } from "../../hooks/useChatStream";
import { ChatComposer } from "./ChatComposer";
import { ChatMenuButtons } from "./ChatMenuButtons";
import { ChatMessageList } from "./ChatMessageList";
import { TypingIndicator } from "./TypingIndicator";

/**
 * 채널톡형 상담 위젯. 우하단 런처를 눌러 열고 닫는다.
 *
 * 상태 판단(보낼 수 있는가, 잠겼는가)은 전부 서버 응답을 그대로 따른다. 이 컴포넌트는
 * 서버가 준 inputLocked를 화면에 반영할 뿐, 스스로 규칙을 재현하지 않는다.
 */

/**
 * 폴백 폴링 간격.
 *
 * 기본 수신 경로는 SSE다. 이 폴링은 SSE가 끊겼을 때만 돈다 - 프록시가 스트림을 막는
 * 환경에서도 대화는 이어져야 하기 때문이다(타이핑 표시만 빠진다).
 */
const POLL_INTERVAL_MS = 4000;

export function ChatWidget() {
  const [open, setOpen] = useState(false);
  const [bootstrap, setBootstrap] = useState<ChatBootstrap | null>(null);
  const [conversation, setConversation] = useState<ChatConversation | null>(null);
  /*
   * 화면에 그리는 메시지. 대화(conversation)와 분리해서 들고 있는 게 핵심이다.
   * 새 문의를 시작하면 conversation은 바뀌지만 transcript는 그대로 이어져야 한다 -
   * 상담이 끝날 때마다 화면이 비면 사용자는 방금 받은 답변조차 다시 볼 수 없다.
   */
  const [transcript, setTranscript] = useState<ChatMessage[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const panelRef = useRef<HTMLDivElement>(null);
  /**
   * 마지막으로 받은 메시지 ID. 상태가 아니라 ref인 이유는 이 값이 바뀔 때마다 스트림
   * 훅이 재구독되면 안 되기 때문이다(메시지 한 건마다 연결이 끊겼다 붙는다).
   */
  const latestMessageIdRef = useRef<number | null>(null);

  /**
   * 이미 받은 메시지에 이어 붙인다.
   *
   * SSE와 폴백 폴링이 겹치는 순간 같은 메시지가 두 경로로 들어올 수 있어 ID로 걸러낸다.
   * 커서(ref)도 여기서 함께 갱신해, 어느 경로로 받았든 다음 조회 시작점이 정확해진다.
   */
  const mergeMessages = useCallback((previous: ChatMessage[], incoming: ChatMessage[]) => {
    const seen = new Set(previous.map((message) => message.messageId));
    const fresh = incoming.filter((message) => !seen.has(message.messageId));
    const merged = fresh.length === 0 ? previous : [...previous, ...fresh];
    const lastId = merged.at(-1)?.messageId;
    if (lastId != null) latestMessageIdRef.current = lastId;
    return merged;
  }, []);

  // 패널을 열 때 인사말·버튼·진행 중 대화를 한 번에 받는다.
  useEffect(() => {
    if (!open || bootstrap) return;
    let canceled = false;

    fetchChatBootstrap()
      .then((data) => {
        if (canceled) return;
        setBootstrap(data);
        latestMessageIdRef.current = data.history.at(-1)?.messageId ?? null;
        setTranscript(data.history);
        setConversation(data.ongoing);
      })
      .catch(() => {
        if (!canceled) setError("상담을 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
      });

    return () => {
      canceled = true;
    };
  }, [open, bootstrap]);

  /** 커서 이후 메시지를 받아 화면을 맞춘다. 스트림 재연결 직후와 폴백 폴링이 함께 쓴다. */
  const syncFromServer = useCallback(async (conversationId: number) => {
    try {
      const lastMessageId = latestMessageIdRef.current;
      const latest = await fetchConversation(conversationId, lastMessageId ?? undefined);
      // 그 사이 다른 대화로 옮겨갔으면 상태 갱신은 건너뛴다(메시지는 이어 붙여도 안전하다).
      setConversation((current) =>
        current && current.conversationId === conversationId ? { ...latest, messages: [] } : current,
      );
      setTranscript((current) => mergeMessages(current, latest.messages));
    } catch {
      // 일시적인 실패로 에러 배너를 띄우면 대화 화면이 계속 깜빡인다. 다음 기회에 다시 맞춘다.
    }
  }, [mergeMessages]);

  const activeConversationId = open && conversation && conversation.status !== "CLOSED"
    ? conversation.conversationId
    : null;

  const handleStreamMessage = useCallback((message: ChatMessage) => {
    setTranscript((current) => mergeMessages(current, [message]));
  }, [mergeMessages]);

  const handleStreamStatus = useCallback((status: ChatStatusEvent) => {
    setConversation((current) => (current ? { ...current, ...status } : current));
  }, []);

  const handleStreamSync = useCallback(() => {
    if (activeConversationId != null) void syncFromServer(activeConversationId);
  }, [activeConversationId, syncFromServer]);

  const { typing, connected } = useChatStream({
    conversationId: activeConversationId,
    onSync: handleStreamSync,
    onMessage: handleStreamMessage,
    onStatus: handleStreamStatus,
  });

  /*
   * 폴백 폴링. SSE가 붙어 있으면 돌지 않는다.
   * 이 위젯은 모든 고객 페이지에 떠 있어서, 조건 없이 폴링하면 서비스 전체 트래픽이
   * 폴링으로 채워진다. 그래서 스트림이 끊겼고, 패널이 열려 있고, 탭이 보일 때만 돈다.
   */
  useEffect(() => {
    if (connected || activeConversationId == null) return;

    const timer = window.setInterval(() => {
      if (document.visibilityState !== "visible") return;
      void syncFromServer(activeConversationId);
    }, POLL_INTERVAL_MS);

    return () => window.clearInterval(timer);
  }, [connected, activeConversationId, syncFromServer]);

  // Esc로 닫는다. 위젯이 화면을 가리는 상태에서 벗어날 키보드 경로가 필요하다.
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open]);

  const handleSelectMenu = async (menu: ChatMenu) => {
    setBusy(true);
    setError(null);
    try {
      const started = await startConversation(menu.code);
      // 기존 내용 위에 새 상담을 이어 붙인다(교체하지 않는다).
      setTranscript((current) => mergeMessages(current, started.messages));
      setConversation({ ...started, messages: [] });
    } catch {
      setError("문의를 시작하지 못했어요. 잠시 후 다시 시도해주세요.");
    } finally {
      setBusy(false);
    }
  };

  const handleSend = async (content: string) => {
    if (!conversation) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await sendChatMessage(conversation.conversationId, content);
      setTranscript((current) => mergeMessages(current, updated.messages));
      setConversation({ ...updated, messages: [] });
    } catch (caught) {
      /*
       * 423(CH010)은 "상담사 답변 대기 중"이다. 폴링과 전송이 엇갈려 이미 잠긴 뒤에
       * 전송이 도착한 경우라, 에러로 알리기보다 최신 상태를 다시 받아 화면을 맞추는 게 맞다.
       */
      if (caught instanceof ApiError && caught.status === 423) {
        try {
          const latest = await fetchConversation(conversation.conversationId);
          setConversation({ ...latest, messages: [] });
          // 잠긴 사이에 도착한 메시지(AI 답변·종료 안내)가 있을 수 있으니 함께 반영한다.
          setTranscript((current) => mergeMessages(current, latest.messages));
        } catch {
          setError(caught.message);
        }
      } else {
        setError(caught instanceof ApiError ? caught.message : "메시지를 보내지 못했어요.");
      }
    } finally {
      setBusy(false);
    }
  };

  const handleClose = async () => {
    if (!conversation) return;
    try {
      await closeConversation(conversation.conversationId);
    } catch {
      // 종료 실패는 사용자가 할 수 있는 게 없다. 아래 재조회로 실제 상태를 다시 맞춘다.
    }
    // 이력은 지우지 않는다. bootstrap을 다시 불러 종료된 상태와 지난 내용을 함께 받는다.
    setBootstrap(null);
  };

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="상담 문의 열기"
        aria-expanded={false}
        className="fixed bottom-5 right-5 z-50 flex h-14 w-14 items-center justify-center rounded-pill bg-primary-strong text-white shadow-lg transition hover:opacity-90"
      >
        <MessageCircle size={24} aria-hidden />
      </button>
    );
  }

  const messages = transcript;
  const closed = conversation?.status === "CLOSED";
  /*
   * 유형 버튼은 (1) 아직 대화가 없거나 (2) 지난 대화가 종료된 경우에만 보여준다.
   * 진행 중인 대화에 계속 떠 있으면 답변을 기다리다 다른 유형을 눌러 대화를 갈아엎게 되고,
   * 반대로 종료된 대화에서 안 보이면 새 문의를 시작할 방법이 없다.
   */
  const showMenus = !conversation || closed;

  return (
    <div
      ref={panelRef}
      role="dialog"
      aria-modal={false}
      aria-label="상담 문의"
      className="fixed inset-x-0 bottom-0 z-50 flex h-[80vh] flex-col bg-card sm:inset-x-auto sm:bottom-5 sm:right-5 sm:h-[560px] sm:w-[380px] sm:rounded-card sm:border sm:border-line sm:shadow-xl"
    >
      <header className="flex items-center justify-between border-b border-line px-4 py-3">
        <div>
          <p className="text-sm font-bold text-ink">펫토피아 상담</p>
          {bootstrap && (
            <p className="text-xs text-muted">
              {bootstrap.withinBusinessHours ? "상담 운영시간이에요" : "지금은 운영시간이 아니에요"}
            </p>
          )}
        </div>
        <div className="flex items-center gap-1">
          {conversation && (
            <button
              type="button"
              onClick={handleClose}
              className="rounded-button px-2 py-1 text-xs font-bold text-muted transition hover:text-ink"
            >
              상담 종료
            </button>
          )}
          <button
            type="button"
            onClick={() => setOpen(false)}
            aria-label="상담 문의 닫기"
            className="flex h-9 w-9 items-center justify-center rounded-button text-muted transition hover:text-ink"
          >
            <X size={18} aria-hidden />
          </button>
        </div>
      </header>

      <ChatMessageList greeting={messages.length === 0 ? (bootstrap?.greeting ?? "") : ""} messages={messages} />

      {typing && <TypingIndicator />}

      {error && (
        <p role="alert" className="px-4 pb-2 text-center text-xs text-muted">
          {error}
        </p>
      )}

      {showMenus ? (
        <>
          {closed && (
            <p className="px-4 pb-1 text-center text-xs text-muted">
              종료된 상담이에요. 위 내용은 계속 확인하실 수 있어요.
            </p>
          )}
          <ChatMenuButtons menus={bootstrap?.menus ?? []} disabled={busy} onSelect={handleSelectMenu} />
        </>
      ) : (
        <ChatComposer
          locked={conversation?.inputLocked ?? false}
          lockReason={conversation?.lockReason ?? null}
          sending={busy}
          onSend={handleSend}
        />
      )}
    </div>
  );
}
