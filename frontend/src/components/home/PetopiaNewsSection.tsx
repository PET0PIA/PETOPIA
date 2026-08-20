import { Pin } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { SectionHeader } from "../common/SectionHeader";
import { NOTICE_CATEGORY_LABELS, getNotices, type NoticeCategory, type NoticeListItem } from "../../api/notice";
import { formatShortDate } from "../../utils/date";

const FILTERS: Array<{ value: NoticeCategory | "ALL"; label: string }> = [
  { value: "ALL", label: "전체" },
  { value: "NOTICE", label: NOTICE_CATEGORY_LABELS.NOTICE },
  { value: "EVENT", label: NOTICE_CATEGORY_LABELS.EVENT },
  { value: "GUIDE", label: NOTICE_CATEGORY_LABELS.GUIDE },
  { value: "RECRUIT", label: NOTICE_CATEGORY_LABELS.RECRUIT },
];

/** 홈에서는 최신 6건까지만. 나머지는 "전체 보기"로 /news에서 본다. */
const HOME_LIMIT = 6;

export function PetopiaNewsSection() {
  const [notices, setNotices] = useState<NoticeListItem[] | null>(null);
  const [active, setActive] = useState<NoticeCategory | "ALL">("ALL");

  // 히어로 배너·팝업과 같은 방식으로 이 섹션이 자기 데이터를 직접 받아온다.
  useEffect(() => {
    let ignore = false;
    getNotices()
      .then((data) => { if (!ignore) setNotices(data); })
      .catch(() => { if (!ignore) setNotices([]); });
    return () => { ignore = true; };
  }, []);

  const items = useMemo(
    () => (notices ?? []).filter((item) => active === "ALL" || item.category === active).slice(0, HOME_LIMIT),
    [notices, active],
  );

  // 등록된 소식이 하나도 없으면 빈 껍데기 대신 섹션을 통째로 감춘다(배너 섹션과 동일한 규칙).
  if (notices !== null && notices.length === 0) return null;

  return (
    <section>
      <SectionHeader title="PETOPIA 소식" description="공지와 이벤트를 확인하세요" centered />
      <div className="mb-5 flex flex-wrap justify-center gap-2">
        {FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => setActive(filter.value)}
            aria-pressed={active === filter.value}
            className={`rounded-pill px-3 py-1.5 text-sm font-medium transition ${
              active === filter.value ? "bg-primary text-white" : "bg-surface-alt text-muted hover:bg-line"
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {notices === null ? (
        <div className="grid min-h-40 place-items-center rounded-card text-sm text-muted ring-1 ring-line">불러오는 중이에요...</div>
      ) : items.length === 0 ? (
        <div className="grid min-h-40 place-items-center rounded-card text-sm text-muted ring-1 ring-line">이 분류에는 아직 소식이 없어요.</div>
      ) : (
        <ul className="overflow-hidden rounded-card ring-1 ring-line">
          {items.map((item) => (
            <li key={`${item.category}-${item.id}`} className="border-b border-line last:border-b-0">
              <Link to={item.linkPath} className="flex items-center gap-3 px-5 py-4 transition hover:bg-primary-soft">
                <span className="shrink-0 rounded-pill bg-surface-alt px-2.5 py-1 text-xs font-bold text-ink">
                  {NOTICE_CATEGORY_LABELS[item.category]}
                </span>
                <span className="flex min-w-0 flex-1 items-center gap-1.5">
                  {item.pinned && <Pin size={13} className="shrink-0 text-ink" aria-label="상단 고정" />}
                  <span className="min-w-0 flex-1 truncate text-sm font-bold text-ink">{item.title}</span>
                </span>
                <span className="shrink-0 text-xs text-muted">{formatShortDate(item.createdAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-5 text-center">
        <Link to="/news" className="inline-flex min-h-11 items-center rounded-button border border-line bg-card px-5 text-sm font-bold text-ink transition hover:bg-page">
          소식 전체 보기
        </Link>
      </div>
    </section>
  );
}
