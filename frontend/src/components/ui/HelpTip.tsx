import { HelpCircle } from "lucide-react";
import { useEffect, useRef, useState } from "react";

/**
 * 라벨·섹션 제목 옆에 붙는 작은 '?' 도움말. 클릭하면 설명 팝오버가 뜨고, 바깥을 누르거나
 * Esc를 누르면 닫힌다. 설명을 항상 펼쳐두지 않고 필요할 때만 보여줘 폼을 깔끔하게 유지한다.
 */
export function HelpTip({ text, label = "도움말" }: { text: string; label?: string }) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (!open) return;
    function handlePointer(event: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(event.target as Node)) setOpen(false);
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", handlePointer);
    document.addEventListener("keydown", handleKey);
    return () => {
      document.removeEventListener("mousedown", handlePointer);
      document.removeEventListener("keydown", handleKey);
    };
  }, [open]);

  return (
    <span ref={wrapRef} className="relative inline-flex align-middle">
      <button
        type="button"
        aria-label={label}
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
        className="inline-grid size-4 place-items-center rounded-full text-muted transition-colors hover:text-ink"
      >
        <HelpCircle size={15} aria-hidden="true" />
      </button>
      {open && (
        <span
          role="tooltip"
          className="surface absolute left-0 top-6 z-20 w-56 p-3 text-xs font-normal leading-5 text-muted"
        >
          {text}
        </span>
      )}
    </span>
  );
}
