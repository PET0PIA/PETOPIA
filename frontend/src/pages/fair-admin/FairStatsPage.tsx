import { AlertCircle, Download } from "lucide-react";
import { useState } from "react";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Button } from "../../components/ui/Button";
import { ReviewStatsSection } from "../../components/fair-admin/ReviewStatsSection";
import { VisitStatsSection } from "../../components/fair-admin/VisitStatsSection";
import { useFairSelector } from "../../contexts/FairSelectorContext";
import { ApiError } from "../../api/client";
import { downloadVisitStatsExcel } from "../../api/statistics";

/**
 * 박람회 관리자 콘솔 - 방문·리뷰 통계. 예전엔 "방문 통계"(VisitStatisticsPage)와
 * "리뷰 통계"(ReviewManagementPage)가 별도 페이지였고, 리뷰 페이지가 방문 통계 위젯 일부를
 * 그대로 베껴 중복 표시했다. 방문 통계 부분은 VisitStatsSection, 리뷰 통계 부분은
 * ReviewStatsSection으로 뽑아 재사용하고, 한 화면으로 합쳐 페이지를 오가지 않고도 방문·리뷰
 * 지표를 함께 볼 수 있게 한다. 두 섹션 모두 VisitStatisticsPage(SUPER_ADMIN 전체 운영
 * 대시보드 경로)와 공유한다.
 */
export function FairStatsPage() {
  const { fairId } = useFairSelector();

  const [exportingVisit, setExportingVisit] = useState(false);
  const [exportVisitError, setExportVisitError] = useState<string | null>(null);

  async function handleExportVisit() {
    if (fairId === null) return;
    setExportingVisit(true);
    setExportVisitError(null);
    try {
      await downloadVisitStatsExcel(fairId);
    } catch (error) {
      setExportVisitError(error instanceof ApiError ? error.message : "엑셀 파일을 내려받지 못했어요.");
    } finally {
      setExportingVisit(false);
    }
  }

  return (
    <div className="mx-auto max-w-[1600px] py-2">
      <PageHeader
        eyebrow="박람회 관리자"
        title="방문·리뷰 통계"
        description="방문 통계와 태그 기반 리뷰 통계를 한 화면에서 확인해요."
        action={
          fairId !== null && (
            <Button type="button" variant="outline" onClick={handleExportVisit} disabled={exportingVisit}>
              <Download size={16} />
              {exportingVisit ? "내보내는 중..." : "방문 통계 엑셀로 내보내기"}
            </Button>
          )
        }
      />

      {exportVisitError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{exportVisitError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 통계가 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
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
