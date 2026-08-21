import { AlertCircle, Download } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Card } from "../../components/ui/Card";
import { Button } from "../../components/ui/Button";
import { DonutChart } from "../../components/fair-admin/DonutChart";
import { TagRankingBoard } from "../../components/fair-admin/TagRankingBoard";
import { TopTagBars } from "../../components/fair-admin/TopTagBars";
import { VisitStatsSection } from "../../components/fair-admin/VisitStatsSection";
import { useFairSelector } from "../../contexts/FairSelectorContext";
import { ApiError } from "../../api/client";
import { downloadVisitStatsExcel } from "../../api/statistics";
import { getFairReviewStats, type CompanionType, type CountItem, type FairReviewStats, type TagCountItem, type VisitPurpose } from "../../api/fairReviewStats";

const companionTypeLabels: Record<string, string> = {
  ALONE: "혼자",
  WITH_PET: "반려동물과 함께",
  WITH_FAMILY: "가족과 함께",
  WITH_FRIEND: "친구와 함께",
} satisfies Record<CompanionType, string>;

const visitPurposeLabels: Record<string, string> = {
  SHOPPING: "쇼핑",
  EXPERIENCE: "체험",
  INFO: "정보 습득",
  ETC: "기타",
} satisfies Record<VisitPurpose, string>;

function toDonutData(items: CountItem[], labels: Record<string, string>) {
  return items.map((item) => ({ label: labels[item.key] ?? item.key, count: item.count }));
}

const TOP_N = 5;

/** 카테고리 구분 없이 전체 태그를 통틀어 count 내림차순 TOP N을 뽑는다. */
function flattenTopTags(rankings: FairReviewStats["fairTagRankings"], pick: "positiveTop" | "negativeTop"): TagCountItem[] {
  return rankings
    .flatMap((ranking) => ranking[pick])
    .sort((a, b) => b.count - a.count)
    .slice(0, TOP_N);
}

/**
 * 박람회 관리자 콘솔 - 방문·리뷰 통계. 예전엔 "방문 통계"(VisitStatisticsPage)와
 * "리뷰 통계"(ReviewManagementPage)가 별도 페이지였고, 리뷰 페이지가 방문 통계 위젯 일부를
 * 그대로 베껴 중복 표시했다. 방문 통계 부분은 VisitStatsSection으로 뽑아 재사용하고,
 * 한 화면으로 합쳐 페이지를 오가지 않고도 방문·리뷰 지표를 함께 볼 수 있게 한다.
 */
export function FairStatsPage() {
  const { fairId } = useFairSelector();

  const [reviewStats, setReviewStats] = useState<FairReviewStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  useEffect(() => {
    if (fairId === null) return;
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    getFairReviewStats(fairId)
      .then((review) => { if (!ignore) setReviewStats(review); })
      .catch((error) => {
        if (!ignore) {
          setReviewStats(null);
          setLoadError(error instanceof ApiError ? error.message : "리뷰 통계를 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId]);

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

  const topPositive = useMemo(() => (reviewStats ? flattenTopTags(reviewStats.fairTagRankings, "positiveTop") : []), [reviewStats]);
  const topNegative = useMemo(() => (reviewStats ? flattenTopTags(reviewStats.fairTagRankings, "negativeTop") : []), [reviewStats]);

  return (
    <div className="mx-auto max-w-[1600px] py-2">
      <PageHeader
        eyebrow="박람회 관리자"
        title="방문·리뷰 통계"
        description="방문 통계와 태그 기반 리뷰 통계를 한 화면에서 확인해요."
        action={
          fairId !== null && (
            <Button type="button" variant="outline" onClick={handleExport} disabled={exporting}>
              <Download size={16} />
              {exporting ? "내보내는 중..." : "방문 통계 엑셀로 내보내기"}
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
      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 통계가 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      )}

      {fairId !== null && (
        <div className="flex flex-col gap-10">
          <VisitStatsSection fairId={fairId} />

          <div className="flex flex-col gap-6">
            <div>
              <h2 className="text-xl font-extrabold text-ink">리뷰 통계</h2>
              <p className="mt-1 text-sm text-muted">방문객이 태그로 남긴 만족도·특성 통계예요.</p>
            </div>

            {loading && (
              <div className="surface grid min-h-40 place-items-center text-sm text-muted">리뷰 통계를 불러오는 중이에요...</div>
            )}

            {!loading && reviewStats && (
              <>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-2 lg:grid-cols-2">
                  <Card className="p-4">
                    <p className="text-xs font-bold text-muted">총 리뷰 수</p>
                    <p className="mt-1.5 text-xl font-extrabold tabular-nums text-ink">{reviewStats.reviewCount}건</p>
                  </Card>
                  <Card className="p-4">
                    <p className="text-xs font-bold text-muted">재방문 의향</p>
                    <p className="mt-1.5 text-xl font-extrabold tabular-nums text-ink">{Math.round(reviewStats.revisitRate * 1000) / 10}%</p>
                  </Card>
                </div>

                {reviewStats.reviewCount === 0 ? (
                  <EmptyState title="아직 리뷰가 없어요." description="방문객이 리뷰를 남기면 이곳에서 만족도 통계를 확인할 수 있어요." />
                ) : (
                  <>
                    <div className="grid min-w-0 grid-cols-1 gap-6 xl:grid-cols-2">
                      <Card className="min-w-0 p-6">
                        <div className="mb-5">
                          <h3 className="text-lg font-extrabold text-ink">방문객 특성</h3>
                          <p className="mt-1 text-sm text-muted">리뷰를 남긴 방문객의 동반유형·방문목적 분포예요.</p>
                        </div>
                        <div className="grid grid-cols-1 gap-x-6 gap-y-6 sm:grid-cols-2">
                          <div className="min-w-0">
                            <h4 className="mb-3 text-sm font-bold text-ink">동반유형</h4>
                            <DonutChart data={toDonutData(reviewStats.companionTypeDistribution, companionTypeLabels)} unit="건" />
                          </div>
                          <div className="min-w-0">
                            <h4 className="mb-3 text-sm font-bold text-ink">방문목적</h4>
                            <DonutChart data={toDonutData(reviewStats.visitPurposeDistribution, visitPurposeLabels)} unit="건" />
                          </div>
                        </div>
                      </Card>

                      <Card className="min-w-0 p-6">
                        <div className="mb-5">
                          <h3 className="text-lg font-extrabold text-ink">만족도 상세 분석</h3>
                          <p className="mt-1 text-sm text-muted">카테고리 구분 없이 전체를 통틀어 가장 많이 선택된 태그 상위 5개예요.</p>
                        </div>
                        <div className="grid grid-cols-1 gap-x-6 gap-y-6 sm:grid-cols-2">
                          <div className="min-w-0">
                            <p className="mb-3 text-sm font-bold text-ink">긍정 평가 TOP 5</p>
                            <TopTagBars items={topPositive} color="leaf" />
                          </div>
                          <div className="min-w-0">
                            <p className="mb-3 text-sm font-bold text-ink">개선 필요 TOP 5</p>
                            <TopTagBars items={topNegative} color="coral" />
                          </div>
                        </div>
                      </Card>
                    </div>

                    <Card className="p-6">
                      <div className="mb-5">
                        <h3 className="text-lg font-extrabold text-ink">카테고리별 태그 랭킹</h3>
                        <p className="mt-1 text-sm text-muted">카테고리마다 많이 선택된 태그 상위 5개를 좋았어요/아쉬웠어요로 나눠 보여줘요.</p>
                      </div>
                      <TagRankingBoard rankings={reviewStats.fairTagRankings} />
                    </Card>
                  </>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
