import { AlertCircle, ArrowLeft, Download } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { downloadVisitStatsExcel } from "../../api/statistics";
import { ReviewStatsSection } from "../../components/fair-admin/ReviewStatsSection";
import { VisitStatsSection } from "../../components/fair-admin/VisitStatsSection";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { useFairSelector } from "../../contexts/FairSelectorContext";

function resolvePathFairId(pathParam: string | undefined): number | null {
  const parsed = Number(pathParam);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export function VisitStatisticsPage() {
  const { fairId: fairIdParam } = useParams<{ fairId: string }>();

  // SUPER_ADMIN이 /admin/dashboard/fairs/:fairId 경로로 들어온 경우 경로 파라미터를 우선 사용한다.
  const fromAdminDashboard = fairIdParam !== undefined;

  const { fairId: selectorFairId } = useFairSelector();

  // 경로 파라미터(SUPER_ADMIN → 대시보드 클릭)나 훅(EVENT_ADMIN 자동 선택)에서 fairId를 결정한다.
  const [overrideFairId, setOverrideFairId] = useState<number | null>(
    () => resolvePathFairId(fairIdParam),
  );

  // fairIdParam이 바뀌면(같은 컴포넌트가 유지된 채 다른 행사로 라우팅) override를 동기화한다.
  useEffect(() => {
    setOverrideFairId(resolvePathFairId(fairIdParam));
  }, [fairIdParam]);

  // 최종 fairId: 대시보드에서 들어온 경우 경로 파라미터만 신뢰한다(무효면 다른 행사로 새지 않도록
  // selectorFairId로 대체하지 않는다). 그 외에는 훅(EVENT_ADMIN 자동 선택 / SUPER_ADMIN 수동 선택)을 쓴다.
  const fairId = fromAdminDashboard ? overrideFairId : selectorFairId;

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  async function handleExport() {
    if (fairId === null) return;
    setExporting(true);
    setExportError(null);
    try {
      await downloadVisitStatsExcel(fairId);
    } catch (error) {
      setExportError(error instanceof ApiError ? error.message : "엑셀 파일을 내려받지 못했어요.");
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="mx-auto max-w-6xl py-2">
      {fromAdminDashboard && (
        <Link to="/admin/dashboard" className="mb-4 inline-flex items-center gap-1 text-sm font-bold text-muted hover:text-primary-strong">
          <ArrowLeft size={16} />
          전체 운영 대시보드로
        </Link>
      )}
      <PageHeader
        eyebrow={fromAdminDashboard ? "전체 운영" : "행사 관리자"}
        title="방문 통계"
        description="운영일별 시간대 입장 추이와 부스별 고유 관람객 수를 확인해요."
        action={
          fairId !== null && (
            <Button type="button" variant="outline" onClick={handleExport} disabled={exporting}>
              <Download size={16} />
              {exporting ? "내보내는 중..." : "엑셀로 내보내기"}
            </Button>
          )
        }
      />

      {exportError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{exportError}</p>
        </div>
      )}

      {fromAdminDashboard && fairId === null && (
        <EmptyState title="잘못된 행사 경로예요." description="전체 운영 대시보드에서 다시 시도해 주세요." />
      )}

      {!fromAdminDashboard && fairId === null && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 방문 통계가 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      )}

      {fairId !== null && (
        <div className="flex flex-col gap-10">
          <VisitStatsSection fairId={fairId} />
          <ReviewStatsSection fairId={fairId} />
        </div>
      )}
    </div>
  );
}
