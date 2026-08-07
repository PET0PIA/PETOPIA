import { useState } from "react";
import type { LabelCount } from "../../api/statistics";

interface DonutChartProps {
  data: LabelCount[];
  /** 값 뒤에 붙는 단위. 기본값 "명" */
  unit?: string;
}

const SIZE = 168;
const STROKE_WIDTH = 22;
const RADIUS = (SIZE - STROKE_WIDTH) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const GAP_PX = 3;
const MAX_SLICES = 6;

// 카테고리 정체성용 6색. 브랜드 코럴(primary)을 1번 슬롯으로 두고, 인접 쌍이 색맹 시뮬레이션에서도
// 구분되도록 고정 순서로 배치했다(scripts/validate_palette.js로 검증). 슬롯 순서를 바꾸면 안 된다.
const SLICE_COLORS = ["#FF6B6B", "#3D7DD1", "#3F9D6B", "#C98500", "#8A6FD1", "#E8829E"];
// "기타"로 접힌 항목은 정체성 색이 아니라 중립색으로 표시한다.
const OTHER_COLOR = "#918B83";

/** 6개를 넘으면 상위 5개만 남기고 나머지는 "기타"로 합친다. 도넛은 한눈에 보는 용도라 슬라이스가 많아지면 오히려 못 읽는다. */
function foldToOther(data: LabelCount[]): LabelCount[] {
  if (data.length <= MAX_SLICES) return data;
  const head = data.slice(0, MAX_SLICES - 1);
  const restCount = data.slice(MAX_SLICES - 1).reduce((sum, row) => sum + row.count, 0);
  return [...head, { label: "기타", count: restCount }];
}

/** 채널·성별·연령대·반려동물 종처럼 "라벨 + 건수" 분포를 도넛으로 보여준다. 범례·툴팁이 항상 함께 붙는다. */
export function DonutChart({ data, unit = "명" }: DonutChartProps) {
  const [hovered, setHovered] = useState<number | null>(null);

  if (data.length === 0) {
    return <p className="py-4 text-center text-sm text-muted">아직 데이터가 없어요.</p>;
  }

  const slices = foldToOther(data);
  const total = slices.reduce((sum, row) => sum + row.count, 0);

  const { arcs } = slices.reduce<{ cumulative: number; arcs: Array<LabelCount & { color: string; percent: number; dasharray: string; offset: number }> }>(
    (acc, row, index) => {
      const fraction = total > 0 ? row.count / total : 0;
      const rawLength = fraction * CIRCUMFERENCE;
      const length = Math.max(rawLength - GAP_PX, 0);
      const percent = total > 0 ? Math.round((row.count / total) * 1000) / 10 : 0;
      const color = row.label === "기타" ? OTHER_COLOR : SLICE_COLORS[index % SLICE_COLORS.length];
      acc.arcs.push({ ...row, color, percent, dasharray: `${length} ${CIRCUMFERENCE - length}`, offset: -acc.cumulative });
      acc.cumulative += rawLength;
      return acc;
    },
    { cumulative: 0, arcs: [] },
  );

  return (
    <div className="flex flex-wrap items-center gap-6">
      <div className="relative shrink-0" style={{ width: SIZE, height: SIZE }}>
        <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} className="-rotate-90">
          <circle cx={SIZE / 2} cy={SIZE / 2} r={RADIUS} fill="none" stroke="var(--color-page)" strokeWidth={STROKE_WIDTH} />
          {arcs.map((arc, index) => (
            <circle
              key={arc.label}
              cx={SIZE / 2}
              cy={SIZE / 2}
              r={RADIUS}
              fill="none"
              stroke={arc.color}
              strokeWidth={hovered === index ? STROKE_WIDTH + 4 : STROKE_WIDTH}
              strokeDasharray={arc.dasharray}
              strokeDashoffset={arc.offset}
              opacity={hovered === null || hovered === index ? 1 : 0.4}
              className="cursor-pointer transition-all"
              onMouseEnter={() => setHovered(index)}
              onMouseLeave={() => setHovered((current) => (current === index ? null : current))}
            >
              <title>{`${arc.label} · ${arc.count}${unit} (${arc.percent}%)`}</title>
            </circle>
          ))}
        </svg>
        <div className="pointer-events-none absolute inset-0 grid place-items-center">
          <div className="text-center">
            <p className="text-xl font-extrabold tabular-nums text-ink">{total}{unit}</p>
            <p className="text-[11px] text-muted">전체</p>
          </div>
        </div>
      </div>

      <ul className="flex min-w-40 flex-1 flex-col gap-1.5">
        {arcs.map((arc, index) => (
          <li
            key={arc.label}
            className={`flex items-center gap-2 rounded-button px-2 py-1 text-sm ${hovered === index ? "bg-page" : ""}`}
            onMouseEnter={() => setHovered(index)}
            onMouseLeave={() => setHovered((current) => (current === index ? null : current))}
          >
            <span className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: arc.color }} />
            <span className="flex-1 truncate font-bold text-ink" title={arc.label}>{arc.label}</span>
            <span className="shrink-0 tabular-nums text-muted">{arc.count}{unit} · {arc.percent}%</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
