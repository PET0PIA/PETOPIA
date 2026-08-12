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

/** 공개 행사 목록. 예정·진행·종료를 한 목록에 모아 상태 배지로 구분하고, 시작일 가까운 순으로 정렬한다. */
export function FairListPage() {
  const [upcoming, setUpcoming] = useState<FairPublicListItem[]>([]);
  const [past, setPast] = useState<FairPublicListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  return (
    <PageContainer className="py-7 sm:py-10">
      <PageHeader title="행사" description="예정된 행사부터 진행 중, 지난 행사까지 한곳에 모아봤어요." />

      {loading ? (
        <FairListSkeleton />
      ) : error ? (
        <EmptyState title="행사 목록을 불러오지 못했어요." description={error} />
      ) : entries.length === 0 ? (
        <EmptyState title="등록된 행사가 아직 없어요." description="새 행사가 공개되면 이곳에서 확인할 수 있어요." />
      ) : (
        <div className="grid gap-5 md:grid-cols-3">
          {entries.map(({ fair, ended }) => (
            <FairPublicCard
              key={fair.fairId}
              fair={fair}
              ended={ended}
              to={ended ? undefined : `/tickets/${fair.fairId}`}
              reservable={fair.reservable}
              recruiting={fair.recruiting}
            />
          ))}
        </div>
      )}
    </PageContainer>
  );
}

/** 로딩 중 자리표시. 실제 카드와 같은 그리드·비율이라 로딩→목록 전환 때 화면이 튀지 않는다. */
function FairListSkeleton() {
  return (
    <div className="grid gap-5 md:grid-cols-3" aria-hidden="true">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="surface overflow-hidden">
          <div className="h-40 animate-pulse bg-surface-alt" />
          <div className="space-y-3 p-5">
            <div className="h-5 w-2/3 animate-pulse rounded bg-surface-alt" />
            <div className="h-4 w-1/2 animate-pulse rounded bg-surface-alt" />
            <div className="h-4 w-2/5 animate-pulse rounded bg-surface-alt" />
          </div>
        </div>
      ))}
    </div>
  );
}
