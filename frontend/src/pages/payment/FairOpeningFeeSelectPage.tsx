import { ChevronRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getAssignedFairs, getFairOpeningFeeSummary, type AssignedFairSummary, type FairStatus } from "../../api/fair";

// 개설비 결제 상태 배지 - FairOpeningFeePaymentPage.tsx의 STATUS_MESSAGE와 같은 상태값 기준.
const OPENING_FEE_STATUS_LABELS: Record<FairStatus, string> = {
  RECEIVED: "승인 대기",
  REJECTED: "반려됨",
  EXPIRED: "결제 기한 만료",
  PAYMENT_PENDING: "결제 대기",
  PREPARING: "결제 완료",
  IN_PROGRESS: "결제 완료",
  ENDED: "종료",
};

const OPENING_FEE_STATUS_TONES: Record<FairStatus, "sun" | "leaf" | "neutral" | "primary"> = {
  RECEIVED: "neutral",
  REJECTED: "primary",
  EXPIRED: "primary",
  PAYMENT_PENDING: "sun",
  PREPARING: "leaf",
  IN_PROGRESS: "leaf",
  ENDED: "neutral",
};

interface FairWithStatus extends AssignedFairSummary {
  status: FairStatus | null;
  canceledAt: string | null;
}

/**
 * 개설비 결제 페이지의 진입점(사이트 내비 전용, fairId 없이 들어온다). 배정된 행사가
 * 하나면 그 행사의 결제 페이지로 바로 넘기고, 여러 개면 고를 수 있게 목록을 보여준다.
 * 각 행사마다 개설비 결제 상태(승인대기/결제대기/결제완료 등)도 같이 보여준다(2026-08-22).
 *
 * meltingujin이 작업 중인 useFairSelector 훅을 쓰지 않는다 - 그 훅이 활발히 바뀌고
 * 있어 의존하면 충돌 위험이 있고, 여기서 필요한 건 getAssignedFairs() 하나뿐이다.
 */
export function FairOpeningFeeSelectPage() {
  const [fairs, setFairs] = useState<FairWithStatus[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getAssignedFairs()
      .then(async (res) => {
        // 상태 조회 하나가 실패해도 나머지 행사는 그대로 보여줘야 하니, 실패한 것만
        // status: null로 남기고(배지 없이) 전체 목록 렌더는 막지 않는다.
        const withStatus = await Promise.all(
          res.map(async (fair): Promise<FairWithStatus> => {
            try {
              const summary = await getFairOpeningFeeSummary(fair.fairId);
              return { ...fair, status: summary.status, canceledAt: summary.canceledAt };
            } catch {
              return { ...fair, status: null, canceledAt: null };
            }
          }),
        );
        if (alive) setFairs(withStatus);
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
    return <Navigate to={`/fair-admin/payments/fair-opening-fee/${fairs[0].fairId}`} replace />;
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader eyebrow="개설비 결제" title="담당 행사 선택" description="개설비를 결제할 행사를 골라 주세요." />
      <div className="flex flex-col gap-3">
        {fairs.map((fair) => (
          <Link key={fair.fairId} to={`/fair-admin/payments/fair-opening-fee/${fair.fairId}`}>
            <Card className="flex items-center justify-between p-4 hover:bg-page">
              <div className="flex items-center gap-2">
                <span className="font-bold text-ink">{fair.name}</span>
                {/* 취소된 행사는 승인대기/결제대기 같은 기존 상태 배지 대신 취소 배지만
                    보여준다(2026-08-23) - status는 취소 승인 후에도 그대로라 같이 보여주면
                    "결제 대기"인데 취소된 것처럼 헷갈린다. */}
                {fair.canceledAt ? (
                  <Badge tone="neutral">행사취소</Badge>
                ) : (
                  fair.status && (
                    <Badge tone={OPENING_FEE_STATUS_TONES[fair.status]}>{OPENING_FEE_STATUS_LABELS[fair.status]}</Badge>
                  )
                )}
              </div>
              <ChevronRight size={18} className="text-muted" />
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
