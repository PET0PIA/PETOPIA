import { useRef } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { upcomingFairs } from "../../mocks/home";
import { FairCard } from "../fair/FairCard";
import { SectionHeader } from "../common/SectionHeader";

export function UpcomingFairSection() {
  const scroller = useRef<HTMLDivElement>(null);
  const scroll = (dir: number) => {
    const el = scroller.current;
    if (!el) return;
    const card = el.firstElementChild as HTMLElement | null;
    const step = card ? card.offsetWidth + 20 : 300;
    el.scrollBy({ left: dir * step, behavior: "smooth" });
  };
  return <section><SectionHeader title="다가오는 행사" linkTo="/fairs/upcoming" centered /><div className="relative"><button type="button" onClick={() => scroll(-1)} aria-label="이전 행사" className="absolute -left-3 top-[38%] z-10 hidden -translate-y-1/2 rounded-pill border border-line bg-card p-2.5 shadow-sm transition hover:bg-page md:block"><ChevronLeft size={20} /></button><div ref={scroller} className="flex snap-x gap-5 overflow-x-auto scroll-smooth pb-2 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">{upcomingFairs.map((fair) => <div key={fair.id} className="w-[85%] shrink-0 snap-start sm:w-[calc(50%_-_0.625rem)] lg:w-[calc(25%_-_0.9375rem)]"><FairCard fair={fair} /></div>)}</div><button type="button" onClick={() => scroll(1)} aria-label="다음 행사" className="absolute -right-3 top-[38%] z-10 hidden -translate-y-1/2 rounded-pill border border-line bg-card p-2.5 shadow-sm transition hover:bg-page md:block"><ChevronRight size={20} /></button></div></section>;
}
