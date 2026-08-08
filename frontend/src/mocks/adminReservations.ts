// 박람회 관리자 "예약 현황" 화면용 가짜(mock) 데이터 + 타입.
// 예약 상태 값은 사용자 화면과 동일하게 재사용한다.
import type { ReservationStatus } from "./reservations";

/** 예약 현황 요약 숫자 */
export interface ReservationSummary {
  capacity: number;
  reserved: number;
  checkedIn: number;
  closed: number;
}

/** 관리자 예약 현황 테이블의 한 행 */
export interface AdminReservationRow {
  reservationId: number;
  reservationNo: string;
  visitorName: string;
  /** YYYY-MM-DD */
  visitDate: string;
  reservationStatus: ReservationStatus;
  amount: number;
  /** YYYY-MM-DD HH:mm */
  reservedAt: string;
}

export const mockReservationSummary: ReservationSummary = {
  capacity: 500,
  reserved: 137,
  checkedIn: 42,
  closed: 8,
};

export const mockAdminReservations: AdminReservationRow[] = [
  { reservationId: 1007, reservationNo: "R-20260918-0007", visitorName: "김포피", visitDate: "2026-09-18", reservationStatus: "CONFIRMED", amount: 15000, reservedAt: "2026-08-05 14:30" },
  { reservationId: 1006, reservationNo: "R-20260918-0006", visitorName: "이보리", visitDate: "2026-09-18", reservationStatus: "CHECKED_IN", amount: 15000, reservedAt: "2026-08-05 11:02" },
  { reservationId: 1005, reservationNo: "R-20260918-0005", visitorName: "박초코", visitDate: "2026-09-18", reservationStatus: "PENDING_PAYMENT", amount: 15000, reservedAt: "2026-08-06 09:12" },
  { reservationId: 1004, reservationNo: "R-20260919-0004", visitorName: "최두부", visitDate: "2026-09-19", reservationStatus: "CONFIRMED", amount: 15000, reservedAt: "2026-08-04 18:45" },
  { reservationId: 1003, reservationNo: "R-20260919-0003", visitorName: "정마루", visitDate: "2026-09-19", reservationStatus: "CANCELED", amount: 15000, reservedAt: "2026-08-03 20:10" },
  { reservationId: 1002, reservationNo: "R-20260919-0002", visitorName: "한별", visitDate: "2026-09-19", reservationStatus: "CONFIRMED", amount: 15000, reservedAt: "2026-08-03 13:22" },
  { reservationId: 1001, reservationNo: "R-20260920-0001", visitorName: "오양이", visitDate: "2026-09-20", reservationStatus: "EXPIRED", amount: 15000, reservedAt: "2026-08-02 10:05" },
  { reservationId: 1000, reservationNo: "R-20260920-0000", visitorName: "서구름", visitDate: "2026-09-20", reservationStatus: "CHECKED_IN", amount: 15000, reservedAt: "2026-08-01 16:40" },
];

/** 필터용 운영일 목록(중복 제거, 오름차순) */
export const mockOperationDates = Array.from(
  new Set(mockAdminReservations.map((row) => row.visitDate)),
).sort();
