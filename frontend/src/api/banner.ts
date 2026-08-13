import { apiClient, type ApiEnvelope } from "./client";

export type LinkTarget = "SELF" | "BLANK";

/**
 * imageKey는 필드명과 달리 완성된 이미지 URL이다. 백엔드가 등록/수정 시 presigned
 * objectKey를 확정 처리(tmp -> uploads)하고 storageService.toPublicUrl()로 치환해서
 * 저장하기 때문 - <img src>에 그대로 써도 된다.
 */
export interface Banner {
  bannerId: number;
  title: string;
  eyebrow: string | null;
  subtitle: string | null;
  imageKey: string;
  linkUrl: string | null;
  linkTarget: LinkTarget;
  linkLabel: string | null;
  link2Label: string | null;
  link2Url: string | null;
  link2Target: LinkTarget | null;
  bgColor: string | null;
  sortOrder: number;
  isActive: boolean;
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
}

export interface BannerInput {
  title: string;
  eyebrow?: string;
  subtitle?: string;
  /** ImageUploadField가 돌려주는 presigned objectKey. 수정 시 생략하면 기존 이미지 유지. */
  imageKey?: string;
  linkUrl?: string;
  linkTarget: LinkTarget;
  linkLabel?: string;
  link2Label?: string;
  link2Url?: string;
  link2Target?: LinkTarget;
  bgColor?: string;
  sortOrder: number;
  startedAt?: string;
  endedAt?: string;
}

/** 등록 시에는 imageKey와 노출 기간(startedAt/endedAt)이 필수다. */
export interface BannerCreateInput extends Omit<BannerInput, "imageKey" | "startedAt" | "endedAt"> {
  imageKey: string;
  startedAt: string;
  endedAt: string;
}

// 공개 - 노출 중인 배너 목록 (기간 유효, sort_order 오름차순)
export async function getActiveBanners(): Promise<Banner[]> {
  const response = await apiClient.get<ApiEnvelope<Banner[]>>("/api/banners/active");
  return response.data;
}

// 관리자 - 전체 배너 목록
export async function getAdminBanners(): Promise<Banner[]> {
  const response = await apiClient.get<ApiEnvelope<Banner[]>>("/api/admin/banners");
  return response.data;
}

// 관리자 - 등록
export async function createBanner(payload: BannerCreateInput): Promise<Banner> {
  const response = await apiClient.post<ApiEnvelope<Banner>>("/api/admin/banners", payload);
  return response.data;
}

// 관리자 - 수정
export async function updateBanner(bannerId: number, payload: Partial<BannerInput>): Promise<Banner> {
  const response = await apiClient.put<ApiEnvelope<Banner>>(`/api/admin/banners/${bannerId}`, payload);
  return response.data;
}

// 관리자 - 삭제
export async function deleteBanner(bannerId: number): Promise<void> {
  await apiClient.delete(`/api/admin/banners/${bannerId}`);
}

// 관리자 - 노출 토글
export async function toggleBannerActive(bannerId: number, isActive: boolean): Promise<void> {
  await apiClient.patch(`/api/admin/banners/${bannerId}/toggle?isActive=${isActive}`);
}

// 관리자 - 드래그 앤 드롭으로 정렬한 순서 일괄 저장
export async function updateBannerOrder(bannerIds: number[]): Promise<void> {
  await apiClient.put("/api/admin/banners/order", { bannerIds });
}
