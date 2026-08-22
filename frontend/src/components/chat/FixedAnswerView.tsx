import { Headset } from "lucide-react";
import type { ChatMenu } from "../../api/chat";
import { ChatBubble } from "./ChatBubble";

interface FixedAnswerViewProps {
  menu: ChatMenu;
  /** 첫 화면과 같은 인사말. 화면이 바뀐 게 아니라 대화가 이어진 것으로 읽히게 한다. */
  greeting: string;
  /** 활성 AGENT 유형이 없으면 null. 그때는 연결 CTA를 그리지 않는다. */
  agentMenu: ChatMenu | null;
  withinBusinessHours: boolean;
  connecting: boolean;
  /** 연결 실패 메시지. 화면은 그대로 두고 버튼 위에만 남긴다. */
  error: string | null;
  onConnectAgent: () => void;
}

/**
 * 고정 답변 화면.
 *
 * <p>대화처럼 보이게 그린다. 누른 버튼 문구가 사용자 말풍선으로, 저장된 답변이 상담사 쪽
 * 말풍선으로 나온다. <b>서버에는 아무 행도 생기지 않는다</b> - 이건 순수한 표현이고,
 * 그래서 {@link ChatBubble}에 ChatMessage가 아니라 값 두 개만 넘긴다. 가짜 messageId를
 * 만들어 넘기면 그것이 언젠가 transcript에 섞여 서버 커서를 망가뜨린다.
 *
 * <p>말풍선으로 두는 이유는 일관성이다. 같은 창에서 상담원 연결은 대화로 보이고 고정 답변은
 * 문서로 보이면, 사용자는 두 기능이 다른 곳에 있다고 느낀다. 형태를 맞춰두면 "물어보면
 * 답이 온다"는 한 가지 사용법만 익히면 된다.
 *
 * <p>고정 답변은 대부분의 문의를 끝내지만, 끝내지 못하는 문의가 남는다. 그때 사용자가 해야
 * 하는 행동이 "뒤로 가서 다른 버튼을 찾는 것"이면 대부분은 그냥 창을 닫는다. 그래서 상담원
 * 연결을 입력창 자리에 고정한다 - 답변이 길어 스크롤이 생겨도 출구가 화면에서 사라지지 않는다.
 */
export function FixedAnswerView({
  menu,
  greeting,
  agentMenu,
  withinBusinessHours,
  connecting,
  error,
  onConnectAgent,
}: FixedAnswerViewProps) {
  return (
    <>
      <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
        {greeting && (
          <p className="whitespace-pre-line text-sm leading-relaxed text-ink">{greeting}</p>
        )}
        <ChatBubble senderType="USER" content={menu.label} />
        {/*
          BOT으로 그린다. AGENT로 두면 사람이 답한 것으로 읽히고, AI로 두면 "자동 답변"
          배지가 붙어 Claude가 만든 답변과 구분되지 않는다. 둘 다 사실이 아니다.
        */}
        <ChatBubble senderType="BOT" content={menu.fixedAnswer ?? ""} />
      </div>

      {agentMenu && (
        <div className="border-t border-line px-4 py-3">
          {error && (
            <p role="alert" className="mb-2 text-center text-xs font-bold text-ink">
              {error}
            </p>
          )}
          <p className="mb-2 text-xs font-bold text-muted">원하는 답변이 아니었나요?</p>
          <button
            type="button"
            disabled={connecting}
            onClick={onConnectAgent}
            className="flex min-h-11 w-full items-center justify-center gap-1.5 rounded-button bg-primary-strong px-3 text-sm font-bold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Headset size={16} aria-hidden />
            {connecting ? "연결하고 있어요..." : "상담원에게 직접 문의하기"}
          </button>
          {/*
            헤더의 상태 점과 목적이 다르다. 헤더는 "지금 어떤 상태인가"이고 여기는 "누르면
            어떻게 되는가"다. 밤에 눌러 자동 응대를 받은 사용자가 "사람이 아니었다"고
            느끼지 않게 하는 것이 이 한 줄의 일이다.
          */}
          <p className="mt-2 text-center text-xs leading-relaxed text-muted">
            {withinBusinessHours
              ? "지금은 상담사가 확인할 수 있는 시간이에요."
              : "운영시간이 아니라 자동 응대로 먼저 답변드려요."}
          </p>
        </div>
      )}
    </>
  );
}
