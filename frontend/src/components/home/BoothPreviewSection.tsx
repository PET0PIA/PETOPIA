import { Link } from "react-router-dom";
import { boothPreviews } from "../../mocks/home";
import { SectionHeader } from "../common/SectionHeader";

export function BoothPreviewSection() {
  return <section><SectionHeader title="이번 행사의 추천 부스" linkTo="/businesses" linkLabel="참여 업체 보기" centered /><div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">{boothPreviews.map((booth) => <Link key={booth.id} to={`/businesses?booth=${booth.id}`} className="group block"><div className="aspect-square overflow-hidden rounded-card bg-page"><img src={booth.imageUrl} alt={`${booth.name} 부스 포스터`} className="size-full object-cover transition duration-300 group-hover:scale-[1.03]" /></div><h3 className="mt-2 text-sm font-extrabold text-ink">{booth.name}</h3><p className="text-xs text-muted">{booth.category} · {booth.boothNumber}</p></Link>)}</div></section>;
}
