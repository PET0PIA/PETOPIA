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

/**
 * 오늘 날짜(YYYY-MM-DD)를 Asia/Seoul 기준으로 만든다.
 * 서비스·서버·DB가 모두 KST 전제이므로, 백엔드가 준 날짜 문자열과 문자열 비교로 오늘을
 * 판정할 때는 이 값을 쓴다. 브라우저 로컬 시간대를 쓰면 해외 기기에서 자정 근처에 하루
 * 밀려, 운영 당일/종료 판정이 어긋난다.
 * en-CA 로케일이 YYYY-MM-DD 형태를 그대로 내준다.
 */
export function todayInSeoul(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}
