import type { CategoryTagRanking, TagCountItem } from "../../api/fairReviewStats";

// V39__unified_review_feedback.sql의 CK_FEEDBACK_TAGS_CATEGORY(scope=FAIR)와 맞춘다.
// FairReviewWizard.tsx의 fairCategoryLabels와 동일한 매핑을 이 화면에서도 독립적으로 둔다.
const fairCategoryLabels: Record<string, string> = {
  GUIDE_OPERATION: "안내/운영",
  SAFETY_HYGIENE: "안전/위생",
  WAIT_FLOW: "대기/동선",
  PET_CONVENIENCE: "반려동물 동반 편의성",
  FACILITY: "시설/편의",
  PRICE_VALUE: "가격/가치",
  CONTENT_PROGRAM: "부대행사/콘텐츠",
};

// 긍정 태그는 --color-leaf(테마에 이미 있는 초록 상태색)를 그대로 쓰고, 부정 태그는
// TopTagBars의 막대 색과 통일시킨 코랄(#F28B82, DonutChart 첫 슬라이스 색과 동일)을 쓴다 -
// 이 프로젝트 팔레트엔 별도 red/danger 토큰이 없어 임의 값(text-[...])으로 지정한다.
const TONE_LABEL_CLASS = {
  positive: "text-leaf",
  negative: "text-[#F28B82]",
} as const;

function TagList({ items, emptyText, tone }: { items: TagCountItem[]; emptyText: string; tone: "positive" | "negative" }) {
  if (items.length === 0) {
    return <p className="py-2 text-sm text-muted">{emptyText}</p>;
  }
  return (
    <ol className="flex min-w-0 flex-col gap-1.5">
      {items.map((item, index) => (
        <li key={item.tagId} className="flex min-w-0 items-start gap-2 text-sm">
          <span className={`w-4 shrink-0 text-right font-bold ${TONE_LABEL_CLASS[tone]}`}>{index + 1}</span>
          {/* 라벨을 한 줄로 잘라 보여주는 대신 줄바꿈을 허용해서(break-words) 태그 문구가
              길어도 잘리지 않고 다 보이게 한다 - 옆 숫자(건수·%)는 항상 같은 줄 우측에 고정. */}
          <span className={`min-w-0 flex-1 break-words font-bold ${TONE_LABEL_CLASS[tone]}`}>{item.label}</span>
          <span className="shrink-0 whitespace-nowrap tabular-nums text-muted">{item.count}건 · {Math.round(item.ratio * 1000) / 10}%</span>
        </li>
      ))}
    </ol>
  );
}

/** 행사 전체 태그(scope=FAIR)를 카테고리별로 묶어, 긍정/개선 필요 TOP5를 나란히 보여준다. */
export function TagRankingBoard({ rankings }: { rankings: CategoryTagRanking[] }) {
  const visible = rankings.filter((r) => r.positiveTop.length > 0 || r.negativeTop.length > 0);

  if (visible.length === 0) {
    return <p className="py-6 text-center text-sm text-muted">아직 선택된 태그가 없어요.</p>;
  }

  return (
    <div className="grid min-w-0 grid-cols-1 gap-6 xl:grid-cols-2">
      {visible.map((ranking) => (
        <div key={ranking.category} className="min-w-0 rounded-card border border-line p-4">
          <h3 className="mb-3 text-sm font-extrabold text-ink">{fairCategoryLabels[ranking.category] ?? ranking.category}</h3>
          <div className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2">
            <div className="min-w-0">
              <p className="mb-2 text-xs font-bold text-leaf">좋았어요 TOP 5</p>
              <TagList items={ranking.positiveTop} emptyText="선택된 태그가 없어요." tone="positive" />
            </div>
            <div className="min-w-0">
              <p className="mb-2 text-xs font-bold text-[#F28B82]">아쉬웠어요 TOP 5</p>
              <TagList items={ranking.negativeTop} emptyText="선택된 태그가 없어요." tone="negative" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
