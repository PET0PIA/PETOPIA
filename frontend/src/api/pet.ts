import { apiClient } from "./client";

export type PetGender = "MALE" | "FEMALE";

/** GET /api/users/me/pets, GET/PATCH /api/users/me/pets/{petId} 응답. 백엔드 PetResponse에 맞춘다. */
export interface Pet {
  petId: number;
  name: string;
  species: string;
  breed: string | null;
  /** YYYY-MM-DD */
  birthDate: string | null;
  gender: PetGender | null;
  isNeutered: boolean | null;
  imageUrl: string | null;
  createdAt: string;
}

/**
 * POST/PATCH 공통 요청 형태. PATCH에서는 보낸 필드만 반영되고 안 보낸 필드는 그대로 둔다.
 * POST(등록)에서는 name/species가 필수라 그때만 값이 채워져 있어야 한다.
 */
export interface PetRequest {
  name?: string;
  species?: string;
  breed?: string;
  /** YYYY-MM-DD */
  birthDate?: string;
  gender?: PetGender;
  isNeutered?: boolean;
  /** presigned-upload로 받은 임시 객체 키. api/files.ts의 uploadImage()가 반환하는 값. */
  imageObjectKey?: string;
}

/** 내 반려동물 목록 조회. */
export function getMyPets() {
  return apiClient.get<Pet[]>("/api/users/me/pets");
}

/** 내 반려동물 상세 조회. 없거나 남의 것이면 404/403. */
export function getPet(petId: number) {
  return apiClient.get<Pet>(`/api/users/me/pets/${petId}`);
}

export function createPet(payload: PetRequest) {
  return apiClient.post<Pet>("/api/users/me/pets", payload);
}

export function updatePet(petId: number, payload: PetRequest) {
  return apiClient.patch<Pet>(`/api/users/me/pets/${petId}`, payload);
}

export function deletePet(petId: number) {
  return apiClient.delete<void>(`/api/users/me/pets/${petId}`);
}
