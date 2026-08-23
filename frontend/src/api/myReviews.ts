import { apiClient } from "./client";
import type { CompanionType, VisitPurpose } from "./fairReviewSubmission";

/**
 * 마이페이지 "내 리뷰" 조회 API. fairReviewList.ts(특정 행사의 공개 목록)와 달리 여러 행사에
 * 걸친 내 리뷰를 한 번에 보여준다. 백엔드 MyReviewController(/api/reviews/me)에 맞춘다.
 * 조회 전용이다 - 수정·본인 삭제 API는 없다(petopia-review-feature-plan 스킬 참고).
 */

/** 목록 항목 1건. 백엔드 MyReviewListItemResponse에 맞춘다. */
export interface MyReviewListItem {
  reviewId: number;
  fairId: number;
  fairName: string;
  posterImageUrl: string | null;
  companionType: CompanionType;
  visitPurpose: VisitPurpose;
  wouldRevisit: boolean;
  fairTagLabels: string[];
  /** ISO LocalDateTime (예: 2026-08-20T10:30:00) */
  createdAt: string;
}

/** 목록 응답(페이지네이션). 백엔드 MyReviewListResponse에 맞춘다. */
export interface MyReviewList {
  items: MyReviewListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

/** 내 리뷰 목록(로그인 필요). page는 0부터. */
export function getMyReviews(page = 0, size = 10) {
  return apiClient.get<MyReviewList>(`/api/reviews/me?page=${page}&size=${size}`);
}
