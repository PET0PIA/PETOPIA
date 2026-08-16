import { useEffect, useMemo, useState } from "react";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { ApiError } from "../../api/client";
import { getPublicFairs, type FairPublicListItem } from "../../api/fair";
import { FairPublicCard } from "./FairPublicCard";

interface FairListEntry {
  fair: FairPublicListItem;
  ended: boolean;
}

type TabKey = "ALL" | "RESERVABLE" | "UPCOMING" | "ENDED";

const TABS: { key: TabKey; label: string }[] = [
  { key: "ALL", label: "전체" },
  { key: "RESERVABLE", label: "사전예약 중" },
  { key: "UPCOMING", label: "오픈 예정" },
  { key: "ENDED", label: "종료" },
];

// 탭별 빈 목록 안내 문구.
const EMPTY_BY_TAB: Record<TabKey, { title: string; description: string }> = {
  ALL: { title: "등록된 행사가 아직 없어요.", description: "새 행사가 공개되면 이곳에서 확인할 수 있어요." },
  RESERVABLE: { title: "지금 사전예약 중인 행사가 없어요.", description: "예매가 열리면 여기에 표시돼요." },
  UPCOMING: { title: "오픈 예정인 행사가 없어요.", description: "새 행사가 공개되면 이곳에서 확인할 수 있어요." },
  ENDED: { title: "종료된 행사가 없어요.", description: "지난 행사가 이곳에 쌓여요." },
};

// 목록 카드 하단 버튼: 예매 가능하면 예매로, 아직이면 오픈 예정(비활성), 종료면 종료(비활성).
function reserveCta(fair: FairPublicListItem, ended: boolean): { to?: string; label: string } {
  if (ended) return { label: "종료" };
  if (fair.reservable) return { to: `/tickets/${fair.fairId}`, label: "예매하기" };
  return { label: "오픈 예정" };
}

// 한 탭이 어떤 항목을 보여줄지. 전체는 그대로, 나머지는 상태로 거른다.
function filterByTab(entries: FairListEntry[], tab: TabKey): FairListEntry[] {
  switch (tab) {
    case "RESERVABLE":
      return entries.filter((e) => !e.ended && e.fair.reservable);
    case "UPCOMING":
      return entries.filter((e) => !e.ended && !e.fair.reservable);
    case "ENDED":
      return entries.filter((e) => e.ended);
    default:
      return entries;
  }
}

/** 공개 행사 목록. 예정·진행·종료를 한곳에 모아 상단 탭으로 나눠 보고, 시작일 가까운 순으로 정렬한다. */
export function FairListPage() {
  const [upcoming, setUpcoming] = useState<FairPublicListItem[]>([]);
  const [past, setPast] = useState<FairPublicListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabKey>("ALL");

  useEffect(() => {
    let alive = true;
    Promise.all([getPublicFairs("UPCOMING"), getPublicFairs("PAST")])
      .then(([up, pa]) => {
        if (alive) {
          setUpcoming(up);
          setPast(pa);
        }
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "행사 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  // 한 목록으로 합쳐 정렬: 안 끝난 행사 먼저(시작일 오름차순, 일정 미정은 뒤로),
  // 종료 행사는 맨 아래(PAST가 이미 최근 끝난 순).
  const entries = useMemo<FairListEntry[]>(() => {
    const active = upcoming
      .map((fair) => ({ fair, ended: false }))
      .sort((a, b) => {
        const sa = a.fair.operationStartDate;
        const sb = b.fair.operationStartDate;
        if (sa && sb) return sa < sb ? -1 : sa > sb ? 1 : 0;
        if (sa) return -1; // 날짜 있는 게 앞, 일정 미정은 뒤
        if (sb) return 1;
        return 0;
      });
    const ended = past.map((fair) => ({ fair, ended: true }));
    return [...active, ...ended];
  }, [upcoming, past]);

  const visible = useMemo(() => filterByTab(entries, tab), [entries, tab]);

  return (
    <PageContainer className="py-7 sm:py-10">
      <PageHeader title="행사" description="예정된 행사부터 진행 중, 지난 행사까지 한곳에 모아봤어요." />

      {loading ? (
        <FairListSkeleton />
      ) : error ? (
        <EmptyState title="행사 목록을 불러오지 못했어요." description={error} />
      ) : entries.length === 0 ? (
        <EmptyState title={EMPTY_BY_TAB.ALL.title} description={EMPTY_BY_TAB.ALL.description} />
      ) : (
        <>
          <div className="mb-6 grid grid-cols-4 divide-x divide-line overflow-hidden rounded-card border border-line" role="tablist" aria-label="행사 상태">
            {TABS.map(({ key, label }) => {
              const active = key === tab;
              return (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setTab(key)}
                  className={`min-h-11 px-1 text-xs font-bold transition sm:text-sm ${active ? "bg-primary-strong text-white" : "bg-card text-muted hover:text-ink"}`}
                >
                  {label}
                </button>
              );
            })}
          </div>

          {visible.length === 0 ? (
            <EmptyState title={EMPTY_BY_TAB[tab].title} description={EMPTY_BY_TAB[tab].description} />
          ) : (
            <div className="grid grid-cols-2 gap-x-5 gap-y-8 md:grid-cols-3 lg:grid-cols-4">
              {visible.map(({ fair, ended }) => (
                <FairPublicCard
                  key={fair.fairId}
                  fair={fair}
                  ended={ended}
                  to={`/fairs/${fair.fairId}`}
                  action={reserveCta(fair, ended)}
                />
              ))}
            </div>
          )}
        </>
      )}
    </PageContainer>
  );
}

/** 로딩 중 자리표시. 실제 카드와 같은 그리드·비율이라 로딩→목록 전환 때 화면이 튀지 않는다. */
function FairListSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-x-5 gap-y-8 md:grid-cols-3 lg:grid-cols-4" aria-hidden="true">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="flex flex-col gap-3">
          <div className="aspect-[4/5] animate-pulse rounded-card bg-surface-alt" />
          <div className="space-y-2">
            <div className="h-4 w-2/3 animate-pulse rounded bg-surface-alt" />
            <div className="h-3 w-1/2 animate-pulse rounded bg-surface-alt" />
            <div className="h-3 w-2/5 animate-pulse rounded bg-surface-alt" />
          </div>
          <div className="h-11 w-full animate-pulse rounded-pill bg-surface-alt" />
        </div>
      ))}
    </div>
  );
}
