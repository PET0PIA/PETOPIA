/** YYYY-MM-DD, 로컬 타임존 기준. toISOString()은 UTC라 자정 근처에 하루 밀릴 수 있어 직접 조립한다. */
function toDateString(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function today(): string {
  return toDateString(new Date());
}

/**
 * 생년월일 <input type="date">의 max 속성용. 백엔드가 `@Past`(오늘 미포함, 그 이전만 허용)라
 * 오늘 날짜를 골라도 제출 시 400이 나므로, 선택 UI 단계에서부터 오늘은 막아둔다.
 */
export function maxBirthDate(): string {
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  return toDateString(yesterday);
}
