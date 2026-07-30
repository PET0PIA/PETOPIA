import { ArrowRight, CalendarDays, Ticket } from "lucide-react";
import { Link } from "react-router-dom";
import { homeHero } from "../../mocks/home";

export function HeroSection() {
  return <section className="surface relative overflow-hidden bg-card px-6 py-6 sm:px-10 sm:py-8"><div className="relative grid items-center gap-8 md:grid-cols-[0.75fr_1.25fr]"><div className="py-3"><p className="mb-3 text-sm font-extrabold text-ink">PETOPIA와 함께하는 반짝이는 주말</p><h1 className="text-3xl font-black leading-[1.22] tracking-tight sm:text-4xl">반려동물과 함께 만나는<br />즐거운 하루</h1><p className="mt-5 max-w-md text-sm leading-7 text-muted sm:text-base">다가오는 펫페어를 확인하고<br />미리 티켓을 예매해 보세요.</p><div className="mt-7 flex flex-wrap gap-3"><Link to="/fairs/upcoming" className="inline-flex min-h-11 items-center gap-2 rounded-button bg-primary px-4 text-sm font-bold text-white transition hover:opacity-90"><CalendarDays size={17} />예정 행사 보기</Link><Link to="/tickets" className="inline-flex min-h-11 items-center gap-2 rounded-button border border-line bg-card px-4 text-sm font-bold transition hover:bg-page"><Ticket size={17} />티켓 예매하기<ArrowRight size={16} /></Link></div></div><div className="relative mx-auto h-64 w-full sm:h-80 md:h-96"><img src={homeHero.imageUrl} alt={homeHero.imageAlt} className="size-full object-contain" /></div></div></section>;
}
