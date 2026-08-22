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

/**
 * 주최측 행사 취소로 자동 취소된 예약에 붙이는 문구.
 * 목록은 짧은 라벨을, 상세는 무슨 일이 있었는지 한 줄로 설명한다.
 * 내가 직접 취소한 건과 구분해서 "왜 취소됐지?" 하는 문의를 줄이는 게 목적이다.
 */
export const fairCancellationLabel = "행사 취소";
export const fairCancellationNotice =
  "주최측 사정으로 행사가 취소되어 예약이 자동으로 취소됐어요. 결제하신 예약금은 환불 처리됐어요.";

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

/**
 * 결제 제한시각까지 남은 시간을 "m:ss"로 만든다. 이미 지났거나 값이 없으면 null.
 *
 * 예매·목록·상세가 같은 문구를 쓰도록 여기 모았다 - 예전에는 예매 화면에만 있어서,
 * 목록·상세에서는 "결제 대기"라는 배지만 보이고 언제까지 결제해야 하는지 알 수 없었다.
 *
 * 백엔드는 LocalDateTime을 오프셋 없이("2026-08-09T12:34:56") 내려주는데, 서버·DB·컨테이너가
 * 전부 Asia/Seoul로 고정돼 있어(Dockerfile / docker-compose의 TZ) 브라우저 로컬 시각으로
 * 파싱해도 어긋나지 않는다.
 */
export function formatRemaining(expiresAt: string | null, now: number): string | null {
  if (!expiresAt) return null;
  const diff = new Date(expiresAt).getTime() - now;
  if (Number.isNaN(diff) || diff <= 0) return null;
  const totalSeconds = Math.floor(diff / 1000);
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, "0")}`;
}
