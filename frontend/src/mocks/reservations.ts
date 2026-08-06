// 예약/입장 도메인 화면용 가짜(mock) 데이터 + 타입.
//
// 백엔드 연동 전이라, 화면은 이 파일의 데이터를 그대로 그린다.
// 나중에 서버와 연동할 때 이 타입에 맞춰 실제 응답을 받으면 된다.
// (필드 이름은 백엔드 ReservationListItemResponse에 맞춰 두었다.)

/** 예약 상태 값 */
export type ReservationStatus =
  | "PENDING_PAYMENT"
  | "CONFIRMED"
  | "CHECKED_IN"
  | "CANCELED"
  | "EXPIRED";

/** 예약 유형: 사전예약 / 현장 직접예매 */
export type ReservationType = "ADVANCE" | "ONSITE_DIRECT";

/** 예약 한 건의 화면 표시용 데이터 */
export interface ReservationListItem {
  reservationId: number;
  /** 예약번호(사람이 읽는 식별자) */
  reservationNo: string;
  fairName: string;
  reservationType: ReservationType;
  /** YYYY-MM-DD */
  visitDate: string;
  /** HH:mm 또는 HH:mm:ss */
  entryStartTime: string;
  entryEndTime: string;
  reservationStatus: ReservationStatus;
  /** 방문일이 지나 종료된 예약인지 */
  isEnded: boolean;
  /** 입장 QR을 보여줄 수 있는 상태인지 */
  qrAvailable: boolean;
  /** 결제 대기 예약에서 "결제 계속하기"를 보여줄지 */
  paymentAvailable: boolean;
  amount: number;
  /** 예약 확정시각(YYYY-MM-DD HH:mm). 미확정이면 null */
  reservedAt: string | null;
  /** 최초 입장시각(YYYY-MM-DD HH:mm). 입장 전이면 null */
  checkedInAt: string | null;
}

// 상태별로 한 건씩 담아, 뱃지 색과 버튼 노출이 화면에서 어떻게 보이는지 한눈에 확인한다.
export const mockReservations: ReservationListItem[] = [
  {
    // 결제 대기: 빨강 뱃지 + 결제 계속하기/취소
    reservationId: 101,
    reservationNo: "R20260906-0101",
    fairName: "2026 서울 펫페어",
    reservationType: "ADVANCE",
    visitDate: "2026-09-18",
    entryStartTime: "10:00:00",
    entryEndTime: "18:00:00",
    reservationStatus: "PENDING_PAYMENT",
    isEnded: false,
    qrAvailable: false,
    paymentAvailable: true,
    amount: 15000,
    reservedAt: null,
    checkedInAt: null,
  },
  {
    // 예약 확정: 초록 뱃지 + QR + 방문일 변경/취소
    reservationId: 102,
    reservationNo: "R20260901-0102",
    fairName: "부산 댕냥 산책 페스타",
    reservationType: "ADVANCE",
    visitDate: "2026-10-03",
    entryStartTime: "11:00:00",
    entryEndTime: "17:00:00",
    reservationStatus: "CONFIRMED",
    isEnded: false,
    qrAvailable: true,
    paymentAvailable: false,
    amount: 12000,
    reservedAt: "2026-08-01 14:00",
    checkedInAt: null,
  },
  {
    // 입장 완료(현장예매): 초록 뱃지, 액션 없음
    reservationId: 103,
    reservationNo: "R20260720-0103",
    fairName: "대전 펫 패밀리데이",
    reservationType: "ONSITE_DIRECT",
    visitDate: "2026-07-20",
    entryStartTime: "10:00:00",
    entryEndTime: "18:00:00",
    reservationStatus: "CHECKED_IN",
    isEnded: true,
    qrAvailable: false,
    paymentAvailable: false,
    amount: 10000,
    reservedAt: "2026-07-20 09:50",
    checkedInAt: "2026-07-20 10:03",
  },
  {
    // 취소됨: 회색 뱃지, 액션 없음
    reservationId: 104,
    reservationNo: "R20260715-0104",
    fairName: "인천 펫 아트페어",
    reservationType: "ADVANCE",
    visitDate: "2026-08-30",
    entryStartTime: "12:00:00",
    entryEndTime: "20:00:00",
    reservationStatus: "CANCELED",
    isEnded: false,
    qrAvailable: false,
    paymentAvailable: false,
    amount: 18000,
    reservedAt: "2026-07-15 10:00",
    checkedInAt: null,
  },
  {
    // 만료됨: 회색 뱃지, 액션 없음
    reservationId: 105,
    reservationNo: "R20260614-0105",
    fairName: "광주 반려생활 박람회",
    reservationType: "ADVANCE",
    visitDate: "2026-06-14",
    entryStartTime: "10:00:00",
    entryEndTime: "17:00:00",
    reservationStatus: "EXPIRED",
    isEnded: true,
    qrAvailable: false,
    paymentAvailable: false,
    amount: 9000,
    reservedAt: null,
    checkedInAt: null,
  },
];
