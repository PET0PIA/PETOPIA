import { apiClient, type ApiEnvelope } from "./client";

export type BoothSlotStatus = "AVAILABLE" | "PENDING" | "CONFIRMED";

export interface RecruitNoticeBoothSlot {
  boothSlotsId: number;
  slotNumber: string;
  price: number;
  status: BoothSlotStatus;
  /** CONFIRMED일 때만 채워짐 */
  businessName: string | null;
}

export interface RecruitNotice {
  recruitNoticeId: number;
  fairId: number;
  title: string;
  content: string;
  imageUrl: string | null;
  recruitDeadline: string;
  updatedAt: string;
  /** 저장값이 아니라 조회 시점마다 서버가 계산: 마감 지났으면 true */
  closed: boolean;
  boothSlots: RecruitNoticeBoothSlot[];
}

// 백엔드가 ApiResponse<T>로 감싸서 응답하므로(fair 도메인과 달리 DTO를 바로 안 줌) data만 꺼내서 돌려준다.
// 모집 공고 상세 + 부스슬롯 현황 조회
export async function getRecruitNotice(fairId: number): Promise<RecruitNotice> {
  const response = await apiClient.get<ApiEnvelope<RecruitNotice>>(`/api/fairs/${fairId}/recruit-detail`);
  return response.data;
}