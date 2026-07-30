import { useEffect, useRef, useState, type ReactNode } from "react";
import { ChevronDown } from "lucide-react";

interface DropdownItem { label: string; onSelect: () => void; }
interface DropdownMenuProps { label: string; items: DropdownItem[]; children?: ReactNode; }

export function DropdownMenu({ label, items, children }: DropdownMenuProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const close = (event: MouseEvent) => { if (!ref.current?.contains(event.target as Node)) setOpen(false); };
    window.addEventListener("mousedown", close);
    return () => window.removeEventListener("mousedown", close);
  }, []);
  return <div ref={ref} className="relative">{children ?? <button type="button" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((value) => !value)} className="flex items-center gap-1 rounded-button px-3 py-2 text-sm font-semibold text-ink hover:bg-page">{label}<ChevronDown size={15} aria-hidden="true" /></button>}{open && <div role="menu" className="surface absolute left-0 top-full z-30 mt-2 min-w-48 overflow-hidden p-1">{items.map((item) => <button key={item.label} role="menuitem" type="button" className="block w-full rounded-xl px-3 py-2.5 text-left text-sm text-muted hover:bg-page hover:text-ink" onClick={() => { item.onSelect(); setOpen(false); }}>{item.label}</button>)}</div>}</div>;
}
