import { ArrowRight, MapPin } from "lucide-react";
import { Link } from "react-router-dom";
import { boothPreviews } from "../../mocks/home";
import { SectionHeader } from "../common/SectionHeader";

const accentClass = { primary: "bg-primary-soft text-primary-strong", sun: "bg-sun-soft text-ink", leaf: "bg-leaf-soft text-ink" };
export function BoothPreviewSection() { return <section><SectionHeader title="이번 행사의 추천 부스" description="좋아할 만한 브랜드를 미리 만나보세요." linkTo="/businesses" linkLabel="참여 업체 보기" /><div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{boothPreviews.map((booth) => <article key={booth.id} className="surface p-5"><div className="flex items-start justify-between"><span className={`grid size-12 place-items-center rounded-button text-sm font-black ${accentClass[booth.accent]}`}>{booth.initials}</span><span className="rounded-full bg-page px-2 py-1 text-xs font-bold text-muted">{booth.boothNumber}</span></div><h3 className="mt-5 font-extrabold">{booth.name}</h3><p className="mt-1 text-sm text-muted">{booth.category}</p><p className="mt-4 flex items-center gap-1 text-xs text-muted"><MapPin size={14} />{booth.fairName}</p><Link to={`/businesses?booth=${booth.id}`} className="mt-4 flex items-center gap-1 text-sm font-bold text-primary-strong hover:underline">상세 보기<ArrowRight size={15} /></Link></article>)}</div></section>; }
