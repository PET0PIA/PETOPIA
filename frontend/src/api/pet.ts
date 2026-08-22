import { apiClient } from "./client";

export type PetGender = "MALE" | "FEMALE";

/** 백엔드 pet_allergy_types.category와 맞춘다(V42__pet_allergy.sql). */
export type PetAllergyCategory = "FOOD" | "ENVIRONMENT" | "OTHER";

/** GET /api/pet-allergy-types 응답 1건. 등록·수정 화면의 선택지. */
export interface PetAllergyType {
  allergyTypeId: number;
  code: string;
  category: PetAllergyCategory;
  label: string;
  /** true면 직접 입력칸이 필요한 항목(기타). code를 특수 분기하지 않고 이 값으로 판단한다. */
  requiresText: boolean;
  sortOrder: number;
}

/** 반려동물에 등록된 알레르기 1건. 백엔드 PetAllergyResponse에 맞춘다. */
export interface PetAllergy {
  allergyTypeId: number;
  code: string;
  category: PetAllergyCategory;
  label: string;
  requiresText: boolean;
  /** requiresText=true인 항목에 사용자가 직접 적은 내용. 그 외에는 null. */
  otherText: string | null;
}

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
  /** 알레르기 여부 3값. null=미입력 / false=없음 / true=있음. */
  hasAllergy: boolean | null;
  allergies: PetAllergy[];
}

/** 등록·수정 요청에 담는 알레르기 선택 1건. */
export interface PetAllergySelection {
  allergyTypeId: number;
  /** requiresText=true인 항목에만 보낸다. 다른 항목에 보내면 서버가 버린다. */
  otherText?: string;
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
  /**
   * 알레르기 여부. 안 보내면 여부와 목록을 모두 그대로 둔다(PATCH),
   * false를 보내면 기존 목록이 전부 지워진다.
   */
  hasAllergy?: boolean;
  /** hasAllergy=true와 함께 보낼 때만 유효하다. 보내면 기존 목록을 이 목록으로 교체한다. */
  allergies?: PetAllergySelection[];
  /** presigned-upload로 받은 임시 객체 키. api/files.ts의 uploadImage()가 반환하는 값. */
  imageObjectKey?: string;
}

/** 알레르기 선택지 목록(공개, 인증 불필요). 활성 항목만 카테고리·정렬순으로 내려온다. */
export function getPetAllergyTypes() {
  return apiClient.get<PetAllergyType[]>("/api/pet-allergy-types");
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
