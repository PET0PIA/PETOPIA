import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, MessageCircle, X } from "lucide-react";
import {
  closeConversation,
  fetchChatBootstrap,
  fetchConversation,
  logMenuClick,
  sendChatMessage,
  startConversation,
  type ChatBootstrap,
  type ChatConversation,
  type ChatMenu,
  type ChatMessage,
} from "../../api/chat";
import { ApiError } from "../../api/client";
import { useChatStream, type ChatStatusEvent } from "../../hooks/useChatStream";
import { ChatBusinessHourBadge } from "./ChatBusinessHourBadge";
import { ChatComposer } from "./ChatComposer";
import { ChatMenuButtons } from "./ChatMenuButtons";
import { ChatMessageList } from "./ChatMessageList";
import { FixedAnswerView } from "./FixedAnswerView";
import { TypingIndicator } from "./TypingIndicator";

/**
 * 채널톡형 상담 위젯. 우하단 런처를 눌러 열고 닫는다.
 *
 * 화면이 셋으로 나뉜다.
 * - MENU: 인사말 + 버튼. 상담 개념이 없고 메시지 리스트를 그리지 않는다.
 * - ANSWER: 누른 버튼과 고정 답변을 말풍선으로 그리고 상담원 연결을 붙인다. 화면만 대화처럼
 *   보일 뿐 상담도 메시지도 만들지 않는다.
 * - THREAD: 지난 상담과 진행 중 상담을 한 스크롤로 이은 화면. 실시간 수신은 여기서만 돈다.
 *
 * 나누는 이유는 비용과 오해 둘이다. 위젯은 모든 고객 페이지에 떠 있는데, 고정 답변만 읽고
 * 닫는 사용자에게까지 SSE를 붙이면 그만큼의 연결이 상시로 열린다. 그리고 첫 화면에 지난
 * 대화가 이어 붙어 있으면 사용자는 그것이 지금 진행 중인 상담이라고 읽는다.
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

type Screen = "MENU" | "ANSWER" | "THREAD";

export function ChatWidget() {
  const [open, setOpen] = useState(false);
  const [bootstrap, setBootstrap] = useState<ChatBootstrap | null>(null);
  const [screen, setScreen] = useState<Screen>("MENU");
  /** ANSWER 화면에 그릴 고정형 메뉴. 그 화면에서만 쓰이므로 화면 상태와 함께 움직인다. */
  const [answerMenu, setAnswerMenu] = useState<ChatMenu | null>(null);
  const [conversation, setConversation] = useState<ChatConversation | null>(null);
  /*
   * THREAD 화면에 그리는 메시지. 대화(conversation)와 분리해서 들고 있는 게 핵심이다.
   * 새 문의를 시작하면 conversation은 바뀌지만 transcript는 그대로 이어져야 한다 -
   * 상담이 끝날 때마다 화면이 비면 사용자는 방금 받은 답변조차 다시 볼 수 없다.
   */
  const [transcript, setTranscript] = useState<ChatMessage[]>([]);
  const [busy, setBusy] = useState(false);
  /**
   * 종료 요청이 도는 중인지. `busy`와 나눠 갖는다.
   *
   * 하나로 합치면 전송 중에 종료 버튼이 잠기는 것과 종료 중에 입력창이 잠기는 것을 구분할 수
   * 없다. 두 동작은 서로를 막아야 하지만(아래 handleClose·handleSend) 같은 표시를 쓰면
   * 안 된다 - 종료를 누른 순간 입력창이 "전송 중"으로 보이면 사용자는 자기 메시지가 나간
   * 줄 안다.
   */
  const [closing, setClosing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const panelRef = useRef<HTMLDivElement>(null);
  /** 런처 버튼. 패널을 닫을 때 여기로 포커스를 되돌린다. */
  const launcherRef = useRef<HTMLButtonElement>(null);
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

  /*
   * 실시간 수신 대상.
   *
   * THREAD 화면 조건이 붙어 있다. 고정 답변만 보는 사용자에게는 받을 메시지가 없는데,
   * 이 위젯이 모든 고객 페이지에 떠 있어서 조건 없이 붙이면 그만큼의 연결이 상시로 열린다.
   */
  const activeConversationId =
    open && screen === "THREAD" && conversation && conversation.status !== "CLOSED"
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
   * activeConversationId가 THREAD 화면에서만 채워지므로 이 폴링도 그 화면에 한정된다.
   */
  useEffect(() => {
    if (connected || activeConversationId == null) return;

    const timer = window.setInterval(() => {
      if (document.visibilityState !== "visible") return;
      void syncFromServer(activeConversationId);
    }, POLL_INTERVAL_MS);

    return () => window.clearInterval(timer);
  }, [connected, activeConversationId, syncFromServer]);

  /*
   * 열고 닫을 때, 그리고 화면이 바뀔 때 포커스를 옮긴다.
   *
   * 화면을 넘기면 방금 누른 버튼이 언마운트되면서 포커스가 document.body로 떨어진다.
   * 그러면 키보드 사용자는 페이지 맨 앞에서부터 Tab을 눌러 다시 위젯까지 와야 한다.
   * 닫을 때도 닫기 버튼이 사라지며 같은 일이 벌어지므로, 시작 지점이었던 런처로 되돌려준다.
   */
  useEffect(() => {
    if (open) {
      panelRef.current?.focus();
      return;
    }
    // 첫 렌더(한 번도 연 적 없음)에는 되돌릴 포커스가 없다. 그때 런처를 강제로 잡으면
    // 페이지에 들어오자마자 상담 버튼에 포커스가 가버린다.
    if (launcherRef.current && document.activeElement === document.body) {
      launcherRef.current.focus();
    }
  }, [open, screen]);

  // Esc로 닫는다. 위젯이 화면을 가리는 상태에서 벗어날 키보드 경로가 필요하다.
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open]);

  const fixedMenus = useMemo(
    () => (bootstrap?.menus ?? []).filter((menu) => menu.answerType === "FIXED"),
    [bootstrap],
  );

  /*
   * 연결 대상 메뉴. 코드를 하드코딩하지 않고 유형으로 찾는다.
   *
   * 시딩값은 AGENT_CONNECT지만 문구·코드·개수는 전부 운영자가 관리자 화면에서 바꾸는
   * 데이터다. 코드를 박아두면 운영자가 그 버튼을 내리는 순간 연결 경로가 조용히 죽는다.
   * 서버가 display_order로 정렬해 내려주므로 첫 항목이 곧 운영자가 앞에 둔 것이다.
   */
  const agentMenu = useMemo(
    () => (bootstrap?.menus ?? []).find((menu) => menu.answerType === "AGENT") ?? null,
    [bootstrap],
  );

  const activeConversation =
    conversation && conversation.status !== "CLOSED" ? conversation : null;

  /*
   * `문의 내역` 버튼을 띄울지.
   *
   * bootstrap.hasHistory만 보면 안 된다. 그 값은 패널을 열 때 받은 스냅샷이라, 이 세션에서
   * 상담을 시작한 경우 여전히 거짓이다. 그러면 상담을 만든 뒤 뒤로 나온 사용자에게 자기
   * 상담으로 돌아갈 길이 사라진다 - `상담원 연결`이 진행 중 상담으로 데려가긴 하지만,
   * 그 라벨은 "돌아가기"로 읽히지 않아 사용자는 새 문의가 시작될 거라고 생각한다.
   *
   * transcript를 함께 보는 이유는 그것이 "이 창에 보여줄 내용이 있는가"의 정확한 답이기
   * 때문이다. THREAD 화면이 그리는 것이 곧 transcript다.
   */
  const hasThread = (bootstrap?.hasHistory ?? false) || transcript.length > 0;

  const goMenu = () => {
    setScreen("MENU");
    setAnswerMenu(null);
    setError(null);
  };

  /**
   * 이력 화면으로.
   *
   * 오류 문구는 화면끼리 공유한다. 그래서 화면을 넘길 때 지우지 않으면 연결에 실패한 뒤
   * `문의 내역`을 누른 사용자가 이력 화면에서 "문의를 시작하지 못했어요"를 읽는다 - 방금
   * 열린 화면과 아무 관계 없는 문장이다.
   */
  const goThread = () => {
    setError(null);
    setScreen("THREAD");
  };

  const handleSelectFixed = (menu: ChatMenu) => {
    // 집계는 결과를 기다리지 않는다. 답변은 이미 손에 있으므로 화면은 즉시 넘어가야 한다.
    logMenuClick(menu.code);
    setAnswerMenu(menu);
    setError(null);
    setScreen("ANSWER");
  };

  /**
   * 상담원 연결. MENU 화면의 버튼과 ANSWER 화면의 CTA가 같은 이 함수를 쓴다.
   *
   * 두 곳에서 시작되는 같은 동작이라 코드를 나누면 아래 두 규칙 중 하나만 반영되는 사고가
   * 난다. 특히 "진행 중이면 새로 만들지 않는다"가 빠지면, 답변을 기다리다 고정 답변을
   * 눌러본 사용자가 이 버튼을 누르는 순간 대기열에 같은 사람의 상담이 두 건 뜬다 -
   * 상담사는 그게 같은 사람인지 알 수 없다.
   */
  const handleConnectAgent = async () => {
    if (!agentMenu || busy) return;

    if (activeConversation) {
      goThread();
      return;
    }

    setBusy(true);
    setError(null);
    try {
      logMenuClick(agentMenu.code);
      const started = await startConversation(agentMenu.code);
      // 기존 내용 위에 새 상담을 이어 붙인다(교체하지 않는다).
      setTranscript((current) => mergeMessages(current, started.messages));
      setConversation({ ...started, messages: [] });
      setScreen("THREAD");
    } catch {
      // 화면을 바꾸지 않는다. ANSWER를 벗어난 뒤 실패하면 사용자는 읽던 답변을 잃는다.
      setError("문의를 시작하지 못했어요. 잠시 후 다시 시도해주세요.");
    } finally {
      setBusy(false);
    }
  };

  const handleSend = async (content: string) => {
    // 종료 요청이 도는 중에는 보내지 않는다. 두 요청이 겹치면 서버가 종료를 먼저 처리해
    // 메시지가 CHAT_ALREADY_CLOSED로 튕기고, 사용자는 자기가 쓴 글을 잃는다.
    if (!conversation || closing) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await sendChatMessage(conversation.conversationId, content);
      setTranscript((current) => mergeMessages(current, updated.messages));
      setConversation({ ...updated, messages: [] });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "메시지를 보내지 못했어요.");
      /*
       * 실패 원인이 상태 불일치일 수 있다. 상담사가 방금 종료한 대화에 보내면 서버는
       * CHAT_ALREADY_CLOSED를 주는데, 문구만 띄우면 입력창은 그대로 열려 있어 사용자는
       * 같은 실패를 반복한다. 서버 상태를 다시 읽어 잠금을 맞춘다 - SSE 상태 이벤트나
       * 폴백 폴링이 도착하면 어차피 수렴하지만, 그때까지 잘못된 화면이 남는다.
       */
      void syncFromServer(conversation.conversationId);
    } finally {
      setBusy(false);
    }
  };

  /**
   * 사용자가 상담을 끝낸다.
   *
   * 전송 중에는 시작하지 않는다(`busy`). 보내는 중에 종료가 끼어들면 서버가 종료를 먼저
   * 처리할 수 있어, 방금 쓴 메시지가 저장되지 않은 채 대화가 닫힌다. 버튼도 함께 잠그지만
   * 판정은 여기서 한다 - 연달아 눌리는 경로(엔터, 더블클릭)가 화면 상태보다 빠르다.
   */
  const handleClose = async () => {
    if (!conversation || busy || closing) return;
    setClosing(true);
    try {
      await closeConversation(conversation.conversationId);
    } catch {
      // 종료 실패는 사용자가 할 수 있는 게 없다. 아래 재조회로 실제 상태를 다시 맞춘다.
    } finally {
      setClosing(false);
    }
    // 이력은 지우지 않는다. bootstrap을 다시 불러 종료된 상태와 지난 내용을 함께 받는다.
    setBootstrap(null);
  };

  if (!open) {
    return (
      <button
        ref={launcherRef}
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

  /*
   * ANSWER는 첫 화면과 같은 제목을 쓴다. 유형은 사용자 말풍선이 이미 말하고 있어서
   * 헤더가 되풀이할 이유가 없고, 제목이 그대로면 화면이 바뀐 게 아니라 대화가 이어진
   * 것으로 읽힌다.
   *
   * THREAD는 둘을 겸한다 - 지난 이력만 읽는 화면과 진행 중 상담을 이어가는 화면. 진행
   * 중인데 제목이 `문의 내역`이면 방금 상담원 연결로 시작한 대화가 지난 기록처럼 읽혀,
   * 사용자는 여기에 써도 전달되는지 확신하지 못한다. 입력창과 `상담 종료` 버튼이 뜨는
   * 조건(activeConversation)을 제목도 그대로 따른다.
   */
  const title =
    screen === "THREAD" ? (activeConversation ? "상담 진행 중" : "문의 내역") : "펫토피아 상담";

  return (
    <div
      ref={panelRef}
      role="dialog"
      aria-modal={false}
      aria-label="상담 문의"
      // 패널 자체는 원래 포커스를 받지 못한다. -1을 줘야 열릴 때 프로그램적으로 포커스를
      // 옮길 수 있고, Tab 순서에는 끼어들지 않는다.
      tabIndex={-1}
      className="fixed inset-x-0 bottom-0 z-50 flex h-[80vh] flex-col bg-card sm:inset-x-auto sm:bottom-5 sm:right-5 sm:h-[560px] sm:w-[380px] sm:rounded-card sm:border sm:border-line sm:shadow-xl"
    >
      <header className="flex items-center gap-2 border-b border-line px-4 py-3">
        {screen !== "MENU" && (
          <button
            type="button"
            // 연결 요청이 도는 중에는 막는다. 먼저 MENU로 돌아가도 요청이 성공하면 화면이
            // THREAD로 끌려가, 사용자가 방금 한 조작이 되돌려진다.
            disabled={busy}
            onClick={goMenu}
            aria-label="문의 유형으로 돌아가기"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-button text-muted transition hover:text-ink disabled:cursor-not-allowed disabled:opacity-50"
          >
            <ArrowLeft size={18} aria-hidden />
          </button>
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-bold text-ink">{title}</p>
          {/* 상태 점은 MENU에만 둔다. 다른 화면에서는 제목이 그 자리를 쓰고, 운영시간은
              이미 접수 안내나 연결 버튼 아래 문장으로 전달됐다. */}
          {screen === "MENU" && bootstrap && (
            <ChatBusinessHourBadge
              within={bootstrap.withinBusinessHours}
              closesAt={bootstrap.closesAt}
            />
          )}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {screen === "THREAD" && activeConversation && (
            <button
              type="button"
              disabled={busy || closing}
              onClick={handleClose}
              className="rounded-button px-2 py-1 text-xs font-bold text-muted transition hover:text-ink disabled:cursor-not-allowed disabled:opacity-50"
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

      {screen === "ANSWER" && answerMenu ? (
        <FixedAnswerView
          menu={answerMenu}
          greeting={bootstrap?.greeting ?? ""}
          agentMenu={agentMenu}
          withinBusinessHours={bootstrap?.withinBusinessHours ?? false}
          closesAt={bootstrap?.closesAt ?? null}
          connecting={busy}
          error={error}
          onConnectAgent={handleConnectAgent}
        />
      ) : screen === "THREAD" ? (
        <>
          <ChatMessageList greeting="" messages={transcript} />
          {typing && <TypingIndicator />}
          {error && (
            <p role="alert" className="px-4 pb-2 text-center text-xs text-muted">
              {error}
            </p>
          )}
          {activeConversation ? (
            <ChatComposer
              locked={conversation?.inputLocked ?? false}
              lockReason={conversation?.lockReason ?? null}
              // 종료 중에도 전송 버튼을 잠근다. handleSend가 어차피 거르지만, 그것만으로는
              // 눌러도 아무 일이 없는 버튼이 된다 - 실패한 것도 아니고 나간 것도 아닌 상태다.
              sending={busy || closing}
              onSend={handleSend}
            />
          ) : (
            <div className="border-t border-line px-4 py-3">
              <button
                type="button"
                onClick={goMenu}
                className="min-h-11 w-full rounded-button bg-primary-strong px-3 text-sm font-bold text-white transition hover:opacity-90"
              >
                새 문의하기
              </button>
            </div>
          )}
        </>
      ) : (
        <>
          <div className="flex-1 overflow-y-auto px-4 py-4">
            <p className="whitespace-pre-line text-sm leading-relaxed text-ink">
              {bootstrap?.greeting ?? ""}
            </p>
          </div>
          {error && (
            <p role="alert" className="px-4 pb-2 text-center text-xs text-muted">
              {error}
            </p>
          )}
          <ChatMenuButtons
            fixedMenus={fixedMenus}
            agentMenu={agentMenu}
            disabled={busy}
            onSelectFixed={handleSelectFixed}
            onConnectAgent={handleConnectAgent}
            onOpenHistory={hasThread ? goThread : null}
          />
        </>
      )}
    </div>
  );
}
