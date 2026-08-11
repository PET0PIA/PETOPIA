import { Link } from "react-router-dom";
import type { Fair } from "../../types/domain";
import { StatusBadge } from "../common/StatusBadge";

export function FairCard({ fair }: { fair: Fair }) {
  return <Link to={`/fairs/upcoming?fair=${fair.id}`} className="group block"><div className="aspect-[3/4] overflow-hidden rounded-card bg-page"><img src={fair.imageUrl} alt={`${fair.name} 행사 포스터`} className="size-full object-cover transition duration-300 group-hover:scale-[1.03]" /></div><div className="mt-3"><StatusBadge status={fair.status} /><h3 className="mt-2 text-base font-extrabold text-ink">{fair.name}</h3><p className="mt-1 text-sm text-muted">{fair.dates}</p><p className="text-sm text-muted">{fair.location}</p></div></Link>;
}
