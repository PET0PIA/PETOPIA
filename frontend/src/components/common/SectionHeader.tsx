import { ArrowRight } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

interface SectionHeaderProps { title: string; description?: string; linkTo?: string; linkLabel?: string; icon?: ReactNode; }

/** icon은 선택 - 제목 앞에 작은 아이콘을 붙이고 싶을 때만 넘긴다(기존 호출부는 안 바꿔도 됨). */
export function SectionHeader({ title, description, linkTo, linkLabel = "전체 보기", icon }: SectionHeaderProps) {
  return <div className="mb-5 flex items-end justify-between gap-4"><div><h2 className="flex items-center gap-2 text-xl font-extrabold tracking-tight text-ink sm:text-2xl">{icon}{title}</h2>{description && <p className="mt-1.5 text-sm text-muted">{description}</p>}</div>{linkTo && <Link to={linkTo} className="inline-flex shrink-0 items-center gap-1 text-sm font-bold text-primary-strong hover:underline">{linkLabel}<ArrowRight size={16} /></Link>}</div>;
}
