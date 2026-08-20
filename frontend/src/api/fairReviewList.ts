import { apiClient } from "./client";
import type { CompanionType, VisitPurpose } from "./fairReviewSubmission";

/**
 * 태그 기반 통합 리뷰(V39)의 공개 목록/요약 조회 API. review.ts(구 별점+텍스트 리뷰의
 * 조회 API, binaryrain1219 소유)와는 별도 파일이다 - 구 API는 백엔드에서 이미 지워졌고,
 * 새 목록/요약 API는 응답 모양이 완전히 달라(별점 대신 태그 라벨) 같은 파일을 재사용하지
 * 않는다.
 */

/** 목록 항목 1건. 백엔드 FairReviewListItemResponse에 맞춘다. */
export interface FairReviewListItem {
  reviewId: number;
  nickname: string;
  companionType: CompanionType;
  visitPurpose: VisitPurpose;
  wouldRevisit: boolean;
  fairTagLabels: string[];
  /** ISO LocalDateTime (예: 2026-08-20T10:30:00) */
  createdAt: string;
}

/** 목록 응답(페이지네이션). 백엔드 FairReviewListResponse에 맞춘다. */
export interface FairReviewList {
  items: FairReviewListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

/** 요약. 백엔드 FairReviewSummaryResponse에 맞춘다 - 별점 평균 대신 재방문 의향 비율. */
export interface FairReviewSummary {
  fairId: number;
  reviewCount: number;
  revisitRate: number;
}

/** 공개 리뷰 목록(인증 불필요). page는 0부터. */
export function getFairReviewList(fairId: number, page = 0, size = 10) {
  return apiClient.get<FairReviewList>(`/api/fairs/${fairId}/reviews?page=${page}&size=${size}`);
}

/** 공개 리뷰 요약(인증 불필요). */
export function getFairReviewSummary(fairId: number) {
  return apiClient.get<FairReviewSummary>(`/api/fairs/${fairId}/reviews/summary`);
}
