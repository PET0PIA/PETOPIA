import { useEffect, useId, useRef, type ReactNode } from "react";
import { X } from "lucide-react";

interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

const focusableSelector = [
  "a[href]",
  "button:not([disabled])",
  "textarea:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

export function Dialog({ open, onClose, title, children }: DialogProps) {
  const dialogRef = useRef<HTMLElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const titleId = useId();

  // onClose가 호출부에서 인라인 함수로 넘어와도(참조가 매 렌더마다 바뀌어도) 아래
  // 포커스 트랩 effect가 재실행되지 않도록 최신값만 ref로 추적한다. 그렇지 않으면
  // 다이얼로그 안 입력창에 한 글자씩 칠 때마다(부모 리렌더 -> onClose 재생성) effect가
  // 매번 cleanup(이전 포커스로 복귀)+재실행(닫기 버튼으로 재포커스)되어 타이핑 중 포커스가
  // 계속 요동친다.
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return;

    previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const focusFrame = window.requestAnimationFrame(() => {
      const focusableElements = dialogRef.current?.querySelectorAll<HTMLElement>(focusableSelector);
      (focusableElements?.[0] ?? dialogRef.current)?.focus();
    });

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCloseRef.current();
        return;
      }

      if (event.key !== "Tab") return;

      const focusableElements = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(focusableSelector) ?? []);
      if (focusableElements.length === 0) {
        event.preventDefault();
        dialogRef.current?.focus();
        return;
      }

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener("keydown", handleKeyDown);
      previousFocusRef.current?.focus();
    };
  }, [open]);

  if (!open) return null;

  return <div className="fixed inset-0 z-50 grid place-items-center bg-ink/30 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section ref={dialogRef} className="surface w-full max-w-md p-6" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}><div className="mb-4 flex items-center justify-between"><h2 id={titleId} className="text-lg font-extrabold">{title}</h2><button type="button" aria-label="대화상자 닫기" className="rounded-button p-2 hover:bg-page" onClick={onClose}><X size={18} /></button></div>{children}</section></div>;
}
