import { useCallback, useEffect, useState, type FormEvent, type KeyboardEvent } from "react";
import { Link } from "react-router-dom";
import { MessageSquare, Send } from "lucide-react";
import {
  closeAdminConversation,
  fetchAdminConversation,
  fetchAdminConversations,
  replyToConversation,
  type AdminChatConversationDetail,
  type AdminChatConversationSummary,
  type AdminChatFilter,
} from "../../api/adminChat";
import type { ChatConversationStatus, ChatMessage, ChatSenderType } from "../../api/chat";
import { ApiError } from "../../api/client";
import { useTypingSignal } from "../../hooks/useTypingSignal";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Button } from "../../components/ui/Button";

/**
 * 상담사 콘솔. 좌측 대기열, 우측 대화와 답변 입력.
 *
 * 목록 갱신은 폴링이다. 상담사 화면은 상시 열어두는 업무 화면이고 동시 접속자도 소수라
 * 고객 위젯만큼 실시간이 급하지 않다. 대화 내용은 답변 직후 서버 응답으로 갱신된다.
 */

const LIST_REFRESH_MS = 5000;

/*
 * 탭은 상태가 아니라 "상담사가 하는 일" 기준이다.
 *
 * 답변이 필요한 것과 이미 답한 것을 한 목록에 둔다. 나눠 두면 고객이 답하지 않은 상담이
 * 다른 탭에서 잊힌 채 종료되지 않고 남는다. 무엇을 먼저 볼지는 정렬(답변 필요가 위)과
 * 배지가 알려주므로, 탭까지 나눌 이유가 없다.
 */
const FILTER_TABS: { label: string; value: AdminChatFilter }[] = [
  { label: "처리 중", value: "OPEN" },
  { label: "종료", value: "CLOSED" },
  { label: "전체", value: "ALL" },
];

/** 초 단위 대기시간을 사람이 읽는 형태로. 오래 기다린 건이 눈에 띄어야 한다. */
function formatWaiting(seconds: number): string {
  if (seconds < 60) return `${seconds}초`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}분`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간`;
  return `${Math.floor(hours / 24)}일`;
}

const STATUS_BADGE: Record<ChatConversationStatus, { label: string; style: string }> = {
  // 답을 기다리는 두 상태만 강조한다. 색을 여러 개 쓰면 정작 봐야 할 것이 묻힌다.
  WAITING_AGENT: { label: "답변 대기", style: "bg-sun-soft text-ink" },
  AI_ANSWERED: { label: "AI 답변함 · 잠김", style: "bg-sun-soft text-ink" },
  IN_PROGRESS: { label: "진행 중", style: "bg-leaf-soft text-ink" },
  BOT: { label: "봇 응대", style: "bg-surface-alt text-muted" },
  CLOSED: { label: "종료", style: "bg-surface-alt text-muted" },
};

/**
 * 상담사 화면에서는 "누가 한 말인지"가 고객 화면보다 훨씬 중요하다.
 *
 * 고객은 봇이든 AI든 "펫토피아가 한 말"로 읽으면 되지만, 상담사는 <b>고객이 실제로 한 말</b>만
 * 골라내 응대해야 한다. 자동 답변이 고객 말풍선과 같은 모양이면 상담사가 이미 안내된 내용을
 * 다시 설명하거나, 봇이 쓴 문장을 고객의 요구로 오해한다.
 */
const SENDER_LABEL: Record<Exclude<ChatSenderType, "SYSTEM">, string> = {
  USER: "고객",
  BOT: "자동 응답(고정 답변)",
  AI: "AI 자동 답변",
  AGENT: "상담사",
};

function AdminMessage({ message }: { message: ChatMessage }) {
  // 접수·운영시간 안내는 대화가 아니라 시스템 알림이다. 말풍선으로 두면 누가 한 말로 읽힌다.
  if (message.senderType === "SYSTEM") {
    return (
      <p className="whitespace-pre-line text-center text-xs leading-relaxed text-muted">
        {message.content}
      </p>
    );
  }

  const fromAgent = message.senderType === "AGENT";
  const automated = message.senderType === "BOT" || message.senderType === "AI";

  /*
   * 자동 답변은 점선 테두리에 옅은 배경으로 둔다. 고객 말풍선(채워진 회색)과 한눈에 갈리고,
   * 색을 새로 추가하지 않아 화면 톤도 유지된다.
   */
  const bubbleStyle = fromAgent
    ? "bg-primary-strong text-white"
    : automated
      ? "border border-dashed border-line bg-page text-ink"
      : "bg-surface-alt text-ink";

  return (
    <div className={`flex ${fromAgent ? "justify-end" : "justify-start"}`}>
      <div className={`max-w-[80%] ${fromAgent ? "text-right" : ""}`}>
        <span className="mb-1 block text-[11px] font-bold text-muted">
          {SENDER_LABEL[message.senderType]}
        </span>
        <div className={`whitespace-pre-line rounded-card px-3 py-2 text-left text-sm leading-relaxed ${bubbleStyle}`}>
          {message.content}
        </div>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: ChatConversationStatus }) {
  const badge = STATUS_BADGE[status];
  return (
    <span className={`rounded-pill px-2 py-0.5 text-[11px] font-bold ${badge.style}`}>
      {badge.label}
    </span>
  );
}

