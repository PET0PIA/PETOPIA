import type { ChatMenu } from "../../api/chat";

interface ChatMenuButtonsProps {
  menus: ChatMenu[];
  disabled: boolean;
  onSelect: (menu: ChatMenu) => void;
}

/**
 * 문의 유형 버튼. 서버가 내려준 순서대로 그리기만 한다.
 * 문구·순서·개수는 전부 운영자가 관리자 화면에서 바꾸는 값이라 여기 하드코딩하지 않는다.
 */
export function ChatMenuButtons({ menus, disabled, onSelect }: ChatMenuButtonsProps) {
  if (menus.length === 0) return null;

  return (
    <div className="border-t border-line px-4 py-3">
      <p className="mb-2 text-xs font-bold text-muted">문의 유형을 선택해주세요</p>
      <div className="flex flex-col gap-2">
        {menus.map((menu) => (
          <button
            key={menu.menuId}
            type="button"
            disabled={disabled}
            onClick={() => onSelect(menu)}
            className="min-h-11 rounded-button border border-line bg-card px-3 text-left text-sm font-bold text-ink transition hover:bg-page disabled:cursor-not-allowed disabled:opacity-50"
          >
            {menu.label}
          </button>
        ))}
      </div>
    </div>
  );
}
