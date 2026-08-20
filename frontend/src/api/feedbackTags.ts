import { apiClient } from "./client";

/** 백엔드 FeedbackTag.Scope와 맞춘다. */
export type FeedbackTagScope = "FAIR" | "BOOTH";

/** 백엔드 FeedbackTag.Sentiment와 맞춘다. */
export type FeedbackTagSentiment = "POSITIVE" | "NEGATIVE";

/**
 * 태그 마스터 1건. 백엔드 FeedbackTagResponse에 맞춘다.
 * category는 scope별로 유효한 값이 다르다(V39__unified_review_feedback.sql 참고) - FAIR는
 * GUIDE_OPERATION/SAFETY_HYGIENE/WAIT_FLOW/PET_CONVENIENCE/FACILITY/PRICE_VALUE/
 * CONTENT_PROGRAM, BOOTH는 CONSULTATION/PRODUCT/EXPERIENCE/PRICE_BENEFIT/BOOTH_ENVIRONMENT.
 */
export interface FeedbackTagItem {
  tagId: number;
  scope: FeedbackTagScope;
  category: string;
  sentiment: FeedbackTagSentiment;
  label: string;
  sortOrder: number;
}

/** 활성 태그 목록(공개, 인증 불필요). 리뷰 마법사가 카테고리별로 묶어서 보여준다. */
export function getActiveFeedbackTags(scope: FeedbackTagScope) {
  return apiClient.get<FeedbackTagItem[]>(`/api/feedback-tags?scope=${scope}`);
}
