import { Building2, CalendarDays, MapPin } from "lucide-react";
import { Link } from "react-router-dom";
import type { Fair } from "../../types/domain";
import { StatusBadge } from "../common/StatusBadge";

export function FairCard({ fair }: { fair: Fair }) {
  return <article className="surface overflow-hidden"><div className="relative h-40 overflow-hidden"><img src={fair.imageUrl} alt={`${fair.name} 행사 일러스트`} className="size-full object-cover" /><div className="absolute left-4 top-4"><StatusBadge status={fair.status} /></div></div><div className="p-5"><h3 className="text-lg font-extrabold">{fair.name}</h3><dl className="mt-4 space-y-2 text-sm text-muted"><div className="flex gap-2"><CalendarDays size={16} className="mt-0.5 shrink-0" /><dd>{fair.dates}</dd></div><div className="flex gap-2"><MapPin size={16} className="mt-0.5 shrink-0" /><dd>{fair.location}</dd></div><div className="flex gap-2"><Building2 size={16} className="mt-0.5 shrink-0" /><dd>참여 업체 {fair.exhibitorCount}곳</dd></div></dl><Link to={`/fairs/upcoming?fair=${fair.id}`} className="mt-5 inline-flex min-h-11 w-full items-center justify-center rounded-button border border-line bg-card px-4 text-sm font-bold transition hover:bg-page">자세히 보기</Link></div></article>;
}
