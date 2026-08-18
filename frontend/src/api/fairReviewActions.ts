import { apiClient } from "./client";

/**
 * 리뷰 작성/수정 쓰기 API. 조회 API(api/review.ts)와 파일을 분리했다 - review.ts는 프론트
 * 개편 담당자가 이미 FairDetailPage.tsx/FairReviews.tsx에서 쓰고 있는 파일이라, 독립 모듈
 * 원칙에 따라 그 파일은 건드리지 않는다(petopia-review-feature-plan 스킬 참고).
 */

/** 리뷰 작성 요청 바디. 백엔드 CreateFairReviewRequest에 맞춘다. */
export interface FairReviewWriteRequest {
  /** 평점 1~5 */
  rating: number;
  content: string;
}

/**
 * 리뷰 수정 요청 바디. 백엔드 UpdateFairReviewRequest에 맞춘다. version은 조회 시점에 받은
 * 낙관적 락 버전을 그대로 돌려보내야 한다 - 그 사이 다른 곳에서 먼저 수정했으면 409
 * (REVIEW_VERSION_CONFLICT)가 온다.
 */
export interface FairReviewUpdateRequest extends FairReviewWriteRequest {
  version: number;
}

/** 리뷰 작성/수정 응답. 백엔드 FairReviewResponse에 맞춘다. */
export interface FairReviewWriteResult {
  reviewId: number;
  fairId: number;
  userId: number;
  rating: number;
  content: string;
  verifiedVisit: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** 리뷰 작성(POST, 로그인 필요 - 예매·방문 여부로 막지 않는다). */
export function createFairReview(fairId: number, payload: FairReviewWriteRequest) {
  return apiClient.post<FairReviewWriteResult>(`/api/fairs/${fairId}/reviews`, payload);
}

/** 리뷰 수정(PATCH, 본인이 작성한 리뷰만 가능). rating·content만 바뀐다. */
export function updateFairReview(fairId: number, reviewId: number, payload: FairReviewUpdateRequest) {
  return apiClient.patch<FairReviewWriteResult>(`/api/fairs/${fairId}/reviews/${reviewId}`, payload);
}

/** 리뷰 삭제(DELETE, 본인이 작성한 리뷰만 가능). 백엔드가 204를 주므로 apiClient가 undefined를 돌려준다. */
export function deleteFairReview(fairId: number, reviewId: number) {
  return apiClient.delete<void>(`/api/fairs/${fairId}/reviews/${reviewId}`);
}

/** 마이페이지 "내 리뷰" 한 건. 백엔드 MyFairReviewResponse에 맞춘다. */
export interface MyFairReviewItem {
  reviewId: number;
  fairId: number;
  fairName: string;
  fairPosterImageUrl: string | null;
  /** 평점 1~5 */
  rating: number;
  content: string;
  verifiedVisit: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** 마이페이지 "내 리뷰" 목록 응답(페이지네이션). 백엔드 MyFairReviewListResponse. */
export interface MyFairReviewList {
  items: MyFairReviewItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

/** 내가 쓴 리뷰 목록(로그인 필요). page는 0부터. */
export function getMyFairReviews(page = 0, size = 10) {
  return apiClient.get<MyFairReviewList>(`/api/users/me/reviews?page=${page}&size=${size}`);
}

/** 백엔드 FairReviewReportReason과 맞춘다. */
export type FairReviewReportReason = "SPAM" | "ABUSE" | "FALSE_INFO" | "ETC";

/** 리뷰 신고 요청 바디. 백엔드 CreateFairReviewReportRequest에 맞춘다. */
export interface FairReviewReportRequest {
  reason: FairReviewReportReason;
  /** reason이 ETC일 때만 필요하다. */
  reasonDetail?: string;
}

/** 리뷰 신고(POST, 로그인 필요). 이미 신고한 리뷰면 409(REVIEW_ALREADY_REPORTED)가 온다. */
export function reportFairReview(fairId: number, reviewId: number, payload: FairReviewReportRequest) {
  return apiClient.post<void>(`/api/fairs/${fairId}/reviews/${reviewId}/reports`, payload);
}

/** 행사 담당자 리뷰 답글. 백엔드 FairReviewReplyResponse에 맞춘다. */
export interface FairReviewReply {
  reviewReplyId: number;
  reviewId: number;
  content: string;
  createdAt: string;
  updatedAt: string;
}

/** 답글 조회(공개). 아직 답글이 없으면 404(REVIEW_REPLY_NOT_FOUND)가 온다 - 정상 상태로 처리한다. */
export function getFairReviewReply(fairId: number, reviewId: number) {
  return apiClient.get<FairReviewReply>(`/api/fairs/${fairId}/reviews/${reviewId}/reply`);
}

/** 답글 작성(POST, 그 행사 담당자만). 이미 답글이 있으면 409(REVIEW_REPLY_ALREADY_EXISTS)가 온다. */
export function createFairReviewReply(fairId: number, reviewId: number, content: string) {
  return apiClient.post<FairReviewReply>(`/api/fairs/${fairId}/reviews/${reviewId}/reply`, { content });
}

/** 답글 수정(PATCH, 그 행사 담당자만). */
export function updateFairReviewReply(fairId: number, reviewId: number, content: string) {
  return apiClient.patch<FairReviewReply>(`/api/fairs/${fairId}/reviews/${reviewId}/reply`, { content });
}
