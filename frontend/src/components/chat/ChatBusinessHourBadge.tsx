import { formatBusinessHourTime } from "../../api/chat";

interface ChatBusinessHourBadgeProps {
  within: boolean;
  /** 오늘 끝나는 시각. 운영시간 밖이면 null이라 시각이 표시되지 않는다. */
  closesAt: string | null;
}

/**
 * 운영시간 표시.
 *
 * 색만으로 정보를 전달하지 않는다 - 점은 aria-hidden으로 두고 실제 내용은 텍스트가 나른다.
 * 초록/회색 구분을 못 보는 사용자에게 점만 남으면 아무 정보도 아니다.
 *
 * 운영시간 안에는 끝나는 시각을 함께 붙인다. "상담 가능"만으로는 "지금 물어봐도 되나"까지만
 * 알 수 있고 "얼마나 여유가 있나"는 알 수 없다 - 17:55에 문의를 시작하는 사람과 10시에
 * 시작하는 사람은 기대가 달라야 한다.
 *
 * 밖에서는 다음 여는 시각을 쓰지 않는다. 기다릴 이유가 없기 때문이다 - 그 시간대에는
 * 자동 응대가 먼저 답한다. 그 사실을 알리는 편이 "내일 9시에 오세요"보다 낫다.
 */
export function ChatBusinessHourBadge({ within, closesAt }: ChatBusinessHourBadgeProps) {
  const label = within
    ? closesAt
      ? `상담 가능 · ${formatBusinessHourTime(closesAt)}까지`
      : "상담 가능"
    : "운영시간 아님 · 자동 응대";

  return (
    <span className="flex items-center gap-1.5 text-xs text-muted">
      <span
        aria-hidden
        className={`h-2 w-2 shrink-0 rounded-pill ${within ? "bg-leaf" : "bg-muted"}`}
      />
      {label}
    </span>
  );
}
