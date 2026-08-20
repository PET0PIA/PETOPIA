import { apiClient } from "./client";

/**
 * 태그 기반 통합 리뷰(V39) 제출 API. review.ts(조회)·fairReviewActions.ts(구 별점+텍스트
 * 리뷰의 작성/수정/삭제/신고/답글)와는 별도 파일이다 - fairReviewActions.ts는 프론트 개편
 * 담당자가 이미 FairReviews.tsx/FairReviewForm.tsx/ReportReviewModal.tsx에서 쓰고 있어
 * 독립 모듈 원칙에 따라 건드리지 않는다(petopia-review-feature-plan 스킬 참고).
 */

/** 백엔드 FairReview.CompanionType과 맞춘다. */
export type CompanionType = "ALONE" | "WITH_PET" | "WITH_FAMILY" | "WITH_FRIEND";

/** 백엔드 FairReview.VisitPurpose와 맞춘다. */
export type VisitPurpose = "SHOPPING" | "EXPERIENCE" | "INFO" | "ETC";

/** 백엔드 BoothFeedback.PurchaseBehavior와 맞춘다. 응답을 건너뛸 수 있어 null 허용. */
export type PurchaseBehavior = "PURCHASED" | "FOLLOWED_SNS" | "LOOKED_ONLY";

/** 리뷰 제출 요청 안의 부스별 평가 1건. 백엔드 BoothFeedbackSubmission에 맞춘다. */
export interface BoothFeedbackSubmission {
  boothId: number;
  tagIds: number[];
  purchaseBehavior: PurchaseBehavior | null;
}

/** 통합 리뷰 제출 요청 바디. 백엔드 SubmitFairReviewRequest에 맞춘다. */
export interface SubmitFairReviewRequest {
  companionType: CompanionType;
  visitPurpose: VisitPurpose;
  wouldRevisit: boolean;
  fairTagIds: number[];
  booths: BoothFeedbackSubmission[];
}

/** 제출 성공 응답. 백엔드 FairReviewResponse에 맞춘다. */
export interface FairReviewSubmitResult {
  reviewId: number;
  fairId: number;
  companionType: CompanionType;
  visitPurpose: VisitPurpose;
  wouldRevisit: boolean;
  createdAt: string;
}

/**
 * 통합 리뷰 제출(POST, 로그인 + 그 행사 방문 이력 필요). 행사당 1건만 가능하고 수정·삭제
 * API는 없다 - "새로 작성"만 지원한다.
 */
export function submitFairReview(fairId: number, payload: SubmitFairReviewRequest) {
  return apiClient.post<FairReviewSubmitResult>(`/api/fairs/${fairId}/reviews`, payload);
}

/**
 * "이미 작성했는지" + "이 행사를 방문했는지" 상태. 백엔드 MyReviewStatusResponse에 맞춘다.
 * hasVisited가 false면 제출해도 어차피 REVIEW_VISIT_REQUIRED(RV009)로 막히므로, 마법사
 * 진입 시 미리 안내하고 작성을 시작하지 못하게 한다.
 */
export interface MyReviewStatus {
  alreadyReviewed: boolean;
  reviewId: number | null;
  hasVisited: boolean;
}

/** 리뷰 작성 화면 진입 시 이미 작성했는지 먼저 확인한다(로그인 필요). */
export function getMyReviewStatus(fairId: number) {
  return apiClient.get<MyReviewStatus>(`/api/fairs/${fairId}/reviews/status`);
}