export function AdminChatPage() {
  const [filter, setFilter] = useState<AdminChatFilter>("OPEN");
  const [conversations, setConversations] = useState<AdminChatConversationSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminChatConversationDetail | null>(null);
  const [reply, setReply] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { onTyping, stopTyping } = useTypingSignal(selectedId);

  /** 답변·종료 직후처럼 즉시 갱신이 필요할 때 쓴다(폴링을 기다리지 않도록). */
  const loadList = useCallback(async (current: AdminChatFilter) => {
    try {
      const list = await fetchAdminConversations(current);
      setConversations(list.items);
    } catch {
      setError("상담 목록을 불러오지 못했어요.");
    }
  }, []);

  /*
   * 목록 폴링. 첫 조회도 인터벌과 같은 경로를 타게 해서, 탭이 백그라운드일 때 도는 갱신과
   * 화면 진입 시 갱신이 어긋나지 않게 한다.
   *
   * setState를 콜백(then) 안에서만 호출하는 형태로 둔다 - 이펙트 본문에서 곧바로 부르면
   * 렌더가 연쇄로 도는 패턴이 되고, 프로젝트 lint 규칙도 이를 막는다.
   */
  useEffect(() => {
    let canceled = false;
    const run = () =>
      fetchAdminConversations(filter)
        .then((list) => {
          if (!canceled) setConversations(list.items);
        })
        .catch(() => {
          if (!canceled) setError("상담 목록을 불러오지 못했어요.");
        });

    void run();
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") void run();
    }, LIST_REFRESH_MS);

    return () => {
      canceled = true;
      window.clearInterval(timer);
    };
  }, [filter]);

  useEffect(() => {
    if (selectedId == null) return;
    let canceled = false;
    fetchAdminConversation(selectedId)
      .then((data) => {
        if (!canceled) setDetail(data);
      })
      .catch(() => {
        if (!canceled) setError("대화를 불러오지 못했어요.");
      });
    return () => {
      canceled = true;
    };
  }, [selectedId]);

  const handleSelect = (conversationId: number) => {
    // 다른 대화로 넘어가기 전에 이전 대화의 입력 중 표시를 끈다.
    stopTyping();
    setReply("");
    setError(null);
    setSelectedId(conversationId);
  };

  const submitReply = async () => {
    const content = reply.trim();
    if (!content || selectedId == null || sending) return;

    setSending(true);
    setError(null);
    try {
      // 서버가 답변 저장과 함께 타이핑 신호도 끄지만, 화면 쪽 타이머도 함께 정리한다.
      stopTyping();
      setDetail(await replyToConversation(selectedId, content));
      setReply("");
      void loadList(filter);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "답변을 보내지 못했어요.");
    } finally {
      setSending(false);
    }
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    void submitReply();
  };

  // isComposing 검사 이유는 ChatComposer와 같다 - 한글 조합 중의 Enter를 전송으로 보면
  // 마지막 글자가 입력창에 남는다.
  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void submitReply();
    }
  };

  const handleClose = async () => {
    if (selectedId == null) return;
    try {
      await closeAdminConversation(selectedId);
      setDetail(await fetchAdminConversation(selectedId));
      void loadList(filter);
    } catch {
      setError("상담을 종료하지 못했어요.");
    }
  };

  // AI가 답한 대화도 사람 답변을 기다리는 건 같다. 오히려 그쪽은 사용자 입력이 잠겨 있다.
  const waitingCount = conversations.filter(
    (item) => item.status === "WAITING_AGENT" || item.status === "AI_ANSWERED",
  ).length;

  return (
    <div className="space-y-6">
      <PageHeader
        title="상담 문의"
        description={`답변을 기다리는 상담이 ${waitingCount}건 있어요.`}
        action={
          <Link
            to="/admin/chat/settings"
            className="inline-flex min-h-11 items-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page"
          >
            상담 설정
          </Link>
        }
      />

      <div className="flex gap-2">
        {FILTER_TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => setFilter(tab.value)}
            className={`min-h-9 rounded-button px-3 text-sm font-bold transition ${
              filter === tab.value
                ? "bg-primary-strong text-white"
                : "border border-line bg-card text-muted hover:text-ink"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {error && (
        <p role="alert" className="text-sm text-muted">
          {error}
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
        {/* 대기열 */}
        <div className="surface max-h-[600px] overflow-y-auto">
          {conversations.length === 0 ? (
            <EmptyState title="상담이 없어요" description="새 문의가 들어오면 여기에 표시됩니다." />
          ) : (
            <ul className="divide-y divide-line">
              {conversations.map((item) => (
                <li key={item.conversationId}>
                  <button
                    type="button"
                    onClick={() => handleSelect(item.conversationId)}
                    className={`w-full px-4 py-3 text-left transition hover:bg-page ${
                      selectedId === item.conversationId ? "bg-page" : ""
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-sm font-bold text-ink">
                        {item.menuLabel ?? "문의"}
                      </span>
                      <StatusBadge status={item.status} />
                    </div>
                    {/*
                      마지막 메시지가 고객 말이 아니면 누가 한 말인지 앞에 붙인다.
                      목록에서 "자동 응답"과 고객 발화가 같은 모양이면, 상담사는 이미 안내된
                      건까지 손대야 할 문의로 착각한다.
                    */}
                    <p className="mt-1 truncate text-xs text-muted">
                      {item.lastMessageSenderType && item.lastMessageSenderType !== "USER" && (
                        <span className="font-bold">
                          {item.lastMessageSenderType === "AGENT"
                            ? "상담사: "
                            : item.lastMessageSenderType === "SYSTEM"
                              ? "안내: "
                              : "자동응답: "}
                        </span>
                      )}
                      {item.lastMessagePreview}
                    </p>
                    <p className="mt-1 text-[11px] text-muted">
                      {item.requesterType === "MEMBER" ? "회원" : "비회원"} · {formatWaiting(item.waitingSeconds)} 경과
                    </p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* 대화 상세 */}
        <div className="surface flex min-h-[600px] flex-col">
          {!detail ? (
            <div className="flex flex-1 items-center justify-center">
              <EmptyState
                title="상담을 선택해주세요"
                description="왼쪽 목록에서 문의를 고르면 내용이 표시됩니다."
              />
            </div>
          ) : (
            <>
              <header className="flex items-center justify-between border-b border-line px-4 py-3">
                <div className="flex items-center gap-2">
                  <MessageSquare size={16} className="text-muted" aria-hidden />
                  <span className="text-sm font-bold text-ink">{detail.menuLabel ?? "문의"}</span>
                  <StatusBadge status={detail.status} />
                </div>
                {detail.status !== "CLOSED" && (
                  <Button variant="outline" onClick={handleClose} className="min-h-9 text-xs">
                    상담 종료
                  </Button>
                )}
              </header>

              <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
                {detail.messages.map((message) => (
                  <AdminMessage key={message.messageId} message={message} />
                ))}
              </div>

              {detail.status === "CLOSED" ? (
                <p className="border-t border-line px-4 py-3 text-center text-xs text-muted">
                  종료된 상담이라 답변을 보낼 수 없어요.
                </p>
              ) : (
                <form onSubmit={handleSubmit} className="flex items-end gap-2 border-t border-line px-4 py-3">
                  <label htmlFor="admin-chat-reply" className="sr-only">
                    답변 내용
                  </label>
                  <textarea
                    id="admin-chat-reply"
                    rows={2}
                    value={reply}
                    onChange={(event) => {
                      setReply(event.target.value);
                      // 키 입력마다 알리되 실제 전송은 훅이 3초로 조인다.
                      onTyping();
                    }}
                    onBlur={stopTyping}
                    onKeyDown={handleKeyDown}
                    maxLength={2000}
                    placeholder="답변을 입력하세요 (Enter 전송, Shift+Enter 줄바꿈)"
                    className="max-h-32 min-h-11 flex-1 resize-none rounded-button border border-line bg-card px-3 py-2 text-sm text-ink placeholder:text-muted"
                  />
                  <button
                    type="submit"
                    disabled={sending || reply.trim().length === 0}
                    aria-label="답변 보내기"
                    className="flex min-h-11 min-w-11 items-center justify-center rounded-button bg-primary-strong text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <Send size={18} aria-hidden />
                  </button>
                </form>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
