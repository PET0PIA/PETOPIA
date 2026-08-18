import { Inbox } from "lucide-react";
import { Link } from "react-router-dom";

interface EmptyStateProps { title?: string; description?: string; actionTo?: string; actionLabel?: string; }
export function EmptyState({ title = "아직 준비된 내용이 없어요", description = "새로운 소식이 등록되면 이곳에서 확인할 수 있어요.", actionTo, actionLabel = "홈으로 이동" }: EmptyStateProps) {
  return <div className="surface grid min-h-72 place-items-center p-8 text-center"><div><div className="mx-auto mb-4 grid size-14 place-items-center rounded-full bg-sun-soft text-ink"><Inbox size={26} /></div><h2 className="text-lg font-extrabold">{title}</h2><p className="mt-2 max-w-sm text-sm leading-6 text-muted">{description}</p>{actionTo && <Link to={actionTo} className="mt-5 inline-flex min-h-11 items-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page">{actionLabel}</Link>}</div></div>;
}
