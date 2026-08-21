import { useEffect, useState } from "react";
import { ApiError } from "../../api/client";
import { getFairDates, type FairDate } from "../../api/fair";
import {
  getBoothVisitPattern,
  getBoothVisitStats,
  getHourlyEntryTrend,
  getVisitStats,
  type BoothVisitStat,
  type HourlyEntryTrend,
  type LabelCount,
  type VisitStats,
} from "../../api/statistics";
import { BoothVisitRanking } from "./BoothVisitRanking";
import { DonutChart } from "./DonutChart";
import { HourlyEntryTrendChart } from "./HourlyEntryTrendChart";
import { PetBreedBreakdown } from "./PetBreedBreakdown";
import { VisitStatsOverview } from "./VisitStatsOverview";
import { EmptyState } from "../common/EmptyState";
import { Card } from "../ui/Card";
import { Select } from "../ui/Select";
import { Link } from "react-router-dom";

const BOOTH_RANKING_PREVIEW_COUNT = 3;

interface VisitStatsSectionProps {
  fairId: number;
}

/**
 * 방문 통계(예약·입장 데이터 기반) 위젯 묶음. VisitStatisticsPage(/fair-admin/statistics,
 * /admin/dashboard/fairs/:fairId)와 FairStatsPage(/fair-admin/statistics, 리뷰 통계와 통합)가
 * 이 컴포넌트를 그대로 공유한다 - 예전엔 두 페이지가 이 위젯들을 각자 베껴 구현해서 방문
 * 통계와 리뷰 통계 화면 내용이 그대로 중복돼 있었다.
 */
export function VisitStatsSection({ fairId }: VisitStatsSectionProps) {
  const [fairDates, setFairDates] = useState<FairDate[]>([]);
  const [boothStats, setBoothStats] = useState<BoothVisitStat[]>([]);
  const [boothVisitPattern, setBoothVisitPattern] = useState<LabelCount[]>([]);
  const [visitStats, setVisitStats] = useState<VisitStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedDate, setSelectedDate] = useState<string>("");
  const [hourlyTrend, setHourlyTrend] = useState<HourlyEntryTrend[]>([]);
  const [trendLoading, setTrendLoading] = useState(false);
  const [trendError, setTrendError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    Promise.all([getFairDates(fairId), getBoothVisitStats(fairId), getBoothVisitPattern(fairId), getVisitStats(fairId)])
      .then(([dates, booth, pattern, visit]) => {
        if (ignore) return;
        setFairDates(dates);
        setBoothStats(booth);
        setBoothVisitPattern(pattern);
        setVisitStats(visit);
        setSelectedDate((current) => (current && dates.some((d) => d.operationDate === current) ? current : (dates[0]?.operationDate ?? "")));
      })
      .catch((error) => {
        if (!ignore) {
          setFairDates([]);
          setBoothStats([]);
          setBoothVisitPattern([]);
          setVisitStats(null);
          setLoadError(error instanceof ApiError ? error.message : "방문 통계를 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId]);

  // 시간대별 입장 추이는 운영일 단위 통계라 선택된 날짜가 바뀔 때마다 다시 조회한다.
  useEffect(() => {
    if (selectedDate === "") { setHourlyTrend([]); return; }
    let ignore = false;

    setTrendLoading(true);
    setTrendError(null);
    getHourlyEntryTrend(fairId, selectedDate)
      .then((data) => { if (!ignore) setHourlyTrend(data); })
      .catch((error) => {
        if (!ignore) {
          setHourlyTrend([]);
          setTrendError(error instanceof ApiError ? error.message : "시간대별 입장 추이를 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setTrendLoading(false); });

    return () => { ignore = true; };
  }, [fairId, selectedDate]);

  if (loading) {
    return <div className="surface grid min-h-40 place-items-center text-sm text-muted">방문 통계를 불러오는 중이에요...</div>;
  }

  if (loadError) {
    return <p className="surface p-4 text-sm font-bold text-primary-strong">{loadError}</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      {visitStats && (
        <Card className="p-6">
          <div className="mb-5">
            <h2 className="text-lg font-extrabold text-ink">방문 요약</h2>
            <p className="mt-1 text-sm text-muted">행사 전 기간을 합산한 실입장 방문객 지표예요.</p>
          </div>
          <VisitStatsOverview stats={visitStats} />
        </Card>
      )}

      <div className="grid min-w-0 grid-cols-1 gap-6 xl:grid-cols-2">
        <Card className="min-w-0 p-6">
          <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 className="text-lg font-extrabold text-ink">시간대별 입장 추이</h2>
              <p className="mt-1 text-sm text-muted">선택한 운영일에 실제로 입장(QR 체크인)한 시각 기준 시간대별 건수예요.</p>
            </div>
            {fairDates.length > 0 && (
              <div className="w-48">
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

        {visitStats && (
          <Card className="min-w-0 p-6">
            <div className="mb-5">
              <h2 className="text-lg font-extrabold text-ink">반려동물 품종 분포</h2>
              <p className="mt-1 text-sm text-muted">방문 건수가 많은 순으로 정렬돼요.</p>
            </div>
            <PetBreedBreakdown data={visitStats.petBreedBreakdown} />
          </Card>
        )}
      </div>

      <div className="grid min-w-0 grid-cols-1 gap-6 xl:grid-cols-2">
        <Card className="min-w-0 p-6">
          <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 className="text-lg font-extrabold text-ink">부스별 방문 통계</h2>
              <p className="mt-1 text-sm text-muted">부스 QR을 스캔한 고유 방문객 수 기준으로, 행사 전 기간을 합산한 상위 {BOOTH_RANKING_PREVIEW_COUNT}개예요.</p>
            </div>
            {boothStats.length > 0 && (
              <Link to={`/fair-admin/statistics/booths/${fairId}`} className="shrink-0 text-sm font-bold text-primary-strong hover:underline">
                전체 {boothStats.length}개 상세보기
              </Link>
            )}
          </div>

          {boothStats.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">아직 부스 방문 기록이 없어요.</p>
          ) : (
            <BoothVisitRanking data={boothStats.slice(0, BOOTH_RANKING_PREVIEW_COUNT)} />
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

      {visitStats && (
        <Card className="p-6">
          <div className="mb-5">
            <h2 className="text-lg font-extrabold text-ink">방문객 분포</h2>
            <p className="mt-1 text-sm text-muted">실입장 방문객 기준 채널·성별·연령대·반려동물 종 분포예요.</p>
          </div>

          <div className="grid grid-cols-1 gap-x-8 gap-y-6 md:grid-cols-2">
            <div>
              <h3 className="mb-3 text-sm font-bold text-ink">채널</h3>
              <DonutChart data={visitStats.channelBreakdown} />
            </div>
            <div>
              <h3 className="mb-3 text-sm font-bold text-ink">성별</h3>
              <DonutChart data={visitStats.genderBreakdown} />
            </div>
            <div>
              <h3 className="mb-3 text-sm font-bold text-ink">연령대</h3>
              <DonutChart data={visitStats.ageGroupBreakdown} />
            </div>
            <div>
              <h3 className="mb-3 text-sm font-bold text-ink">반려동물 종</h3>
              <DonutChart data={visitStats.petSpeciesBreakdown} />
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
