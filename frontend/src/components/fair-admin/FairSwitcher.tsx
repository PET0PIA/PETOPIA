import { useEffect, useRef, useState } from "react";
import { Check, ChevronDown } from "lucide-react";
import { useFairSelector } from "../../contexts/FairSelectorContext";

/**
 * 콘솔 상단 바의 "현재 작업 중인 행사" 스위처. 박스형 트리거를 누르면 리스트가 바로 아래로 펼쳐진다
 * (기본 <select>가 macOS에서 선택 항목 위로 겹쳐 뜨는 걸 피하려고 커스텀으로 만든다). 고를 수 있는
 * 행사가 없으면(예: 최고 관리자 콘솔) 아무것도 렌더하지 않는다.
 */
export function FairSwitcher() {
  const { fairId, setFairId, selectableFairs } = useFairSelector();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false);
    };
    window.addEventListener("mousedown", close);
    return () => window.removeEventListener("mousedown", close);
  }, []);

  if (selectableFairs.length === 0) return null;

  const currentId = fairId ?? selectableFairs[0].fairId;
  const currentName = selectableFairs.find((fair) => fair.fairId === currentId)?.name ?? "행사 선택";

  return (
    <div className="flex min-w-0 items-center gap-2 border-l border-line pl-3">
      <span className="hidden shrink-0 text-xs font-bold text-muted sm:inline">관리 행사</span>
      <div ref={ref} className="relative">
        <button
          type="button"
          aria-haspopup="listbox"
          aria-expanded={open}
          onClick={() => setOpen((value) => !value)}
          className="flex h-9 max-w-[56vw] items-center gap-2 rounded-button border border-line bg-card px-3 text-sm font-medium text-ink hover:border-primary sm:max-w-[240px]"
        >
          <span className="truncate">{currentName}</span>
          <ChevronDown
            size={15}
            className={`shrink-0 text-muted transition-transform ${open ? "rotate-180" : ""}`}
            aria-hidden="true"
          />
        </button>
        {open && (
          <ul
            role="listbox"
            className="surface absolute left-0 top-full z-30 mt-2 max-h-72 min-w-full overflow-auto p-1"
          >
            {selectableFairs.map((fair) => {
              const active = fair.fairId === currentId;
              return (
                <li key={fair.fairId}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={active}
                    onClick={() => {
                      setFairId(fair.fairId);
                      setOpen(false);
                    }}
                    className={`flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-left text-sm ${active ? "bg-primary-soft font-bold text-primary-strong" : "text-muted hover:bg-page hover:text-ink"}`}
                  >
                    <Check size={14} className={`shrink-0 ${active ? "opacity-100" : "opacity-0"}`} aria-hidden="true" />
                    <span className="truncate">{fair.name}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
