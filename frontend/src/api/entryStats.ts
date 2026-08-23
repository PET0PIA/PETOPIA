import { apiClient } from "./client";

/**
 * 홈 상단 숫자용 공개 입장 요약. 백엔드 PublicEntryStatsResponse와 맞춘다.
 *
 * 둘 다 중복을 제거한 "고유" 수다 - 같은 사람이 행사 세 곳에 다녀왔어도 1명으로 센다.
 * 관리자 대시보드(getAdminDashboardSummary)의 totalVisitors는 행사별 방문자를 더한 누적
 * 값이라 이 숫자보다 크게 나온다. 두 화면의 숫자가 다른 건 정의 차이지 버그가 아니다.
 */
export interface PublicEntryStats {
  /** 한 번이라도 행사에 입장한 사용자 수 */
  visitorCount: number;
  /** 그 입장에 함께 온 반려동물 수. 동반 예약으로 등록된 반려동물만 잡힌다. */
  petCount: number;
}

/** 로그인 없이 호출할 수 있다(합계 숫자만 내려오고 개인정보는 없다). */
export function getPublicEntryStats() {
  return apiClient.get<PublicEntryStats>("/api/v1/entry-stats/public");
}
