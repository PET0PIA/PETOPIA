import { useEffect, useMemo, useState } from "react";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { ApiError } from "../../api/client";
import { getPublicFairs, type FairPublicListItem } from "../../api/fair";
import { FairPublicCard } from "./FairPublicCard";
import { isFairInProgress } from "./fairCard";

interface FairListEntry {
  fair: FairPublicListItem;
  ended: boolean;
}

type TabKey = "ALL" | "RESERVABLE" | "IN_PROGRESS" | "UPCOMING" | "ENDED";

/*
 * 탭 라벨은 다섯 칸에 맞춰 띄어쓰기를 뺀다("사전예약 중"→"사전예약", "오픈 예정"→"오픈예정").
 * 폭을 넓히는 대신 칸을 좁혔기 때문에 글자가 들어갈 자리가 한정된다 - 지원 최소 폭인 320px
 * (theme.css의 body min-width)에서 한 칸 안쪽이 48px이고, 여기에 text-xs 굵은 NanumSquareNeo로
 * "사전예약"이 45.5px다. 띄어쓰기를 넣은 "사전예약 중"은 이 자리를 넘긴다.
 *
 * "사전예약"을 "예약"으로 더 줄이지 않는 이유: 이 앱은 사전예약과 현장예매가 따로 있어
 * 그냥 "예약"이라고 하면 어느 쪽인지 알 수 없다.
 */
const TABS: { key: TabKey; label: string }[] = [
  { key: "ALL", label: "전체" },
  { key: "RESERVABLE", label: "사전예약" },
  { key: "IN_PROGRESS", label: "진행중" },
  { key: "UPCOMING", label: "오픈예정" },
  { key: "ENDED", label: "종료" },
];

// 탭별 빈 목록 안내 문구.
const EMPTY_BY_TAB: Record<TabKey, { title: string; description: string }> = {
  ALL: { title: "등록된 행사가 아직 없어요.", description: "새 행사가 공개되면 이곳에서 확인할 수 있어요." },
  RESERVABLE: { title: "지금 사전예약 중인 행사가 없어요.", description: "예매가 열리면 여기에 표시돼요." },
  IN_PROGRESS: { title: "지금 진행 중인 행사가 없어요.", description: "행사가 시작되면 이곳에 표시돼요." },
  UPCOMING: { title: "오픈 예정인 행사가 없어요.", description: "새 행사가 공개되면 이곳에서 확인할 수 있어요." },
  ENDED: { title: "종료된 행사가 없어요.", description: "지난 행사가 이곳에 쌓여요." },
};

/** 운영 중 판정. 카드 CTA와 탭 필터가 반드시 같은 기준을 써야 해서 한곳을 거친다. */
function inProgress(fair: FairPublicListItem): boolean {
  return isFairInProgress(fair.status, fair.operationStartDate, fair.operationEndDate);
}

/*
 * 목록 카드 하단 버튼. 예매 화면(/tickets/:fairId)은 사전예약과 현장예매를 **둘 다** 다루므로,
 * 둘 중 하나라도 가능성이 있으면 비활성 버튼이 아니라 링크를 준다.
 *
 * - reservable: 사전예약 가능(예매 기간 안 + 자리 남은 운영일 존재) → "예매하기"
 * - IN_PROGRESS: 운영 중인 행사 → "현장예매". 사전예약이 닫혔어도 현장예매는 열려 있을 수
 *   있고, 그 화면은 오늘이 운영기간 안이면 현장예매 폼을 띄운다(TicketReservationPage).
 *
 * reservable을 먼저 보는 이유: 여러 날 열리는 행사는 운영 중에도 남은 날짜 사전예약이 열려
 * 있다. 그 화면은 두 유형을 함께 보여주므로 더 넓은 쪽인 "예매하기"로 부르는 게 맞다.
 *
 * 진행 중을 비활성으로 두면 안 되는 이유: 현장예매 입구가 이 버튼뿐이라, 회색 버튼으로
 * 막으면 실제로 가능한 현장예매까지 차단된다. 판매 상태(OPEN/PAUSED/CLOSED)·입장 마감 시각
 * 같은 최종 판정은 백엔드 OnsiteReservationService가 하므로 여기서 미리 닫지 않는다.
 */
function reserveCta(fair: FairPublicListItem, ended: boolean): { to?: string; label: string } {
  if (ended) return { label: "종료" };
  if (fair.reservable) return { to: `/tickets/${fair.fairId}`, label: "예매하기" };
  if (inProgress(fair)) return { to: `/tickets/${fair.fairId}`, label: "현장예매" };
  return { label: "오픈 예정" };
}

/*
 * 한 탭이 어떤 항목을 보여줄지. 전체는 그대로, 나머지는 상태로 거른다.
 *
 * 사전예약과 진행중은 서로 다른 질문("지금 예약할 수 있나" / "지금 열리고 있나")이라 한
 * 행사가 양쪽에 함께 나올 수 있다 - 여러 날 행사는 운영 중에도 남은 날짜 사전예약이 열려
 * 있기 때문이다. 배타적으로 나누면 그 행사를 어느 한쪽에서 잃는다.
 *
 * 여기서 "오픈"은 행사 개막이 아니라 **예매 오픈**을 뜻한다(카드 버튼의 "오픈 예정"과 같은
 * 기준). 그래서 예매가 열렸거나 이미 진행 중인 행사는 이 탭에서 빠진다.
 */
function filterByTab(entries: FairListEntry[], tab: TabKey): FairListEntry[] {
  switch (tab) {
    case "RESERVABLE":
      return entries.filter((e) => !e.ended && e.fair.reservable);
    case "IN_PROGRESS":
      return entries.filter((e) => !e.ended && inProgress(e.fair));
    case "UPCOMING":
      return entries.filter((e) => !e.ended && !e.fair.reservable && !inProgress(e.fair));
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
          <div className="mb-6 grid grid-cols-5 divide-x divide-line overflow-hidden rounded-card border border-line" role="tablist" aria-label="행사 상태">
            {TABS.map(({ key, label }) => {
              const active = key === tab;
              return (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setTab(key)}
                  className={`min-h-11 whitespace-nowrap px-1 text-xs font-bold transition sm:text-sm ${active ? "bg-primary-strong text-white" : "bg-card text-muted hover:text-ink"}`}
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
