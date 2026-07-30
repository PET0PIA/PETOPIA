import { ArrowUpRight, CalendarDays, FilePlus2, Store, Ticket } from "lucide-react";
import { Link } from "react-router-dom";

const menus = [
  { title: "예정 행사", description: "다음 펫페어를 찾아보세요", path: "/fairs/upcoming", icon: CalendarDays, tone: "primary" },
  { title: "티켓 예매", description: "빠르고 간편하게 예매해요", path: "/tickets", icon: Ticket, tone: "sun" },
  { title: "참여 부스 신청", description: "우리 브랜드를 소개해요", path: "/participations/new", icon: Store, tone: "leaf" },
  { title: "행사 개최 신청", description: "새로운 행사를 시작해요", path: "/fair-applications/new", icon: FilePlus2, tone: "primary" },
] as const;
const tones = { primary: "bg-primary-soft text-primary-strong", sun: "bg-sun-soft text-ink", leaf: "bg-leaf-soft text-ink" };

export function QuickMenuSection() { return <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{menus.map(({ title, description, path, icon: Icon, tone }) => <Link to={path} key={title} className="surface group relative p-5 transition hover:-translate-y-0.5 hover:border-primary"><span className={`mb-5 grid size-11 place-items-center rounded-button ${tones[tone]}`}><Icon size={21} /></span><h2 className="font-extrabold">{title}</h2><p className="mt-1.5 text-sm text-muted">{description}</p><ArrowUpRight className="absolute right-5 top-5 text-muted transition group-hover:text-primary-strong" size={18} /></Link>)}</section>; }
