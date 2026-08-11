import { useState } from "react";
import { petopiaNews, type NewsCategory } from "../../mocks/home";
import { SectionHeader } from "../common/SectionHeader";

const FILTERS = ["전체", "공지", "이벤트", "안내"] as const;
type Filter = (typeof FILTERS)[number];

export function PetopiaNewsSection() {
  const [active, setActive] = useState<Filter>("전체");
  const items = active === "전체" ? petopiaNews : petopiaNews.filter((n) => n.category === (active as NewsCategory));
  return (
    <section>
      <SectionHeader title="PETOPIA 소식" description="공지와 이벤트를 확인하세요" centered />
      <div className="mb-5 flex flex-wrap justify-center gap-2">
        {FILTERS.map((filter) => (
          <button key={filter} type="button" onClick={() => setActive(filter)} className={`rounded-pill px-3 py-1.5 text-sm font-medium transition ${active === filter ? "bg-primary text-white" : "bg-surface-alt text-muted hover:bg-line"}`}>
            {filter}
          </button>
        ))}
      </div>
      <ul className="overflow-hidden rounded-card ring-1 ring-line">
        {items.map((item) => (
          <li key={item.id} className="flex items-center gap-3 border-b border-line px-5 py-4 transition last:border-b-0 hover:bg-primary-soft">
            <span className="shrink-0 rounded-pill bg-surface-alt px-2.5 py-1 text-xs font-bold text-ink">{item.category}</span>
            <span className="min-w-0 flex-1 truncate text-sm font-bold text-ink">{item.title}</span>
            <span className="shrink-0 text-xs text-muted">{item.date}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
