import { AlertCircle } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Card } from "../../components/ui/Card";
import { Select } from "../../components/ui/Select";
import { DonutChart } from "../../components/fair-admin/DonutChart";
import { HourlyEntryTrendChart } from "../../components/fair-admin/HourlyEntryTrendChart";
import { TagRankingBoard } from "../../components/fair-admin/TagRankingBoard";
import { TopTagBars } from "../../components/fair-admin/TopTagBars";
import { useFairSelector } from "../../contexts/FairSelectorContext";
import { ApiError } from "../../api/client";
import { getFairDates, type FairDate } from "../../api/fair";
import { getBoothVisitPattern, getHourlyEntryTrend, getVisitStats, type HourlyEntryTrend, type LabelCount, type VisitStats } from "../../api/statistics";
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
 * 박람회 관리자 콘솔 - 리뷰 관리. 태그 기반 통합 리뷰(V39)의 리뷰(태그) 데이터가 중심이지만,
 * 사용자 요청으로 "방문 통계"(/fair-admin/statistics)·"예약 현황"(/fair-admin/reservations)
 * 화면과 겹치는 지표(총 방문자·시간대별 입장 추이·방문 부스 수 분포)도 이 화면에 그대로
 * 중복 표시한다 - 페이지를 오가지 않고 한 화면에서 리뷰 통계와 함께 볼 수 있게 하기 위함이다.
 * 예약·입장 데이터 자체는 새로 만들지 않고 meltingujin의 statistics API를 그대로 재사용한다
 * (petopia-review-feature-plan 스킬 참고).
 */
