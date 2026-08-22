import type { FairStatus } from "../../api/fair";
import { todayInSeoul } from "../../utils/date";

export const fairCategoryLabels: Record<string, string> = { DOG: "강아지", CAT: "고양이", ETC: "기타" };

/**
 * 행사가 지금 운영 중인지. 서버 status를 먼저 믿고, status가 비어 있을 때만 운영기간으로 판정한다.
 *
 * FairDetailPage의 ended 판정과 짝을 이룬다 - 한쪽에만 날짜 폴백을 두면 status가 비었을 때
 * "종료도 아니고 진행 중도 아닌" 상태가 되어, 운영 중인 행사의 현장예매 입구(목록·상세 CTA가
 * 유일한 입구다)가 조용히 막힌다.
 *
 * status가 비는 경우: 공개 목록·상세를 만드는 쿼리는 모두 fairs.status(NOT NULL)를 선택하므로
 * 지금은 비지 않는다. 다만 Fair 객체가 여러 쿼리에 공유돼서, status를 안 고르는 쿼리가 끼어들면
 * null이 된다(FairService가 매핑에 null 가드를 둔 이유이기도 하다). 그 경우의 안전망이다.
 *
 * 오늘은 브라우저 시간대가 아니라 Asia/Seoul 기준으로 구한다(해외 기기에서 하루 밀림 방지).
 */
export function isFairInProgress(
  status: FairStatus | null,
  operationStartDate: string | null,
  operationEndDate: string | null,
): boolean {
  if (status) return status === "IN_PROGRESS";
  if (!operationStartDate || !operationEndDate) return false;
  const today = todayInSeoul();
  return operationStartDate <= today && today <= operationEndDate;
}

const WEEKDAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];

// "2026-08-14" → { month, day, dow }. 형식이 어긋나면 null.
// 연·월·일을 직접 넣어 로컬 Date를 만든다(문자열 파싱의 UTC 해석에 따른 요일 밀림 방지).
function parseYmd(iso: string): { month: number; day: number; dow: number } | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return null;
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(year, month - 1, day);
  // 달력에 실제로 존재하는 날짜인지 확인(예: 2026-02-30은 3월로 밀리므로 걸러냄).
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null;
  return { month, day, dow: date.getDay() };
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
