import { Pin } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { ApiError } from "../../api/client";
import { NOTICE_CATEGORY_LABELS, getNotices, type NoticeCategory, type NoticeListItem } from "../../api/notice";
import { formatShortDate } from "../../utils/date";

const FILTERS: Array<{ value: NoticeCategory | "ALL"; label: string }> = [
  { value: "ALL", label: "전체" },
  { value: "NOTICE", label: NOTICE_CATEGORY_LABELS.NOTICE },
  { value: "EVENT", label: NOTICE_CATEGORY_LABELS.EVENT },
  { value: "GUIDE", label: NOTICE_CATEGORY_LABELS.GUIDE },
  { value: "RECRUIT", label: NOTICE_CATEGORY_LABELS.RECRUIT },
];

/** 한 번에 보여줄 개수. "더 보기"를 누를 때마다 이만큼 늘린다(서버 페이징 대신). */
const PAGE_SIZE = 10;

export function NewsListPage() {
  const [items, setItems] = useState<NoticeListItem[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [filter, setFilter] = useState<NoticeCategory | "ALL">("ALL");
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

  // 모집공고까지 합친 전체를 한 번만 받아두고 탭 전환은 화면에서 거른다.
  // 건수가 많지 않아 매번 다시 불러오는 것보다 빠르고, 탭을 눌러도 화면이 깜빡이지 않는다.
  useEffect(() => {
    let ignore = false;
    getNotices()
      .then((data) => { if (!ignore) { setItems(data); setLoadError(null); } })
      .catch((error) => {
        if (ignore) return;
        setItems(null);
        setLoadError(error instanceof ApiError ? error.message : "소식을 불러오지 못했어요.");
      });
    return () => { ignore = true; };
  }, []);

  const filtered = useMemo(
    () => (items ?? []).filter((item) => filter === "ALL" || item.category === filter),
    [items, filter],
  );
  const visible = filtered.slice(0, visibleCount);

  function changeFilter(next: NoticeCategory | "ALL") {
    setFilter(next);
    setVisibleCount(PAGE_SIZE);
  }

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="PETOPIA"
        title="소식·이벤트"
        description="공지와 이벤트, 진행 중인 참가업체 모집공고를 한곳에서 확인하세요."
      />

      <div className="mb-5 flex flex-wrap gap-2">
        {FILTERS.map((item) => (
          <button
            key={item.value}
            type="button"
            onClick={() => changeFilter(item.value)}
            aria-pressed={filter === item.value}
            className={`rounded-pill px-3.5 py-1.5 text-sm font-medium transition ${
              filter === item.value ? "bg-primary text-white" : "bg-surface-alt text-muted hover:bg-line"
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {loadError && <EmptyState title="소식을 불러오지 못했어요" description={loadError} />}

      {!loadError && items === null && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>
      )}

      {!loadError && items !== null && filtered.length === 0 && (
        <EmptyState
          title={filter === "ALL" ? "아직 등록된 소식이 없어요" : "이 분류에는 아직 소식이 없어요"}
          description="새로운 소식이 등록되면 이곳에서 확인할 수 있어요."
        />
      )}

      {filtered.length > 0 && (
        <>
          <ul className="overflow-hidden rounded-card ring-1 ring-line">
            {visible.map((item) => (
              <li key={`${item.category}-${item.id}`} className="border-b border-line last:border-b-0">
                <Link to={item.linkPath} className="flex items-center gap-3 px-5 py-4 transition hover:bg-primary-soft">
                  <span className="shrink-0 rounded-pill bg-surface-alt px-2.5 py-1 text-xs font-bold text-ink">
                    {NOTICE_CATEGORY_LABELS[item.category]}
                  </span>
                  <span className="flex min-w-0 flex-1 items-center gap-1.5">
                    {item.pinned && <Pin size={13} className="shrink-0 text-ink" aria-label="상단 고정" />}
                    <span className="min-w-0 flex-1 truncate text-sm font-bold text-ink">{item.title}</span>
                  </span>
                  {item.fairName && <span className="hidden shrink-0 text-xs text-muted sm:inline">{item.fairName}</span>}
                  <span className="shrink-0 text-xs text-muted">{formatShortDate(item.createdAt)}</span>
                </Link>
              </li>
            ))}
          </ul>

          {visible.length < filtered.length && (
            <div className="mt-5 text-center">
              <button
                type="button"
                onClick={() => setVisibleCount((count) => count + PAGE_SIZE)}
                className="inline-flex min-h-11 items-center rounded-button border border-line bg-card px-5 text-sm font-bold text-ink hover:bg-page"
              >
                더 보기 ({filtered.length - visible.length}건)
              </button>
            </div>
          )}
        </>
      )}
    </PageContainer>
  );
}
