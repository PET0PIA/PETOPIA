import { apiClient, type ApiEnvelope } from "./client";
import type { LinkTarget } from "./banner";

/** imageKey는 banner.ts와 동일하게 완성된 이미지 URL이다. */
export interface Popup {
  popupId: number;
  title: string;
  imageKey: string;
  linkUrl: string | null;
  linkTarget: LinkTarget;
  width: number | null;
  height: number | null;
  isActive: boolean;
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
}

export interface PopupInput {
  title: string;
  /** ImageUploadField가 돌려주는 presigned objectKey. 수정 시 생략하면 기존 이미지 유지. */
  imageKey?: string;
  linkUrl?: string;
  linkTarget: LinkTarget;
  width?: number;
  height?: number;
  startedAt?: string;
  endedAt?: string;
}

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
export async function createPopup(payload: PopupInput): Promise<Popup> {
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
