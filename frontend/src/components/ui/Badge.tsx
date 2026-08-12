import type { HTMLAttributes } from "react";

type BadgeTone = "primary" | "sun" | "leaf" | "neutral" | "ink";
const toneClass: Record<BadgeTone, string> = { primary: "bg-primary-soft text-primary-strong", sun: "bg-sun-soft text-ink", leaf: "bg-leaf-soft text-ink", neutral: "bg-page text-muted", ink: "bg-primary-strong text-white" };

export function Badge({ className = "", children, tone = "neutral", ...props }: HTMLAttributes<HTMLSpanElement> & { tone?: BadgeTone }) {
  return <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-bold ${toneClass[tone]} ${className}`} {...props}>{children}</span>;
}
