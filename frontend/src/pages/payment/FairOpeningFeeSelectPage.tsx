import { ChevronRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getAssignedFairs, type AssignedFairSummary } from "../../api/fair";

/**
 * 개설비 결제 페이지의 진입점(사이트 내비 전용, fairId 없이 들어온다). 배정된 행사가
 * 하나면 그 행사의 결제 페이지로 바로 넘기고, 여러 개면 고를 수 있게 목록을 보여준다.
 *
 * meltingujin이 작업 중인 useFairSelector 훅을 쓰지 않는다 - 그 훅이 활발히 바뀌고
 * 있어 의존하면 충돌 위험이 있고, 여기서 필요한 건 getAssignedFairs() 하나뿐이다.
 */
export function FairOpeningFeeSelectPage() {
  const [fairs, setFairs] = useState<AssignedFairSummary[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getAssignedFairs()
      .then((res) => {
        if (alive) setFairs(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "배정된 행사 목록을 불러오지 못했어요.");
      });
    return () => {
      alive = false;
    };
  }, []);

  if (loadError) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <EmptyState title="행사 목록을 볼 수 없어요." description={loadError} actionTo="/" actionLabel="홈으로" />
      </div>
    );
  }

  if (fairs === null) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <p className="py-16 text-center text-sm text-muted">배정된 행사를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (fairs.length === 0) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <EmptyState title="배정된 행사가 없어요." description="담당으로 배정된 행사가 있어야 개설비를 결제할 수 있어요." actionTo="/" actionLabel="홈으로" />
      </div>
    );
  }

  if (fairs.length === 1) {
    return <Navigate to={`/payments/fair-opening-fee/${fairs[0].fairId}`} replace />;
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader eyebrow="개설비 결제" title="담당 행사 선택" description="개설비를 결제할 행사를 골라 주세요." />
      <div className="flex flex-col gap-3">
        {fairs.map((fair) => (
          <Link key={fair.fairId} to={`/payments/fair-opening-fee/${fair.fairId}`}>
            <Card className="flex items-center justify-between p-4 hover:bg-page">
              <span className="font-bold text-ink">{fair.name}</span>
              <ChevronRight size={18} className="text-muted" />
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
