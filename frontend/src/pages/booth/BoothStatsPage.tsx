import { AlertCircle, ArrowLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getBooth, getBoothStats, type BoothResponse, type BoothStatsResponse } from "../../api/booth";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Card } from "../../components/ui/Card";
import { Table } from "../../components/ui/Table";
import type { LabelCount } from "../../api/booth";

function formatVisitDateDow(value: string) {
  const date = new Date(`${value}T00:00:00`);
  return date.toLocaleDateString("ko-KR", { month: "numeric", day: "numeric", weekday: "short" });
}

/** 라벨·건수 표. count DESC로 이미 정렬되어 내려온다(반려동물 종·알레르기 분포 공용). */
function LabelCountTable({ data, labelHeader }: { data: LabelCount[]; labelHeader: string }) {
  if (data.length === 0) {
    return <p className="py-4 text-center text-sm text-muted">아직 데이터가 없어요.</p>;
  }

  return (
    <Table>
      <thead>
        <tr className="border-b border-line text-xs font-bold text-muted">
          <th className="px-4 py-2.5">{labelHeader}</th>
          <th className="px-4 py-2.5 text-right">건수</th>
        </tr>
      </thead>
      <tbody>
        {data.map((row) => (
          <tr key={row.label} className="border-b border-line last:border-0">
            <td className="px-4 py-2.5 text-sm font-bold text-ink">{row.label}</td>
            <td className="px-4 py-2.5 text-right text-sm font-bold tabular-nums text-ink">{row.count}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

export function BoothStatsPage() {
  const params = useParams<{ boothId: string }>();
  const boothId = Number(params.boothId);
  const boothIdValid = Number.isInteger(boothId) && boothId > 0;

  const [booth, setBooth] = useState<BoothResponse | null>(null);
  const [stats, setStats] = useState<BoothStatsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!boothIdValid) { setLoading(false); return; }
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    Promise.all([getBooth(boothId), getBoothStats(boothId)])
      .then(([boothResult, statsResult]) => {
        if (ignore) return;
        setBooth(boothResult);
        setStats(statsResult);
      })
      .catch((error) => {
        if (ignore) return;
        setLoadError(error instanceof ApiError ? error.message : "부스 통계를 불러오지 못했어요.");
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [boothId, boothIdValid]);

  const tiles = stats && [
    { label: "고유 방문자", value: `${stats.uniqueVisitorCount}명` },
    { label: "총 스캔 횟수", value: `${stats.totalScanCount}회` },
    { label: "재방문율", value: `${stats.revisitRate}%` },
    { label: "리뷰 전환율", value: `${stats.reviewConversionRate}%` },
    { label: "찜 전환율", value: `${stats.favoriteConversionRate}%` },
    { label: "평균 반려동물 나이", value: stats.avgPetAge === null ? "-" : `${stats.avgPetAge}세` },
  ];

  const maxDailyVisitors = stats ? Math.max(1, ...stats.dailyVisits.map((row) => row.visitorCount)) : 1;

  return (
    <PageContainer className="py-10">
      <Link to="/vendor/booths" className="mb-4 inline-flex items-center gap-1 text-sm font-bold text-muted hover:text-ink">
        <ArrowLeft size={16} />내 부스 관리로
      </Link>

      <PageHeader
        eyebrow="부스 관리자"
        title={booth ? `${booth.name} 방문 통계` : "부스 방문 통계"}
        description="부스 QR 스캔 기록을 기준으로 방문자·재방문·리뷰/찜 전환율을 확인해요."
      />

      {!boothIdValid ? (
        <EmptyState title="잘못된 부스 ID예요." description="내 부스 관리에서 다시 이동해 주세요." />
      ) : loadError ? (
        <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      ) : loading ? (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>
      ) : stats && tiles ? (
        <div className="flex flex-col gap-6">
          <Card className="p-6">
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
              {tiles.map((tile) => (
                <div key={tile.label} className="rounded-card bg-page px-4 py-3">
                  <p className="text-xs font-bold text-muted">{tile.label}</p>
                  <p className="mt-1 text-xl font-extrabold tabular-nums text-ink">{tile.value}</p>
                </div>
              ))}
            </div>
          </Card>

          <Card className="p-6">
            <div className="mb-5">
              <h2 className="text-lg font-extrabold text-ink">일자별 방문자 추이</h2>
              <p className="mt-1 text-sm text-muted">최초 방문일 기준 하루당 고유 방문자 수예요.</p>
            </div>
            {stats.dailyVisits.length === 0 ? (
              <p className="text-sm text-muted">아직 방문 기록이 없어요.</p>
            ) : (
              <ul className="flex flex-col gap-3">
                {stats.dailyVisits.map((row) => {
                  const widthPercent = (row.visitorCount / maxDailyVisitors) * 100;
                  return (
                    <li key={row.visitDate} className="flex items-center gap-3">
                      <div className="w-20 shrink-0 text-sm font-bold text-ink">{formatVisitDateDow(row.visitDate)}</div>
                      <div className="relative h-6 flex-1 rounded-[4px] bg-page">
                        <div
                          className="h-full min-w-[6px] rounded-[4px] bg-leaf"
                          style={{ width: `${widthPercent}%` }}
                        />
                      </div>
                      <div className="w-16 shrink-0 text-right text-sm font-bold tabular-nums text-ink">{row.visitorCount}명</div>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>

          <div className="grid min-w-0 grid-cols-1 gap-6 xl:grid-cols-2">
            <Card className="min-w-0 p-6">
              <div className="mb-5">
                <h2 className="text-lg font-extrabold text-ink">반려동물 종 분포</h2>
                <p className="mt-1 text-sm text-muted">예약 시 등록한 반려동물 기준이라, 등록이 없으면 "미등록"으로 잡혀요.</p>
              </div>
              <LabelCountTable data={stats.petSpeciesBreakdown} labelHeader="종" />
            </Card>

            <Card className="min-w-0 p-6">
              <div className="mb-5">
                <h2 className="text-lg font-extrabold text-ink">반려동물 알레르기 분포</h2>
                <p className="mt-1 text-sm text-muted">알레르기가 있는 반려동물 기준, 건수가 많은 순으로 정렬돼요.</p>
              </div>
              <LabelCountTable data={stats.petAllergyBreakdown} labelHeader="알레르기" />
            </Card>
          </div>
        </div>
      ) : null}
    </PageContainer>
  );
}
