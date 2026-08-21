import type { BadgeTone } from "../../components/ui/Badge";
import type { ReservationStatus, ReservationType } from "../../api/reservation";

/**
 * 예약 화면(목록·상세·예매)이 공유하는 표시 규칙.
 * 상태 라벨·색·유형 라벨·시간/날짜 포맷을 한곳에 모아, 화면마다 값이 어긋나지 않게 한다.
 */

/** 예약 상태 한글 라벨. */
export const reservationStatusLabels: Record<ReservationStatus, string> = {
  PENDING_PAYMENT: "결제 대기",
  CONFIRMED: "예약 확정",
  CHECKED_IN: "입장 완료",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};

/**
 * 예약 상태 배지 색. 목록·상세 공통.
 * 결제 대기=sun(행동 필요), 확정=leaf(정상), 입장 완료=ink(완료), 취소·만료=neutral(무효/종료).
 */
export const reservationStatusTones: Record<ReservationStatus, BadgeTone> = {
  PENDING_PAYMENT: "sun",
  CONFIRMED: "leaf",
  CHECKED_IN: "ink",
  CANCELED: "neutral",
  EXPIRED: "neutral",
};

/** 카드 전체를 흐릿하게(지난 예약 느낌) 처리할 상태. */
export const inactiveReservationStatuses: ReservationStatus[] = ["CHECKED_IN", "CANCELED", "EXPIRED"];

/** 예약 유형 한글 라벨. */
export const reservationTypeLabels: Record<ReservationType, string> = {
  ADVANCE: "사전예약",
  ONSITE_DIRECT: "현장예매",
};

/** "10:00:00"(LocalTime) → "10:00". */
export function formatEntryTime(time: string): string {
  return time.slice(0, 5);
}

const WEEKDAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];

/**
 * 방문일 "2026-09-05" → "2026.9.5(금)". 요일까지 보여 행사 화면과 톤을 맞춘다.
 * 연·월·일을 직접 넣어 로컬 Date를 만든다(문자열 파싱의 UTC 해석에 따른 요일 밀림 방지).
 * 형식이 어긋나거나 달력에 없는 날짜면 원본 문자열을 그대로 돌려준다.
 */
export function formatVisitDateDow(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return iso;
  return `${year}.${month}.${day}(${WEEKDAY_LABELS[date.getDay()]})`;
}
