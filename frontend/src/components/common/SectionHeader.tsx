import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";

interface SectionHeaderProps { title: string; description?: string; linkTo?: string; linkLabel?: string; }

export function SectionHeader({ title, description, linkTo, linkLabel = "전체 보기" }: SectionHeaderProps) {
  return <div className="mb-5 flex items-end justify-between gap-4"><div><h2 className="text-xl font-extrabold tracking-tight text-ink sm:text-2xl">{title}</h2>{description && <p className="mt-1.5 text-sm text-muted">{description}</p>}</div>{linkTo && <Link to={linkTo} className="inline-flex shrink-0 items-center gap-1 text-sm font-bold text-primary-strong hover:underline">{linkLabel}<ArrowRight size={16} /></Link>}</div>;
}
