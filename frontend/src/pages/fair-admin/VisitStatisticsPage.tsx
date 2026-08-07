import { AlertCircle, Search } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getFairDates, type FairDate } from "../../api/fair";
import { getBoothVisitStats, getHourlyEntryTrend, getVisitStats, type BoothVisitStat, type HourlyEntryTrend, type VisitStats } from "../../api/statistics";
import { BoothVisitRanking } from "../../components/fair-admin/BoothVisitRanking";
import { DonutChart } from "../../components/fair-admin/DonutChart";
import { HourlyEntryTrendChart } from "../../components/fair-admin/HourlyEntryTrendChart";
import { PetBreedBreakdown } from "../../components/fair-admin/PetBreedBreakdown";
import { VisitStatsOverview } from "../../components/fair-admin/VisitStatsOverview";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";

const BOOTH_RANKING_PREVIEW_COUNT = 3;

export function VisitStatisticsPage() {
  // TODO 관리자 세션에 현재 담당 행사(fairId)가 연결되면 이 입력을 없애고 세션 값을 바로 쓴다.
  const [fairIdInput, setFairIdInput] = useState("");
  const [fairId, setFairId] = useState<number | null>(null);

  const [fairDates, setFairDates] = useState<FairDate[]>([]);
  const [boothStats, setBoothStats] = useState<BoothVisitStat[]>([]);
  const [visitStats, setVisitStats] = useState<VisitStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  const [selectedDate, setSelectedDate] = useState<string>("");
  const [hourlyTrend, setHourlyTrend] = useState<HourlyEntryTrend[]>([]);
  const [trendLoading, setTrendLoading] = useState(false);
  const [trendError, setTrendError] = useState<string | null>(null);

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
  }, [fairId, reloadTick]);

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

  function handleLoadFair(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(fairIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setLoadError("행사 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    if (parsed === fairId) {
      setReloadTick((tick) => tick + 1);
    } else {
      setFairId(parsed);
    }
  }

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader
        eyebrow="박람회 관리자"
        title="방문 통계"
        description="운영일별 시간대 입장 추이와 부스별 고유 방문객 수를 확인해요."
      />

      <form onSubmit={handleLoadFair} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label htmlFor="statistics-fair-id" className="mb-1.5 block text-sm font-bold text-ink">관리할 행사 ID</label>
          <Input id="statistics-fair-id" type="number" min={1} value={fairIdInput} onChange={(event) => setFairIdInput(event.target.value)} placeholder="예: 1" />
        </div>
        <Button type="submit" variant="outline"><Search size={16} />불러오기</Button>
      </form>

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="행사 ID를 먼저 입력해 주세요." description="담당 행사의 ID를 입력하고 불러오기를 누르면 방문 통계가 표시돼요." />
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
              {boothStats.length > BOOTH_RANKING_PREVIEW_COUNT && (
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
