import { useEffect, useMemo, useState } from "react";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { ApiError } from "../../api/client";
import { getPublicFairs, type FairPublicListItem } from "../../api/fair";
import { FairPublicCard } from "../fair/FairPublicCard";

interface FairListEntry {
  fair: FairPublicListItem;
  ended: boolean;
}

/** 행사별 참여 기업(확정 부스) 목록 - 행사를 고르면 그 행사의 확정 부스 목록으로 이동한다. */
export function BusinessesByFairPage() {
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

  const entries = useMemo<FairListEntry[]>(() => {
    const active = upcoming.map((fair) => ({ fair, ended: false }));
    const ended = past.map((fair) => ({ fair, ended: true }));
    return [...active, ...ended];
  }, [upcoming, past]);

  return (
    <PageContainer className="py-7 sm:py-10">
      <PageHeader eyebrow="참여 기업" title="행사별 참여 기업" description="행사를 선택하면 확정된 참가 부스를 볼 수 있어요." />

      {loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!loading && error && <EmptyState title="행사 목록을 불러오지 못했어요." description={error} />}

      {!loading && !error && entries.length === 0 && (
        <EmptyState title="등록된 행사가 아직 없어요." description="새 행사가 열리면 이곳에서 확인할 수 있어요." />
      )}

      {!loading && !error && entries.length > 0 && (
        <div className="grid gap-5 md:grid-cols-3">
          {entries.map(({ fair, ended }) => (
            <FairPublicCard key={fair.fairId} fair={fair} ended={ended} to={`/fairs/${fair.fairId}/booths`} />
          ))}
        </div>
      )}
    </PageContainer>
  );
}