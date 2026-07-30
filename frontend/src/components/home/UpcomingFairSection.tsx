import { upcomingFairs } from "../../mocks/home";
import { FairCard } from "../fair/FairCard";
import { SectionHeader } from "../common/SectionHeader";

export function UpcomingFairSection() { return <section><SectionHeader title="다가오는 행사" description="반려동물과 특별한 추억을 만들 준비를 해보세요." linkTo="/fairs/upcoming" /><div className="grid gap-5 md:grid-cols-3">{upcomingFairs.map((fair) => <FairCard key={fair.id} fair={fair} />)}</div></section>; }
