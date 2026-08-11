import { homeNotices } from "../../mocks/home";

function NoticeRow({ hidden = false }: { hidden?: boolean }) {
  return <div className="flex shrink-0 items-center" aria-hidden={hidden || undefined}>{homeNotices.map((text, i) => <span key={i} className="flex items-center gap-10 pl-10 text-sm font-medium text-white"><span className="text-white/40">▶▶</span>{text}</span>)}</div>;
}

export function HomeNoticeMarquee() {
  return <div className="overflow-hidden bg-ink py-3" aria-label="공지 안내"><div className="flex w-max animate-[marquee_40s_linear_infinite] hover:[animation-play-state:paused] motion-reduce:animate-none"><NoticeRow /><NoticeRow hidden /></div></div>;
}
