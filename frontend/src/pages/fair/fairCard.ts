export const fairCategoryLabels: Record<string, string> = { DOG: "강아지", CAT: "고양이", ETC: "기타" };

// operationEndDate가 없으면(일정 미정) 그대로, 있으면 "시작 ~ 종료"로 붙인다.
// (연도가 같으면 종료일은 월-일만 - TicketReservationPage와 동일 규칙)
export function formatFairPeriod(start: string | null, end: string | null) {
  if (!start) return "일정 미정";
  if (!end || end === start) return start;
  const sameYear = start.slice(0, 4) === end.slice(0, 4);
  return `${start} ~ ${sameYear ? end.slice(5) : end}`;
}
