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
        onClose();
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
  }, [open, onClose]);

  if (!open) return null;

  return <div className="fixed inset-0 z-50 grid place-items-center bg-ink/30 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section ref={dialogRef} className="surface w-full max-w-md p-6" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}><div className="mb-4 flex items-center justify-between"><h2 id={titleId} className="text-lg font-extrabold">{title}</h2><button type="button" aria-label="대화상자 닫기" className="rounded-button p-2 hover:bg-page" onClick={onClose}><X size={18} /></button></div>{children}</section></div>;
}
