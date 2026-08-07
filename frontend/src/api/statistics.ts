import { apiClient } from "./client";

/**
 * ReservationDashboardController는 NotificationController와 마찬가지로 응답을
 * ApiResponse<T>({success, status, code, message, data})로 감싸서 내려준다.
 * apiClient는 이 래핑을 모르므로 여기서 data만 꺼내 돌려준다.
 */
interface ApiEnvelope<T> { data: T; }

async function unwrap<T>(promise: Promise<ApiEnvelope<T>>): Promise<T> {
  const envelope = await promise;
  return envelope.data;
}

/** 운영일 하나에 대한 예약 상태별 집계. fair_dates LEFT JOIN 기준이라 예약이 0건인 날짜도 포함된다. */
export interface ReservationDateSummary {
  fairDateId: number;
  /** YYYY-MM-DD */
  operationDate: string;
  capacity: number;
  /** HH:mm:ss */
  entryStartTime: string;
  entryEndTime: string;
  totalCount: number;
  confirmedCount: number;
  checkedInCount: number;
  pendingCount: number;
  canceledCount: number;
  expiredCount: number;
  remainingCapacity: number;
}

export interface QrIssuanceSummary {
  fairDateId: number;
  operationDate: string;
  qrIssuedCount: number;
  qrActiveCount: number;
  qrRevokedCount: number;
}

export interface HourlyEntryTrend {
  /** 0~23 */
  entryHour: number;
  entryCount: number;
}

export interface BoothVisitStat {
  boothId: number;
  boothNumber: string;
  boothName: string;
  uniqueVisitorCount: number;
  totalScanCount: number;
}

export interface LabelCount {
  label: string;
  count: number;
}

export interface PetBreedStat {
  species: string;
  /** 미등록이면 "미등록" 문자열로 내려온다. */
  breed: string;
  count: number;
}

export interface VisitStats {
  /** 실제 입장한 총 방문자 수(중복 제거) */
  totalVisitors: number;
  /** 확정된 예약 수(방문율 분모) */
  totalConfirmedReservations: number;
  /** 방문율 %, 소수점 1자리 */
  visitRate: number;
  channelBreakdown: LabelCount[];
  genderBreakdown: LabelCount[];
  ageGroupBreakdown: LabelCount[];
  petSpeciesBreakdown: LabelCount[];
  petBreedBreakdown: PetBreedStat[];
  /** 데이터 없으면 null */
  avgPetAge: number | null;
}

/** date를 생략하면 해당 행사의 전체 운영일을 반환한다. */
export function getReservationDashboard(fairId: number, date?: string) {
  const query = date ? `?date=${date}` : "";
  return unwrap(apiClient.get<ApiEnvelope<ReservationDateSummary[]>>(`/api/fairs/${fairId}/reservation-dashboard${query}`));
}

export function getQrIssuanceSummary(fairId: number) {
  return unwrap(apiClient.get<ApiEnvelope<QrIssuanceSummary[]>>(`/api/fairs/${fairId}/qr-issuance-summary`));
}

export function getHourlyEntryTrend(fairId: number, date: string) {
  return unwrap(apiClient.get<ApiEnvelope<HourlyEntryTrend[]>>(`/api/fairs/${fairId}/hourly-entry-trend?date=${date}`));
}

export function getBoothVisitStats(fairId: number) {
  return unwrap(apiClient.get<ApiEnvelope<BoothVisitStat[]>>(`/api/fairs/${fairId}/booth-visit-stats`));
}

export function getVisitStats(fairId: number) {
  return unwrap(apiClient.get<ApiEnvelope<VisitStats>>(`/api/fairs/${fairId}/visit-stats`));
}

/**
 * 예약 현황 SSE 구독. 백엔드가 예약 상태 변경(결제완료·취소·QR 스캔 등)이 있을 때마다
 * "dashboard-update" 이벤트로 해당 행사의 전체 운영일 최신 요약을 다시 밀어준다.
 * 연결 직후에도 현재 상태를 한 번 즉시 받는다(서버 쪽 초기 전송).
 */
export function subscribeReservationDashboard(fairId: number, onUpdate: (data: ReservationDateSummary[]) => void) {
  const source = new EventSource(`/api/fairs/${fairId}/reservation-dashboard/stream`);
  source.addEventListener("dashboard-update", (event) => {
    onUpdate(JSON.parse((event as MessageEvent<string>).data) as ReservationDateSummary[]);
  });
  return () => source.close();
}
