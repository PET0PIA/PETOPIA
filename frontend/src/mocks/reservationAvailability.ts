// "예매하기" 화면용 가짜(mock) 데이터 + 타입.
// 행사(fairId)별로 예매 가능한 운영일과 잔여석, 그리고 현장예매 정보를 담는다.

/** 운영일 한 건의 예매 가능 정보 (사전예약용) */
export interface FairDateAvailability {
  fairDateId: number;
  /** YYYY-MM-DD */
  visitDate: string;
  /** HH:mm 또는 HH:mm:ss */
  entryStartTime: string;
  entryEndTime: string;
  remainingCapacity: number;
  /** 예매 가능 여부(마감이면 false) */
  available: boolean;
}

/** 오늘 현장예매 정보 */
export interface OnsiteAvailability {
  /** 오늘 현장예매를 접수 중인지 */
  available: boolean;
  /** 오늘 방문일 YYYY-MM-DD */
  visitDate: string;
  entryStartTime: string;
  entryEndTime: string;
  /** 현장예매 금액. 사전예약금과 독립적이다(0이면 무료). */
  price: number;
}

/** 한 행사의 예매 화면에 필요한 정보 */
export interface FairReservationInfo {
  fairId: number;
  fairName: string;
  placeName: string;
  /** 화면 표시용 기간 문자열 */
  period: string;
  /** 사전예약 금액(0이면 무료) */
  reservationFee: number;
  dates: FairDateAvailability[];
  /** 현장예매 정보. null이면 이 행사는 현장예매를 접수하지 않는다. */
  onsite: OnsiteAvailability | null;
}

// fairId(문자열) → 예매 정보. 티켓 목록(#1)에서 넘어온다고 가정한다.
const fairs: Record<string, FairReservationInfo> = {
  // 유료 사전예약 + 유료 현장예매
  "1": {
    fairId: 1,
    fairName: "2026 서울 펫페어",
    placeName: "서울 코엑스 C홀",
    period: "2026-09-18 ~ 09-20",
    reservationFee: 15000,
    dates: [
      { fairDateId: 11, visitDate: "2026-09-18", entryStartTime: "10:00:00", entryEndTime: "18:00:00", remainingCapacity: 213, available: true },
      { fairDateId: 12, visitDate: "2026-09-19", entryStartTime: "10:00:00", entryEndTime: "18:00:00", remainingCapacity: 48, available: true },
      { fairDateId: 13, visitDate: "2026-09-20", entryStartTime: "10:00:00", entryEndTime: "17:00:00", remainingCapacity: 0, available: false },
    ],
    onsite: { available: true, visitDate: "2026-08-06", entryStartTime: "10:00:00", entryEndTime: "18:00:00", price: 20000 },
  },
  // 유료 사전예약 + 현장예매 미접수
  "2": {
    fairId: 2,
    fairName: "부산 댕냥 산책 페스타",
    placeName: "벡스코 제2전시장",
    period: "2026-10-03 ~ 10-04",
    reservationFee: 12000,
    dates: [
      { fairDateId: 21, visitDate: "2026-10-03", entryStartTime: "11:00:00", entryEndTime: "17:00:00", remainingCapacity: 120, available: true },
      { fairDateId: 22, visitDate: "2026-10-04", entryStartTime: "11:00:00", entryEndTime: "17:00:00", remainingCapacity: 85, available: true },
    ],
    onsite: null,
  },
  // 무료 사전예약 + 유료 현장예매 (사전이 무료여도 현장은 유료일 수 있음)
  "3": {
    fairId: 3,
    fairName: "무료 반려동물 입양의 날",
    placeName: "일산 킨텍스 제1전시장",
    period: "2026-11-07 ~ 11-08",
    reservationFee: 0,
    dates: [
      { fairDateId: 31, visitDate: "2026-11-07", entryStartTime: "10:00:00", entryEndTime: "18:00:00", remainingCapacity: 300, available: true },
      { fairDateId: 32, visitDate: "2026-11-08", entryStartTime: "10:00:00", entryEndTime: "18:00:00", remainingCapacity: 150, available: true },
    ],
    onsite: { available: true, visitDate: "2026-08-06", entryStartTime: "11:00:00", entryEndTime: "17:00:00", price: 10000 },
  },
};

/** fairId로 예매 정보를 찾는다. 없으면 undefined. */
export function getFairReservationInfo(fairId: string | undefined): FairReservationInfo | undefined {
  if (!fairId) return undefined;
  return fairs[fairId];
}
