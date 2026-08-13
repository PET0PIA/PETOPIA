import { apiClient, type ApiEnvelope } from "./client";

export type BoothTargetAnimal = "DOG" | "CAT" | "ETC";
export type BoothItemType = "PRODUCT" | "EVENT" | "SAMPLE";

export interface BoothItemResponse {
  boothItemId: number;
  boothId: number;
  name: string;
  type: BoothItemType;
  imageUrl: string | null;
  note: string | null;
}

export interface BoothResponse {
  boothId: number;
  name: string;
  intro: string | null;
  category: string | null;
  targetAnimal: BoothTargetAnimal | null;
  imageUrl: string | null;
  items: BoothItemResponse[];
  favorited: boolean;
}

export interface BoothFavoriteResponse {
  boothId: number;
  name: string;
  imageUrl: string | null;
  category: string | null;
  targetAnimal: BoothTargetAnimal | null;
  fairId: number;
  fairName: string;
}

export interface BoothUpdateRequest {
  name?: string;
  intro?: string;
  category?: string;
  targetAnimal?: BoothTargetAnimal;
  imageObjectKey?: string;
}

export interface BoothItemCreateRequest {
  name: string;
  type: BoothItemType;
  imageObjectKey?: string;
  note?: string;
}

export interface BoothItemUpdateRequest {
  name?: string;
  type?: BoothItemType;
  imageObjectKey?: string;
  note?: string;
}

export interface ConfirmedBoothResponse {
  boothId: number;
  businessName: string;
  imageUrl: string | null;
  slotNumber: string;
  posX: number | null;
  posY: number | null;
  width: number | null;
  height: number | null;
  hallId: number;
  hallName: string;
  floorPlanImageUrl: string | null;
}

// 부스 상세 조회 (공개, 선택적 인증 - 로그인 시 favorited가 실제 값으로 내려옴)
export async function getBooth(boothId: number): Promise<BoothResponse> {
  const response = await apiClient.get<ApiEnvelope<BoothResponse>>(`/api/booths/${boothId}`);
  return response.data;
}

// 부스 프로필 부분 수정 (본인 소유만)
export async function updateBooth(boothId: number, payload: BoothUpdateRequest): Promise<BoothResponse> {
  const response = await apiClient.put<ApiEnvelope<BoothResponse>>(`/api/booths/${boothId}`, payload);
  return response.data;
}

// 내 즐겨찾기 목록 조회
export async function getMyFavoriteBooths(): Promise<BoothFavoriteResponse[]> {
  const response = await apiClient.get<ApiEnvelope<BoothFavoriteResponse[]>>("/api/booths/favorites");
  return response.data;
}

// 즐겨찾기 추가
export async function addBoothFavorite(boothId: number): Promise<void> {
  await apiClient.post<ApiEnvelope<null>>(`/api/booths/${boothId}/favorites`);
}

// 즐겨찾기 삭제
export async function removeBoothFavorite(boothId: number): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/api/booths/${boothId}/favorites`);
}

// 판매상품·이벤트 등록 (본인 소유 부스만)
export async function createBoothItem(boothId: number, payload: BoothItemCreateRequest): Promise<BoothItemResponse> {
  const response = await apiClient.post<ApiEnvelope<BoothItemResponse>>(`/api/booths/${boothId}/items`, payload);
  return response.data;
}

// 판매상품·이벤트 부분 수정 (본인 소유 부스만)
export async function updateBoothItem(boothItemId: number, payload: BoothItemUpdateRequest): Promise<BoothItemResponse> {
  const response = await apiClient.put<ApiEnvelope<BoothItemResponse>>(`/api/booth-items/${boothItemId}`, payload);
  return response.data;
}

// 판매상품·이벤트 삭제 (본인 소유 부스만)
export async function deleteBoothItem(boothItemId: number): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(`/api/booth-items/${boothItemId}`);
}

// 행사의 확정 부스 안내판 조회 (공개, 인증 불필요)
export async function getConfirmedBooths(fairId: number): Promise<ConfirmedBoothResponse[]> {
  const response = await apiClient.get<ApiEnvelope<ConfirmedBoothResponse[]>>(`/api/fairs/${fairId}/confirmed-booths`);
  return response.data;
}