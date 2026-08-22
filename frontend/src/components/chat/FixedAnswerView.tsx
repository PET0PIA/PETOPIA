import { Headset } from "lucide-react";
import type { ChatMenu } from "../../api/chat";

interface FixedAnswerViewProps {
  menu: ChatMenu;
  /** 활성 AGENT 유형이 없으면 null. 그때는 연결 CTA를 그리지 않는다. */
  agentMenu: ChatMenu | null;
  withinBusinessHours: boolean;
  connecting: boolean;
  /** 연결 실패 메시지. 화면은 그대로 두고 버튼 아래에만 남긴다. */
  error: string | null;
  onConnectAgent: () => void;
}

/**
 * 고정 답변 화면.
 *
 * 고정 답변은 대부분의 문의를 끝내지만, 끝내지 못하는 문의가 남는다. 그때 사용자가 해야 하는
 * 행동이 "뒤로 가서 다른 버튼을 찾는 것"이면 대부분은 그냥 창을 닫는다. 그래서 답변 바로
 * 아래에 상담원 연결을 둔다 - 이 화면의 절반은 답변이고 절반은 그 출구다.
 *
 * 운영시간 힌트를 버튼 아래에 붙이는 이유는 헤더의 상태 점과 목적이 다르기 때문이다. 헤더는
 * "지금 어떤 상태인가"이고 여기는 "누르면 어떻게 되는가"다. 밤에 눌러 자동 응대를 받은
 * 사용자가 "사람이 아니었다"고 느끼지 않게 하는 것이 이 한 줄의 일이다.
 */
export function FixedAnswerView({
  menu,
  agentMenu,
  withinBusinessHours,
  connecting,
  error,
  onConnectAgent,
}: FixedAnswerViewProps) {
  return (
    <div className="flex-1 overflow-y-auto px-4 py-4">
      <p className="whitespace-pre-line text-sm leading-relaxed text-ink">{menu.fixedAnswer}</p>

      {agentMenu && (
        <div className="mt-6 border-t border-line pt-4">
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
          <p className="mt-2 text-center text-xs leading-relaxed text-muted">
            {withinBusinessHours
              ? "지금은 상담사가 확인할 수 있는 시간이에요."
              : "운영시간이 아니라 자동 응대로 먼저 답변드려요."}
          </p>
          {error && (
            <p role="alert" className="mt-2 text-center text-xs font-bold text-ink">
              {error}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
