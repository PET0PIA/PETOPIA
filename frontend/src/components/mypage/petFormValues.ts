import { today } from "../../utils/date";
import type { Pet, PetRequest } from "../../api/pet";

export interface PetFormValues {
  name: string;
  species: string;
  breed: string;
  /** YYYY-MM-DD, 빈 문자열이면 미입력 */
  birthDate: string;
  gender: "" | "MALE" | "FEMALE";
  /** 서버는 Boolean(3값)이라 select로 받는다 - ""는 "선택 안 함"(값 안 보냄) */
  isNeutered: "" | "true" | "false";
  /** 새로 첨부한 사진의 objectKey. null이면 "새로 첨부 안 함"(수정에서는 기존 사진 유지). */
  imageObjectKey: string | null;
}

export const emptyPetForm: PetFormValues = {
  name: "",
  species: "",
  breed: "",
  birthDate: "",
  gender: "",
  isNeutered: "",
  imageObjectKey: null,
};

/**
 * 반려동물 상세 조회 응답을 폼 초기값으로 변환한다. imageObjectKey는 항상 null로 시작한다 -
 * 기존 사진은 폼 값이 아니라 PetForm의 initialImageUrl prop으로 미리보기만 보여주고,
 * 사용자가 실제로 새 파일을 고를 때만 값이 채워진다.
 */
export function toPetFormValues(pet: Pet): PetFormValues {
  return {
    name: pet.name,
    species: pet.species,
    breed: pet.breed ?? "",
    birthDate: pet.birthDate ?? "",
    gender: pet.gender ?? "",
    isNeutered: pet.isNeutered === null || pet.isNeutered === undefined ? "" : (String(pet.isNeutered) as "true" | "false"),
    imageObjectKey: null,
  };
}

export function validatePetForm(form: PetFormValues): string[] {
  const errors: string[] = [];
  if (form.name.trim() === "") errors.push("이름을 입력해 주세요.");
  if (form.species.trim() === "") errors.push("종을 입력해 주세요. (예: 강아지, 고양이, 햄스터)");
  if (form.birthDate !== "" && form.birthDate >= today()) errors.push("생년월일은 오늘 이전 날짜여야 해요.");
  return errors;
}

export function toPetRequest(form: PetFormValues): PetRequest {
  return {
    name: form.name.trim(),
    species: form.species.trim(),
    breed: form.breed.trim() === "" ? undefined : form.breed.trim(),
    birthDate: form.birthDate === "" ? undefined : form.birthDate,
    gender: form.gender === "" ? undefined : form.gender,
    isNeutered: form.isNeutered === "" ? undefined : form.isNeutered === "true",
    imageObjectKey: form.imageObjectKey ?? undefined,
  };
}
