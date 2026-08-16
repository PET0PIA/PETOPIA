import { apiClient } from "./client";

/** 행사 리뷰 한 건(목록용). 백엔드 FairReviewListItemResponse에 맞춘다. */
export interface FairReviewListItem {
  reviewId: number;
  fairId: number;
  nickname: string;
  /** 평점 1~5 */
  rating: number;
  content: string;
  /** 작성 시점 예매·방문 이력 스냅샷. 지금 백엔드가 항상 false로 저장하므로 화면에선 아직 쓰지 않는다. */
  verifiedVisit: boolean;
  /** ISO LocalDateTime (예: 2026-08-14T10:30:00) */
  createdAt: string;
  updatedAt: string;
}

/** 리뷰 목록 응답(페이지네이션). 백엔드 FairReviewListResponse. */
export interface FairReviewList {
  items: FairReviewListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

/** 리뷰 요약. reviewCount가 0이면 averageRating은 null(AVG NULL). */
export interface FairReviewSummary {
  fairId: number;
  averageRating: number | null;
  reviewCount: number;
}

/** 행사 리뷰 목록(공개, 인증 불필요). page는 0부터. */
export function getFairReviews(fairId: number, page = 0, size = 10) {
  return apiClient.get<FairReviewList>(`/api/fairs/${fairId}/reviews?page=${page}&size=${size}`);
}

/** 행사 리뷰 요약(평균 평점·개수, 공개). */
export function getFairReviewSummary(fairId: number) {
  return apiClient.get<FairReviewSummary>(`/api/fairs/${fairId}/reviews/summary`);
}
