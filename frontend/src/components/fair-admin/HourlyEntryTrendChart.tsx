import { useState } from "react";
import type { HourlyEntryTrend } from "../../api/statistics";

interface HourlyEntryTrendChartProps {
  data: HourlyEntryTrend[];
}

const CHART_HEIGHT = 260;

/** 0~23시 전체를 채운 배열로 만든다. 입장 기록이 없는 시간대는 0건으로 표시한다. */
function fillAllHours(data: HourlyEntryTrend[]): HourlyEntryTrend[] {
  const byHour = new Map(data.map((row) => [row.entryHour, row.entryCount]));
  return Array.from({ length: 24 }, (_, hour) => ({ entryHour: hour, entryCount: byHour.get(hour) ?? 0 }));
}

export function HourlyEntryTrendChart({ data }: HourlyEntryTrendChartProps) {
  const [hoveredHour, setHoveredHour] = useState<number | null>(null);
  const hours = fillAllHours(data);
  const dataMax = Math.max(1, ...hours.map((row) => row.entryCount));
  // 축 상단이 실제 최댓값과 딱 맞으면 제일 높은 막대가 축 끝에 붙어 버리므로,
  // 축 스케일은 실제 최댓값보다 여유를 두고 잡는다(막대는 이 axisMax 기준으로 높이를 계산).
  // 항상 짝수로 맞춰야 "중간 눈금 = axisMax/2"가 딱 떨어져서, 50% 위치의 그리드선과 그 옆 숫자가 어긋나지 않는다.
  const axisMaxWithHeadroom = dataMax + Math.max(1, Math.ceil(dataMax * 0.2));
  const axisMax = axisMaxWithHeadroom % 2 === 0 ? axisMaxWithHeadroom : axisMaxWithHeadroom + 1;
  const peakHour = hours.reduce((best, row) => (row.entryCount > best.entryCount ? row : best), hours[0]);

  return (
    <div>
      {/* pt: 막대 위로 뜨는 피크 라벨·툴팁이 위쪽 여백에서 잘리지 않도록 미리 공간을 확보한다 */}
      <div className="flex pt-9" style={{ height: CHART_HEIGHT + 36 }}>
        <div className="mr-2 flex flex-col justify-between text-right text-[11px] tabular-nums text-muted">
          <span>{axisMax}건</span>
          <span>{axisMax / 2}건</span>
          <span>0건</span>
        </div>
        <div className="relative flex flex-1 items-end gap-1 border-l border-line">
          {[0, 0.5, 1].map((fraction) => (
            <div key={fraction} className="pointer-events-none absolute inset-x-0 border-t border-line/70" style={{ bottom: `${fraction * 100}%` }} />
          ))}
          {hours.map((row) => {
            const heightPercent = Math.max((row.entryCount / axisMax) * 100, row.entryCount > 0 ? 2 : 0);
            const isPeak = row.entryCount > 0 && row.entryCount === dataMax && row.entryHour === peakHour.entryHour;
            // 좌우 끝 시간대는 툴팁을 가운데 정렬하면 카드 밖으로 넘어가므로 가장자리 쪽은 안쪽으로 붙인다.
            const align = row.entryHour <= 1 ? "left-0 translate-x-0" : row.entryHour >= 22 ? "right-0 left-auto translate-x-0" : "left-1/2 -translate-x-1/2";
            return (
              <div
                key={row.entryHour}
                className="group relative z-10 flex h-full flex-1 items-end justify-center"
                onMouseEnter={() => setHoveredHour(row.entryHour)}
                onMouseLeave={() => setHoveredHour((current) => (current === row.entryHour ? null : current))}
              >
                {/* 막대 자신을 기준(relative)으로 삼아야 라벨·툴팁이 실제 막대 높이 바로 위에 붙는다.
                    차트 전체 높이를 기준으로 삼으면 짧은 막대에서 툴팁이 차트 맨 위로 튀어 위쪽 UI와 겹친다. */}
                {/* 0건 시간대는 높이가 0%가 되어 아무것도 안 그려지면 "막대가 아예 안 보인다"는
                    인상을 주므로, 3px짜리 회색 기준선을 대신 그려 24시간 전체 축을 눈에 보이게 한다. */}
                <div className="relative w-full max-w-[18px]" style={row.entryCount > 0 ? { height: `${heightPercent}%` } : { height: 3 }}>
                  <div className={`h-full w-full rounded-t-[4px] transition-opacity group-hover:opacity-80 ${row.entryCount > 0 ? "bg-primary" : "bg-line"}`} />
                  {isPeak && (
                    <span className="absolute -top-5 left-1/2 -translate-x-1/2 whitespace-nowrap text-[11px] font-bold text-primary-strong">{row.entryCount}</span>
                  )}
                  {hoveredHour === row.entryHour && (
                    <div className={`pointer-events-none absolute bottom-full z-20 mb-2 whitespace-nowrap rounded-button bg-ink px-2.5 py-1.5 text-xs font-bold text-white shadow-lg ${align}`}>
                      {row.entryHour}시 · {row.entryCount}건
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
      <div className="ml-8 mt-2 flex gap-1">
        {hours.map((row) => (
          <span key={row.entryHour} className="flex-1 text-center text-[10px] text-muted">
            {row.entryHour % 3 === 0 ? row.entryHour : ""}
          </span>
        ))}
      </div>
    </div>
  );
}
