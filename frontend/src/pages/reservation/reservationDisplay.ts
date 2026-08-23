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

/** 백엔드 LocalDateTime의 기준 시간대. 서버·DB·컨테이너가 전부 이 값으로 고정돼 있다. */
const SERVER_UTC_OFFSET = "+09:00";
/** 끝에 Z나 ±hh:mm이 붙어 있으면 이미 시간대가 명시된 문자열이다. */
const HAS_TIMEZONE = /(?:Z|[+-]\d{2}:?\d{2})$/;

/**
 * 백엔드가 준 시각 문자열을 epoch ms로 바꾼다. 값이 없거나 못 읽으면 null.
 *
 * 백엔드는 LocalDateTime을 오프셋 없이("2026-08-09T12:34:56") 내려준다. 이걸 그냥
 * new Date()에 넣으면 "브라우저가 있는 곳의 시각"으로 읽어서, 기기 시간대가 KST가 아니면
 * 마감시각이 통째로 밀린다(UTC 기기면 9시간, LA면 16시간). 서버·DB·컨테이너는 Asia/Seoul로
 * 고정돼 있으니(Dockerfile / docker-compose의 TZ) 오프셋이 없는 값에는 +09:00을 붙여 읽는다.
 * 나중에 응답이 오프셋이나 Z를 달고 오면 그 값을 그대로 존중한다.
 */
export function parseServerDateTime(value: string | null): number | null {
  if (!value) return null;
  const normalized = HAS_TIMEZONE.test(value) ? value : `${value}${SERVER_UTC_OFFSET}`;
  const parsed = new Date(normalized).getTime();
  return Number.isNaN(parsed) ? null : parsed;
}

/**
 * 결제 제한시각까지 남은 시간을 "m:ss"로 만든다. 이미 지났거나 값이 없으면 null.
 *
 * 예매·목록·상세가 같은 문구를 쓰도록 여기 모았다 - 예전에는 예매 화면에만 있어서,
 * 목록·상세에서는 "결제 대기"라는 배지만 보이고 언제까지 결제해야 하는지 알 수 없었다.
 *
 * null은 "결제할 수 없다"와 같은 뜻이다 - 호출하는 화면은 이 값이 null이면 결제 버튼을
 * 열지 않는다(응답 시점의 paymentAvailable만 믿으면 마감 뒤에도 버튼이 살아 있다).
 */
export function formatRemaining(expiresAt: string | null, now: number): string | null {
  const expiresAtMs = parseServerDateTime(expiresAt);
  if (expiresAtMs === null) return null;
  const diff = expiresAtMs - now;
  if (diff <= 0) return null;
  const totalSeconds = Math.floor(diff / 1000);
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, "0")}`;
}
