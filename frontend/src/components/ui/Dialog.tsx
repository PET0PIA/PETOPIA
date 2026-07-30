import type { ReactNode } from "react";
import { X } from "lucide-react";

interface DialogProps { open: boolean; onClose: () => void; title: string; children: ReactNode; }

export function Dialog({ open, onClose, title, children }: DialogProps) {
  if (!open) return null;
  return <div className="fixed inset-0 z-50 grid place-items-center bg-ink/30 p-4" role="presentation" onMouseDown={onClose}><section className="surface w-full max-w-md p-6" role="dialog" aria-modal="true" aria-labelledby="dialog-title" onMouseDown={(event) => event.stopPropagation()}><div className="mb-4 flex items-center justify-between"><h2 id="dialog-title" className="text-lg font-extrabold">{title}</h2><button type="button" aria-label="대화상자 닫기" className="rounded-button p-2 hover:bg-page" onClick={onClose}><X size={18} /></button></div>{children}</section></div>;
}
