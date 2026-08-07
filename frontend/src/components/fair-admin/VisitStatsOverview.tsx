import type { VisitStats } from "../../api/statistics";

interface VisitStatsOverviewProps {
  stats: VisitStats;
}

/** 방문 통계 상단 요약 지표(총 방문자·확정 예약·방문율·평균 반려동물 나이). */
export function VisitStatsOverview({ stats }: VisitStatsOverviewProps) {
  const tiles = [
    { label: "총 방문자", value: `${stats.totalVisitors}명` },
    { label: "확정 예약", value: `${stats.totalConfirmedReservations}건` },
    { label: "방문율", value: `${stats.visitRate}%` },
    { label: "평균 반려동물 나이", value: stats.avgPetAge === null ? "-" : `${stats.avgPetAge}세` },
    { label: "1인 평균 부스 방문", value: stats.avgBoothsPerVisitor == null ? "-" : `${stats.avgBoothsPerVisitor}개` },
  ];

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {tiles.map((tile) => (
        <div key={tile.label} className="rounded-card bg-page px-4 py-3">
          <p className="text-xs font-bold text-muted">{tile.label}</p>
          <p className="mt-1 text-xl font-extrabold tabular-nums text-ink">{tile.value}</p>
        </div>
      ))}
    </div>
  );
}
