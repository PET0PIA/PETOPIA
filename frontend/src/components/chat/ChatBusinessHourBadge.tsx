interface ChatBusinessHourBadgeProps {
  within: boolean;
}

/**
 * 운영시간 표시.
 *
 * 색만으로 정보를 전달하지 않는다 - 점은 aria-hidden으로 두고 실제 내용은 텍스트가 나른다.
 * 초록/회색 구분을 못 보는 사용자에게 점만 남으면 아무 정보도 아니다.
 *
 * 문장을 "상담 운영시간이에요"에서 줄인 이유는 이 값이 헤더의 보조 정보이기 때문이다.
 * 사용자가 알아야 하는 건 "지금 사람이 답하는가"이고, 그 답은 두 단어로 충분하다.
 */
export function ChatBusinessHourBadge({ within }: ChatBusinessHourBadgeProps) {
  return (
    <span className="flex items-center gap-1.5 text-xs text-muted">
      <span
        aria-hidden
        className={`h-2 w-2 shrink-0 rounded-pill ${within ? "bg-leaf" : "bg-muted"}`}
      />
      {within ? "상담 가능" : "운영시간 아님 · 자동 응대"}
    </span>
  );
}
