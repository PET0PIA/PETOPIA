import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { HelpTip } from "../ui/HelpTip";

interface SectionHeaderProps { title: string; description?: string; help?: string; linkTo?: string; linkLabel?: string; icon?: ReactNode; centered?: boolean; }

/**
 * icon은 선택 - 제목 앞에 작은 아이콘을 붙이고 싶을 때만 넘긴다. help는 제목 옆 '?' 클릭 툴팁.
 * centered는 ocreo식 가운데 정렬 헤더.
 *
 * 가운데 정렬 + linkTo일 때 제목에 좌우 여백(px-8)을 주는 이유: '+' 링크는 헤더 오른쪽 위에
 * 겹쳐 놓기(absolute) 때문에, 제목이 길어 두 줄로 접히면 첫 줄 끝이 '+' 아래로 들어가 글자와
 * 겹친다. 여백으로 그 자리를 미리 비워 둔다(양쪽에 똑같이 줘서 가운데 정렬은 유지).
 */
export function SectionHeader({ title, description, help, linkTo, linkLabel = "전체 보기", icon, centered = false }: SectionHeaderProps) {
  const moreLink = linkTo && <Link to={linkTo} aria-label={linkLabel} className="shrink-0 text-2xl font-bold leading-none text-ink transition hover:text-muted">+</Link>;
  if (centered) {
    return <div className="relative mb-6 text-center"><h2 className={`flex items-center justify-center gap-2 text-xl font-extrabold tracking-tight text-ink sm:text-2xl${linkTo ? " px-8" : ""}`}>{icon}{title}{help && <HelpTip text={help} />}</h2>{description && <p className="mt-1.5 text-sm text-muted">{description}</p>}{linkTo && <div className="absolute right-0 top-1">{moreLink}</div>}</div>;
  }
  return <div className="mb-5 flex items-end justify-between gap-4"><div><h2 className="flex items-center gap-2 text-xl font-extrabold tracking-tight text-ink sm:text-2xl">{icon}{title}{help && <HelpTip text={help} />}</h2>{description && <p className="mt-1.5 text-sm text-muted">{description}</p>}</div>{moreLink}</div>;
}
