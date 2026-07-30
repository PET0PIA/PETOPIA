import type { ReactNode } from "react";

interface PageHeaderProps { eyebrow?: string; title: string; description?: string; action?: ReactNode; }

export function PageHeader({ eyebrow, title, description, action }: PageHeaderProps) {
  return <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div>{eyebrow && <p className="mb-2 text-sm font-bold text-primary">{eyebrow}</p>}<h1 className="text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">{title}</h1>{description && <p className="mt-2 text-sm leading-6 text-muted">{description}</p>}</div>{action}</header>;
}
