import { today } from "../../utils/date";
import type { Pet, PetRequest } from "../../api/pet";

export const DOG_BREEDS = ["말티즈", "포메라니안", "푸들", "비숑 프리제", "시츄", "치와와", "골든 리트리버", "래브라도 리트리버", "웰시 코기", "진돗개", "시바견", "닥스훈트", "프렌치 불독", "보더 콜리", "요크셔테리어", "미니어처 슈나우저", "파피용", "페키니즈", "비글", "잭 러셀 테리어", "아메리칸 코커 스패니얼", "잉글리시 코커 스패니얼", "사모예드", "셰틀랜드 쉽독", "도베르만", "저먼 셰퍼드", "시베리안 허스키", "믹스견"] as const;
export const CAT_BREEDS = ["코리안 숏헤어", "페르시안", "러시안 블루", "스코티시 폴드", "브리티시 숏헤어", "먼치킨", "노르웨이 숲", "랙돌", "샴", "벵갈", "메인쿤"] as const;

export type PetSpeciesChoice = "" | "DOG" | "CAT" | "OTHER";

function isKnownBreed(species: PetSpeciesChoice, breed: string) {
  return (species === "DOG" ? DOG_BREEDS : species === "CAT" ? CAT_BREEDS : []).includes(breed as never);
}

export interface PetFormValues {
  name: string;
  /** 통계가 흔들리지 않도록 선택값은 고정하고, 기타만 별도로 직접 입력받는다. */
  speciesChoice: PetSpeciesChoice;
  speciesOther: string;
  breedChoice: string;
  breedOther: string;
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
  speciesChoice: "",
  speciesOther: "",
  breedChoice: "",
  breedOther: "",
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
  const speciesChoice: PetSpeciesChoice = pet.species === "개" || pet.species === "강아지" ? "DOG" : pet.species === "고양이" ? "CAT" : "OTHER";
  const breed = pet.breed ?? "";
  const breedChoice = breed === "" ? "" : isKnownBreed(speciesChoice, breed) ? breed : "OTHER";
  return {
    name: pet.name,
    speciesChoice,
    speciesOther: speciesChoice === "OTHER" ? pet.species : "",
    breedChoice,
    breedOther: breedChoice === "OTHER" ? breed : "",
    birthDate: pet.birthDate ?? "",
    gender: pet.gender ?? "",
    isNeutered: pet.isNeutered === null || pet.isNeutered === undefined ? "" : (String(pet.isNeutered) as "true" | "false"),
    imageObjectKey: null,
  };
}

export function validatePetForm(form: PetFormValues): string[] {
  const errors: string[] = [];
  if (form.name.trim() === "") errors.push("이름을 입력해 주세요.");
  if (form.speciesChoice === "") errors.push("종을 선택해 주세요.");
  if (form.speciesChoice === "OTHER" && form.speciesOther.trim() === "") errors.push("기타 종을 입력해 주세요.");
  if (form.breedChoice === "OTHER" && form.breedOther.trim() === "") errors.push("기타 품종을 입력해 주세요.");
  if (form.birthDate !== "" && form.birthDate >= today()) errors.push("생년월일은 오늘 이전 날짜여야 해요.");
  return errors;
}

export function toPetRequest(form: PetFormValues): PetRequest {
  const species = form.speciesChoice === "DOG" ? "강아지"
    : form.speciesChoice === "CAT" ? "고양이"
      : form.speciesOther.trim();
  const breed = form.breedChoice === "OTHER" ? form.breedOther.trim() : form.breedChoice;
  return {
    name: form.name.trim(),
    species,
    breed: breed === "" ? undefined : breed,
    birthDate: form.birthDate === "" ? undefined : form.birthDate,
    gender: form.gender === "" ? undefined : form.gender,
    isNeutered: form.isNeutered === "" ? undefined : form.isNeutered === "true",
    imageObjectKey: form.imageObjectKey ?? undefined,
  };
}
