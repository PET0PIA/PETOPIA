export const fairCategoryLabels: Record<string, string> = { DOG: "강아지", CAT: "고양이", ETC: "기타" };

const WEEKDAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];

// "2026-08-14" → { month, day, dow }. 형식이 어긋나면 null.
// 연·월·일을 직접 넣어 로컬 Date를 만든다(문자열 파싱의 UTC 해석에 따른 요일 밀림 방지).
function parseYmd(iso: string): { month: number; day: number; dow: number } | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return null;
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  return { month, day, dow: new Date(year, month - 1, day).getDay() };
}

// "8.14(금) - 16(일)" 형태. 요일 포함, 같은 달이면 종료일은 일만. 목록 카드용(k-pet 스타일).
// 파싱 실패하면 원본 문자열을 그대로 돌려준다.
export function formatFairPeriodDow(start: string | null, end: string | null) {
  if (!start) return "일정 미정";
  const s = parseYmd(start);
  if (!s) return start;
  const startText = `${s.month}.${s.day}(${WEEKDAY_LABELS[s.dow]})`;
  if (!end || end === start) return startText;
  const e = parseYmd(end);
  if (!e) return startText;
  const endText = s.month === e.month ? `${e.day}(${WEEKDAY_LABELS[e.dow]})` : `${e.month}.${e.day}(${WEEKDAY_LABELS[e.dow]})`;
  return `${startText} - ${endText}`;
}
