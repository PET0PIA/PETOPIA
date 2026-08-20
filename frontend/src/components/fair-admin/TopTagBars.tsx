import type { TagCountItem } from "../../api/fairReviewStats";

interface TopTagBarsProps {
  items: TagCountItem[];
  /**
   * 막대 색상. positive는 leaf(초록), negative는 coral(붉은 계열) 사용.
   * theme.css 토큰엔 빨간 계열이 없어서, DonutChart.tsx의 SLICE_COLORS 1번 슬롯과 동일한
   * "브랜드 코럴" 계열 hex(#F28B82)를 그대로 재사용해 팔레트 일관성을 맞췄다.
   */
  color: "leaf" | "coral";
}

const BAR_COLOR: Record<TopTagBarsProps["color"], string> = {
  leaf: "var(--color-leaf)",
  coral: "#F28B82",
};

/** 카테고리 구분 없이 전체를 통틀어 뽑은 TOP N 태그를 퍼센트 막대로 보여준다. */
export function TopTagBars({ items, color }: TopTagBarsProps) {
  if (items.length === 0) {
    return <p className="py-4 text-center text-sm text-muted">아직 데이터가 없어요.</p>;
  }

  const maxRatio = Math.max(...items.map((item) => item.ratio), 0.0001);

  return (
    <ol className="flex min-w-0 flex-col gap-3">
      {items.map((item) => {
        const percent = Math.round(item.ratio * 1000) / 10;
        const barWidthPercent = (item.ratio / maxRatio) * 100;
        return (
          <li key={item.tagId} className="min-w-0 text-sm">
            {/* 라벨을 막대와 한 줄에 나란히 두면 좁은 칸(2단 배치)에서 라벨이 잘리기 쉬워서,
                라벨+퍼센트를 한 줄, 막대를 그 아래 줄로 분리한다 - 라벨이 잘릴 걱정이 없다. */}
            <div className="flex items-center justify-between gap-3">
              <span className="min-w-0 truncate font-bold text-ink" title={item.label}>{item.label}</span>
              <span className="shrink-0 tabular-nums text-muted">{percent}%</span>
            </div>
            <div className="mt-1.5 h-2.5 w-full overflow-hidden rounded-pill bg-page">
              <div className="h-full rounded-pill" style={{ width: `${barWidthPercent}%`, backgroundColor: BAR_COLOR[color] }} />
            </div>
          </li>
        );
      })}
    </ol>
  );
}
