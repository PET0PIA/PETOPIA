import type { BoothVisitStat } from "../../api/statistics";

interface BoothVisitRankingProps {
  data: BoothVisitStat[];
}

/** 부스별 고유 방문객 수 랭킹. booth-visit-stats는 서버에서 이미 uniqueVisitorCount DESC로 정렬해 내려온다. */
export function BoothVisitRanking({ data }: BoothVisitRankingProps) {
  const max = Math.max(1, ...data.map((row) => row.uniqueVisitorCount));

  return (
    <ul className="flex flex-col gap-3">
      {data.map((row, index) => {
        const widthPercent = (row.uniqueVisitorCount / max) * 100;
        return (
          <li key={row.boothId} className="group flex items-center gap-3 rounded-button px-2 py-1.5 hover:bg-page">
            <span className="w-5 shrink-0 text-right text-xs font-bold tabular-nums text-muted">{index + 1}</span>
            <div className="w-44 shrink-0 truncate text-sm font-bold text-ink" title={`${row.boothNumber} · ${row.boothName}`}>
              {row.boothNumber} <span className="font-normal text-muted">{row.boothName}</span>
            </div>
            <div className="relative h-6 flex-1 rounded-[4px] bg-page">
              <div
                className="h-full min-w-[6px] rounded-[4px] bg-leaf transition-opacity group-hover:opacity-80"
                style={{ width: `${widthPercent}%` }}
              />
            </div>
            <div className="w-32 shrink-0 whitespace-nowrap text-right text-xs tabular-nums text-muted">
              <span className="text-sm font-bold text-ink">{row.uniqueVisitorCount}명</span> · 스캔 {row.totalScanCount}회
            </div>
          </li>
        );
      })}
    </ul>
  );
}
