import { apiClient, type ApiEnvelope } from "./client";
import type { LinkTarget } from "./banner";

/** imageKey는 banner.ts와 동일하게 완성된 이미지 URL이다. */
export interface Popup {
  popupId: number;
  title: string;
  subtitle: string | null;
  imageKey: string | null;
  linkUrl: string | null;
  linkTarget: LinkTarget;
  linkLabel: string | null;
  bgColor: string | null;
  width: number | null;
  height: number | null;
  /** banner.ts의 Banner.active와 동일한 이유(Jackson의 isActive() -> "active" 직렬화)로 active를 쓴다. */
  active: boolean;
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
}

export interface PopupInput {
  title: string;
  subtitle?: string;
  /** ImageUploadField가 돌려주는 presigned objectKey. 수정 시 생략하면 기존 이미지 유지. */
  imageKey?: string;
  linkUrl?: string;
  linkTarget: LinkTarget;
  linkLabel?: string;
  bgColor?: string;
  width?: number;
  height?: number;
  startedAt?: string;
  endedAt?: string;
}

/**
 * 등록 시에는 imageKey와 subtitle 중 최소 하나가 있어야 한다. startedAt/endedAt은 배너와
 * 달리 여전히 선택값이다(비우면 즉시 시작/무기한 노출) - 팝업 DB 컬럼은 nullable로 유지됨.
 */
export type PopupCreateInput = Omit<PopupInput, "imageKey" | "subtitle"> &
  ({ imageKey: string; subtitle?: string } | { imageKey?: string; subtitle: string });

// 공개 - 노출 중인 팝업 목록 (기간 유효)
export async function getActivePopups(): Promise<Popup[]> {
  const response = await apiClient.get<ApiEnvelope<Popup[]>>("/api/popups/active");
  return response.data;
}

// 관리자 - 전체 팝업 목록
export async function getAdminPopups(): Promise<Popup[]> {
  const response = await apiClient.get<ApiEnvelope<Popup[]>>("/api/admin/popups");
  return response.data;
}

// 관리자 - 등록
export async function createPopup(payload: PopupCreateInput): Promise<Popup> {
  const response = await apiClient.post<ApiEnvelope<Popup>>("/api/admin/popups", payload);
  return response.data;
}

// 관리자 - 수정
export async function updatePopup(popupId: number, payload: Partial<PopupInput>): Promise<Popup> {
  const response = await apiClient.put<ApiEnvelope<Popup>>(`/api/admin/popups/${popupId}`, payload);
  return response.data;
}

// 관리자 - 삭제
export async function deletePopup(popupId: number): Promise<void> {
  await apiClient.delete(`/api/admin/popups/${popupId}`);
}

// 관리자 - 노출 토글
export async function togglePopupActive(popupId: number, isActive: boolean): Promise<void> {
  await apiClient.patch(`/api/admin/popups/${popupId}/toggle?isActive=${isActive}`);
}
