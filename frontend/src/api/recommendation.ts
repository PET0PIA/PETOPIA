import { apiClient } from "./client";

/**
 * POST /api/fairs/{fairId}/booth-recommendations, POST /api/fairs/{fairId}/booth-routes
 * 공통 요청 body. 둘 다 petIds/need가 optional이지만 최소 하나는 필수(비면 서버가 400).
 * petIds를 보내면 로그인 필수 + 전부 본인 소유 반려동물이어야 한다(그 외엔 비로그인도 가능).
 */
export interface BoothRecommendationRequest {
  petIds?: number[];
  need?: string;
}

/** 부스 추천 결과 한 건. */
export interface BoothRecommendationItem {
  boothId: number;
  boothName: string;
  reason: string;
  hallName: string;
  slotNumber: string;
}

/**
 * 동선 추천 결과에서 한 홀 안의 부스 하나. order는 그 홀 안에서 몇 번째로 방문하는지(1부터).
 * posX/posY/width/height는 0~1 사이 상대 좌표·크기 - 지도 마커를 그릴 때 쓴다(부스 배치 편집
 * 화면의 BoothCanvas와 동일 단위).
 */
export interface BoothRouteItem {
  boothId: number;
  boothName: string;
  reason: string;
  matched: boolean;
  slotNumber: string;
  order: number;
  posX: number;
  posY: number;
  width: number;
  height: number;
}

/** 동선 추천 결과 - 홀 하나와, 그 홀 안에서 방문 순서가 정해진 부스 목록. */
export interface HallRoute {
  hallId: number;
  hallName: string;
  /** 그 홀에 배치도 이미지가 업로드돼 있으면 지도 배경으로 쓴다. 없으면 null. */
  floorPlanImageUrl: string | null;
  stops: BoothRouteItem[];
}

// 이 도메인은 booth/business처럼 { data } 로 감싸지 않고 DTO(배열)를 그대로 반환한다(백엔드
// RecommendationController 참고) - apiClient에서 unwrap 없이 바로 타입만 지정하면 된다.

export function getBoothRecommendations(fairId: number, payload: BoothRecommendationRequest) {
  return apiClient.post<BoothRecommendationItem[]>(`/api/fairs/${fairId}/booth-recommendations`, payload);
}

export function getBoothRoutes(fairId: number, payload: BoothRecommendationRequest) {
  return apiClient.post<HallRoute[]>(`/api/fairs/${fairId}/booth-routes`, payload);
}
