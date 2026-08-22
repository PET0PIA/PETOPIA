import { Headset, History } from "lucide-react";
import type { ChatMenu } from "../../api/chat";

interface ChatMenuButtonsProps {
  /** 고정 답변 버튼. 누르면 답변 화면으로 넘어간다(상담이 생기지 않는다). */
  fixedMenus: ChatMenu[];
  /** 상담원 연결 버튼. 활성 AGENT 유형이 없으면 null이고, 그때는 그리지 않는다. */
  agentMenu: ChatMenu | null;
  disabled: boolean;
  onSelectFixed: (menu: ChatMenu) => void;
  onConnectAgent: () => void;
  /** 지난 상담이 없으면 null. 빈 화면으로 들어가는 버튼을 두지 않는다. */
  onOpenHistory: (() => void) | null;
}

/**
 * 첫 화면의 버튼들. 문구·순서·개수는 전부 운영자가 관리자 화면에서 바꾸는 값이라
 * 여기 하드코딩하지 않는다.
 *
 * 고정 답변과 상담원 연결을 시각적으로 나누는 이유: 결과가 다르다. 앞의 것은 저장된 안내를
 * 읽는 것이고 뒤의 것은 사람을 부르는 것이다. 같은 모양으로 나열하면 사용자는 넷 중 하나를
 * 고르듯 상담원 연결을 누르고, 그 결과로 답할 사람이 없는 시간대에 대기열만 늘어난다.
 */
export function ChatMenuButtons({
  fixedMenus,
  agentMenu,
  disabled,
  onSelectFixed,
  onConnectAgent,
  onOpenHistory,
}: ChatMenuButtonsProps) {
  return (
    <div className="border-t border-line px-4 py-3">
      {fixedMenus.length > 0 && (
        <div className="flex flex-col gap-2">
          {fixedMenus.map((menu) => (
            <button
              key={menu.menuId}
              type="button"
              disabled={disabled}
              onClick={() => onSelectFixed(menu)}
              className="min-h-11 rounded-button border border-line bg-card px-3 text-left text-sm font-bold text-ink transition hover:bg-page disabled:cursor-not-allowed disabled:opacity-50"
            >
              {menu.label}
            </button>
          ))}
        </div>
      )}

      {(agentMenu || onOpenHistory) && (
        <div className="mt-3 flex gap-2 border-t border-line pt-3">
          {agentMenu && (
            <button
              type="button"
              disabled={disabled}
              onClick={onConnectAgent}
              className="flex min-h-11 flex-1 items-center justify-center gap-1.5 rounded-button bg-primary-strong px-3 text-sm font-bold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Headset size={16} aria-hidden />
              {agentMenu.label}
            </button>
          )}
          {onOpenHistory && (
            <button
              type="button"
              onClick={onOpenHistory}
              className="flex min-h-11 items-center justify-center gap-1.5 rounded-button border border-line px-3 text-sm font-bold text-ink transition hover:bg-page"
            >
              <History size={16} aria-hidden />
              문의 내역
            </button>
          )}
        </div>
      )}
    </div>
  );
}