export function ReviewManagementPage() {
  const { fairId } = useFairSelector();

  const [reviewStats, setReviewStats] = useState<FairReviewStats | null>(null);
  const [visitStats, setVisitStats] = useState<VisitStats | null>(null);
  const [boothVisitPattern, setBoothVisitPattern] = useState<LabelCount[]>([]);
  const [fairDates, setFairDates] = useState<FairDate[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedDate, setSelectedDate] = useState<string>("");
  const [hourlyTrend, setHourlyTrend] = useState<HourlyEntryTrend[]>([]);
  const [trendLoading, setTrendLoading] = useState(false);
  const [trendError, setTrendError] = useState<string | null>(null);

  useEffect(() => {
    if (fairId === null) return;
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    Promise.all([getFairReviewStats(fairId), getVisitStats(fairId), getFairDates(fairId), getBoothVisitPattern(fairId)])
      .then(([review, visit, dates, pattern]) => {
        if (ignore) return;
        setReviewStats(review);
        setVisitStats(visit);
        setFairDates(dates);
        setBoothVisitPattern(pattern);
        setSelectedDate((current) => (current && dates.some((d) => d.operationDate === current) ? current : (dates[0]?.operationDate ?? "")));
      })
      .catch((error) => {
        if (!ignore) {
          setReviewStats(null);
          setVisitStats(null);
          setFairDates([]);
          setBoothVisitPattern([]);
          setLoadError(error instanceof ApiError ? error.message : "리뷰 통계를 불러오지 못했어요.");
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [fairId]);

  // 시간대별 입장 추이는 운영일 단위 통계라 선택된 날짜가 바뀔 때마다 다시 조회한다.
  useEffect(() => {
    if (fairId === null || selectedDate === "") {
      setHourlyTrend([]);
      return;
    }
    let ignore = false;

    setTrendLoading(true);
    setTrendError(null);
    getHourlyEntryTrend(fairId, selectedDate)
      .then((data) => {
        if (!ignore) setHourlyTrend(data);
      })
      .catch((error) => {
        if (!ignore) {
          setHourlyTrend([]);
          setTrendError(error instanceof ApiError ? error.message : "시간대별 입장 추이를 불러오지 못했어요.");
        }
      })
      .finally(() => {
        if (!ignore) setTrendLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [fairId, selectedDate]);

  const topPositive = useMemo(() => (reviewStats ? flattenTopTags(reviewStats.fairTagRankings, "positiveTop") : []), [reviewStats]);
  const topNegative = useMemo(() => (reviewStats ? flattenTopTags(reviewStats.fairTagRankings, "negativeTop") : []), [reviewStats]);

  return (
    <div className="mx-auto max-w-[1600px] py-2">
      <PageHeader eyebrow="박람회 관리자" title="리뷰 관리" description="태그 기반으로 남긴 방문객 리뷰 통계와 방문 지표를 함께 확인해요." />

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 리뷰 통계가 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      )}

      {fairId !== null && loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">리뷰 통계를 불러오는 중이에요...</div>
      )}

      {fairId !== null && !loading && !loadError && reviewStats && visitStats && (
        <div className="flex flex-col gap-6">
          <div>
            <div className="mb-3">
              <h2 className="text-lg font-extrabold text-ink">방문·리뷰 요약</h2>
              <p className="mt-1 text-sm text-muted">행사 전 기간을 합산한 지표예요.</p>
            </div>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">
              {[
                { label: "총 방문자", value: `${visitStats.totalVisitors}명` },
                { label: "확정 예약", value: `${visitStats.totalConfirmedReservations}건` },
                { label: "방문율", value: `${visitStats.visitRate}%` },
                { label: "평균 반려동물 나이", value: visitStats.avgPetAge === null ? "-" : `${visitStats.avgPetAge}세` },
                { label: "1인 평균 부스 방문", value: visitStats.avgBoothsPerVisitor == null ? "-" : `${visitStats.avgBoothsPerVisitor}개` },
                { label: "총 리뷰 수", value: `${reviewStats.reviewCount}건` },
                { label: "재방문 의향", value: `${Math.round(reviewStats.revisitRate * 1000) / 10}%` },
              ].map((tile) => (
                <Card key={tile.label} className="p-4">
                  <p className="text-xs font-bold text-muted">{tile.label}</p>
                  <p className="mt-1.5 text-xl font-extrabold tabular-nums text-ink">{tile.value}</p>
                </Card>
              ))}
            </div>
          </div>

          {/* 시간대별 방문자 수·방문 부스 수 분포는 리뷰와 무관한 예약·입장 통계라, 리뷰가
              0건이어도(reviewStats.reviewCount === 0) 항상 보여준다. 리뷰 자체에서 나오는
              지표(방문객 특성·만족도 상세 분석·카테고리별 태그 랭킹)만 아래 조건부에 둔다. */}
          <div className="grid min-w-0 grid-cols-1 gap-6 xl:grid-cols-2">
            <Card className="min-w-0 p-6">
              <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
                <div>
                  <h2 className="text-lg font-extrabold text-ink">시간대별 방문자 수</h2>
                  <p className="mt-1 text-sm text-muted">선택한 운영일에 실제로 입장(QR 체크인)한 시각 기준 시간대별 건수예요.</p>
                </div>
                {fairDates.length > 0 && (
                  <div className="w-40 shrink-0">
                    <Select value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)}>
                      {fairDates.map((date) => <option key={date.fairDateId} value={date.operationDate}>{date.operationDate}</option>)}
                    </Select>
                  </div>
                )}
              </div>

              {fairDates.length === 0 && (
                <EmptyState title="등록된 운영일이 없어요." description="운영일이 먼저 등록돼야 입장 추이를 볼 수 있어요." />
              )}
              {fairDates.length > 0 && trendError && (
                <p className="text-sm font-bold text-primary-strong">{trendError}</p>
              )}
              {fairDates.length > 0 && !trendError && trendLoading && (
                <div className="grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>
              )}
              {fairDates.length > 0 && !trendError && !trendLoading && (
                <>
                  <HourlyEntryTrendChart data={hourlyTrend} />
                  {hourlyTrend.length === 0 && (
                    <p className="mt-4 text-center text-sm text-muted">이 운영일에는 아직 입장 기록이 없어요.</p>
                  )}
                </>
              )}
            </Card>

            <Card className="min-w-0 p-6">
              <div className="mb-5">
                <h2 className="text-lg font-extrabold text-ink">방문 부스 수 분포</h2>
                <p className="mt-1 text-sm text-muted">방문객이 이번 행사에서 몇 개의 부스를 방문했는지 분포예요.</p>
              </div>
              <DonutChart data={boothVisitPattern} unit="명" />
            </Card>
          </div>

          {reviewStats.reviewCount === 0 ? (
            <EmptyState title="아직 리뷰가 없어요." description="방문객이 리뷰를 남기면 이곳에서 만족도 통계를 확인할 수 있어요." />
          ) : (
            <>
              <div className="grid min-w-0 grid-cols-1 gap-6 xl:grid-cols-2">
                <Card className="min-w-0 p-6">
                  <div className="mb-5">
                    <h2 className="text-lg font-extrabold text-ink">방문객 특성</h2>
                    <p className="mt-1 text-sm text-muted">리뷰를 남긴 방문객의 동반유형·방문목적 분포예요.</p>
                  </div>
                  <div className="grid grid-cols-1 gap-x-6 gap-y-6 sm:grid-cols-2">
                    <div className="min-w-0">
                      <h3 className="mb-3 text-sm font-bold text-ink">동반유형</h3>
                      <DonutChart data={toDonutData(reviewStats.companionTypeDistribution, companionTypeLabels)} unit="건" />
                    </div>
                    <div className="min-w-0">
                      <h3 className="mb-3 text-sm font-bold text-ink">방문목적</h3>
                      <DonutChart data={toDonutData(reviewStats.visitPurposeDistribution, visitPurposeLabels)} unit="건" />
                    </div>
                  </div>
                </Card>

                <Card className="min-w-0 p-6">
                  <div className="mb-5">
                    <h2 className="text-lg font-extrabold text-ink">만족도 상세 분석</h2>
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
                  <h2 className="text-lg font-extrabold text-ink">카테고리별 태그 랭킹</h2>
                  <p className="mt-1 text-sm text-muted">카테고리마다 많이 선택된 태그 상위 5개를 좋았어요/아쉬웠어요로 나눠 보여줘요.</p>
                </div>
                <TagRankingBoard rankings={reviewStats.fairTagRankings} />
              </Card>
            </>
          )}
        </div>
      )}
    </div>
  );
}
