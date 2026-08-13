import { AlertCircle, ArrowLeft, Download } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getFairDates, type FairDate } from "../../api/fair";
import { downloadVisitStatsExcel, getBoothVisitStats, getHourlyEntryTrend, getVisitStats, type BoothVisitStat, type HourlyEntryTrend, type VisitStats } from "../../api/statistics";
import { BoothVisitRanking } from "../../components/fair-admin/BoothVisitRanking";
import { DonutChart } from "../../components/fair-admin/DonutChart";
import { HourlyEntryTrendChart } from "../../components/fair-admin/HourlyEntryTrendChart";
import { PetBreedBreakdown } from "../../components/fair-admin/PetBreedBreakdown";
import { VisitStatsOverview } from "../../components/fair-admin/VisitStatsOverview";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Select } from "../../components/ui/Select";
import { useFairSelector } from "../../contexts/FairSelectorContext";

const BOOTH_RANKING_PREVIEW_COUNT = 3;

function resolvePathFairId(pathParam: string | undefined): number | null {
  const parsed = Number(pathParam);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export function VisitStatisticsPage() {
  const { fairId: fairIdParam } = useParams<{ fairId: string }>();
  const [searchParams] = useSearchParams();

  // SUPER_ADMIN이 /admin/dashboard/fairs/:fairId 경로로 들어온 경우 경로 파라미터를 우선 사용한다.
  const fromAdminDashboard = fairIdParam !== undefined;

  const { fairId: selectorFairId, selectableFairs } = useFairSelector();

  // 경로 파라미터(SUPER_ADMIN → 대시보드 클릭)나 훅(EVENT_ADMIN 자동 선택)에서 fairId를 결정한다.
  const [overrideFairId, setOverrideFairId] = useState<number | null>(
    () => resolvePathFairId(fairIdParam),
  );

  // fairIdParam이 바뀌면(같은 컴포넌트가 유지된 채 다른 행사로 라우팅) override를 동기화한다.
  useEffect(() => {
    const newId = resolvePathFairId(fairIdParam);
    setOverrideFairId(newId);
    setVisitStats(null);
    setLoadError(null);
  }, [fairIdParam]);

  // 최종 fairId: 대시보드에서 들어온 경우 경로 파라미터만 신뢰한다(무효면 다른 행사로 새지 않도록
  // selectorFairId로 대체하지 않는다). 그 외에는 훅(EVENT_ADMIN 자동 선택 / SUPER_ADMIN 수동 선택)을 쓴다.
  const fairId = fromAdminDashboard ? overrideFairId : selectorFairId;

  const [fairDates, setFairDates] = useState<FairDate[]>([]);
  const [boothStats, setBoothStats] = useState<BoothVisitStat[]>([]);
  const [visitStats, setVisitStats] = useState<VisitStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedDate, setSelectedDate] = useState<string>("");
  const [hourlyTrend, setHourlyTrend] = useState<HourlyEntryTrend[]>([]);
  const [trendLoading, setTrendLoading] = useState(false);
  const [trendError, setTrendError] = useState<string | null>(null);

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  // 운영일 목록(날짜 선택용)과 부스 방문 통계(운영일과 무관, 행사 전체 집계)는 fairId만 있으면 조회 가능하다.
  useEffect(() => {
    if (fairId === null) return;
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    Promise.all([getFairDates(fairId), getBoothVisitStats(fairId), getVisitStats(fairId)])
      .then(([dates, booth, visit]) => {
        if (ignore) return;
        setFairDates(dates);
        setBoothStats(booth);
        setVisitStats(visit);
        setSelectedDate((current) => (current && dates.some((d) => d.operationDate === current) ? current : (dates[0]?.operationDate ?? "")));
      })
      .catch((error) => {
        if (!ignore) {
          setFairDates([]);
          setBoothStats([]);
          setVisitStats(null);
          setLoadError(error instanceof ApiError ? error.message : "방문 통계를 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId]);

  // 시간대별 입장 추이는 운영일 단위 통계라 선택된 날짜가 바뀔 때마다 다시 조회한다.
  useEffect(() => {
    if (fairId === null || selectedDate === "") { setHourlyTrend([]); return; }
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
        <Link to="/admin" className="mb-4 inline-flex items-center gap-1 text-sm font-bold text-muted hover:text-primary-strong">
          <ArrowLeft size={16} />
          전체 운영 대시보드로
        </Link>
      )}
      <PageHeader
        eyebrow={fromAdminDashboard ? "전체 운영" : "박람회 관리자"}
        title="방문 통계"
        description="운영일별 시간대 입장 추이와 부스별 고유 방문객 수를 확인해요."
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

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {fromAdminDashboard && fairId === null && (
        <EmptyState title="잘못된 행사 경로예요." description="전체 운영 대시보드에서 다시 시도해 주세요." />
      )}

      {!fromAdminDashboard && fairId === null && selectableFairs.length > 0 && (
        <EmptyState title="행사를 선택해 주세요." description="상단 바에서 행사를 고르면 방문 통계가 표시돼요." />
      )}

      {fairId !== null && loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">방문 통계를 불러오는 중이에요...</div>
      )}

      {fairId !== null && !loading && !loadError && (
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

          <Card className="p-6">
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

          <Card className="p-6">
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

          {visitStats && (
            <Card className="p-6">
              <div className="mb-5">
                <h2 className="text-lg font-extrabold text-ink">반려동물 품종 분포</h2>
                <p className="mt-1 text-sm text-muted">방문 건수가 많은 순으로 정렬돼요.</p>
              </div>
              <PetBreedBreakdown data={visitStats.petBreedBreakdown} />
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
