import { apiClient } from "./client";
import type { CompanionType, VisitPurpose } from "./fairReviewSubmission";

/** "값별 개수" 집계 한 줄(동반유형·방문목적 분포). ratio는 0~1. */
export interface CountItem {
  key: string;
  count: number;
  ratio: number;
}

export interface TagCountItem {
  tagId: number;
  label: string;
  count: number;
  ratio: number;
}

/** 카테고리 하나의 긍정/개선 필요 TOP5(count 내림차순, 서버에서 이미 잘라서 내려온다). */
export interface CategoryTagRanking {
  category: string;
  positiveTop: TagCountItem[];
  negativeTop: TagCountItem[];
}

/**
 * 리뷰(태그) 데이터 기반 통계. 예약·입장 데이터 기반 통계(총방문자·시간대별 추이 등)는
 * "방문 통계"·"예약 현황" 화면이 api/statistics.ts로 이미 따로 보여주고 있어 여기서는
 * 다루지 않는다 - 이 API는 FairReviewStatsController가 ApiResponse로 감싸지 않고
 * FairReviewStatsResponse를 그대로 내려준다는 점에서 statistics.ts와 다르다.
 */
export interface FairReviewStats {
  reviewCount: number;
  companionTypeDistribution: CountItem[];
  visitPurposeDistribution: CountItem[];
  /** 재방문 의향 비율(0~1) */
  revisitRate: number;
  fairTagRankings: CategoryTagRanking[];
}

export function getFairReviewStats(fairId: number) {
  return apiClient.get<FairReviewStats>(`/api/fairs/${fairId}/reviews/stats`);
}

// CompanionType/VisitPurpose는 fairReviewSubmission.ts의 타입을 그대로 재사용한다(중복 선언 방지).
export type { CompanionType, VisitPurpose };
