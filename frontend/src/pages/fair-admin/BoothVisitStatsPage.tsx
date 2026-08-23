import { AlertCircle, ArrowLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getBoothVisitStats, type BoothVisitStat } from "../../api/statistics";
import { BoothVisitRanking } from "../../components/fair-admin/BoothVisitRanking";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Card } from "../../components/ui/Card";

export function BoothVisitStatsPage() {
  const params = useParams<{ fairId: string }>();
  const fairId = Number(params.fairId);

  const [boothStats, setBoothStats] = useState<BoothVisitStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(fairId) || fairId <= 0) { setLoading(false); return; }
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    getBoothVisitStats(fairId)
      .then((stats) => { if (!ignore) setBoothStats(stats); })
      .catch((error) => {
        if (!ignore) {
          setBoothStats([]);
          setLoadError(error instanceof ApiError ? error.message : "부스별 방문 통계를 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId]);

  return (
    <div className="mx-auto max-w-6xl py-2">
      <Link to="/fair-admin/statistics" className="mb-4 inline-flex items-center gap-1 text-sm font-bold text-muted hover:text-ink">
        <ArrowLeft size={16} />방문 통계로
      </Link>

      <PageHeader
        eyebrow="행사 관리자"
        title="부스별 방문 통계"
        description="부스 QR을 스캔한 고유 관람객 수 기준으로, 행사 전 기간을 합산한 전체 순위예요."
      />

      {!Number.isInteger(fairId) || fairId <= 0 ? (
        <EmptyState title="잘못된 행사 ID예요." description="방문 통계 페이지에서 다시 이동해 주세요." />
      ) : loadError ? (
        <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      ) : loading ? (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>
      ) : boothStats.length === 0 ? (
        <EmptyState title="아직 부스 방문 기록이 없어요." description="부스 QR 스캔 기록이 쌓이면 이곳에서 순위를 확인할 수 있어요." />
      ) : (
        <Card className="p-6">
          <BoothVisitRanking data={boothStats} />
        </Card>
      )}
    </div>
  );
}
