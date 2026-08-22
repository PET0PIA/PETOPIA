import { useEffect, useState, type FormEvent } from "react";
import { Button } from "../ui/Button";
import { ImageUploadField } from "../ui/ImageUploadField";
import { Input } from "../ui/Input";
import { Select } from "../ui/Select";
import { clearDateOnDelete, maxBirthDate } from "../../utils/date";
import { CAT_BREEDS, DOG_BREEDS, emptyPetForm, toPetRequest, validatePetForm, type PetFormValues } from "./petFormValues";
import { getPetAllergyTypes, type PetAllergyCategory, type PetAllergyType, type PetRequest } from "../../api/pet";

const allergyCategoryLabels: Record<PetAllergyCategory, string> = {
  FOOD: "음식·사료",
  ENVIRONMENT: "환경",
  OTHER: "기타",
};

const allergyCategoryOrder: PetAllergyCategory[] = ["FOOD", "ENVIRONMENT", "OTHER"];

interface PetFormProps {
  initialValues?: PetFormValues;
  /** 수정 화면에서 기존에 저장된 사진 미리보기용. 등록 화면에서는 생략한다. */
  initialImageUrl?: string | null;
  submitLabel: string;
  submitting: boolean;
  submitError?: string | null;
  onSubmit: (payload: PetRequest) => void;
  onCancel?: () => void;
}

/** 반려동물 등록/수정 공통 폼. 두 화면(등록·상세 수정)에서 그대로 재사용한다. */
export function PetForm({ initialValues, initialImageUrl, submitLabel, submitting, submitError, onSubmit, onCancel }: PetFormProps) {
  const [form, setForm] = useState<PetFormValues>(initialValues ?? emptyPetForm);
  const [errors, setErrors] = useState<string[]>([]);
  // 이미지 업로드가 끝나기 전에 제출되면 objectKey가 아직 없는 채로 저장 요청이 나가버린다 -
  // 업로드 중에는 제출 버튼을 막아야 한다.
  const [uploadingImage, setUploadingImage] = useState(false);
  // 알레르기 선택지는 마스터 테이블에서 온다(항목 추가가 배포 없이 되도록). 폼이 직접
  // 불러오므로 이 폼을 쓰는 화면(마이페이지 등록·수정)은 아무것도 넘겨줄 필요가 없다.
  const [allergyTypes, setAllergyTypes] = useState<PetAllergyType[]>([]);
  const [allergyLoadFailed, setAllergyLoadFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    getPetAllergyTypes()
      .then((types) => {
        if (alive) setAllergyTypes(types);
      })
      .catch(() => {
        // 목록을 못 불러와도 이름·종 같은 나머지 입력은 계속 할 수 있어야 한다 -
        // 화면 전체를 막지 않고 알레르기 칸만 안내 문구로 대체한다.
        if (alive) setAllergyLoadFailed(true);
      });
    return () => {
      alive = false;
    };
  }, []);

  function update<K extends keyof PetFormValues>(key: K, value: PetFormValues[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  function updateSpecies(value: PetFormValues["speciesChoice"]) {
    setForm((previous) => ({ ...previous, speciesChoice: value, speciesOther: "", breedChoice: "", breedOther: "" }));
  }

  const breeds = form.speciesChoice === "DOG" ? DOG_BREEDS : form.speciesChoice === "CAT" ? CAT_BREEDS : [];

  function toggleAllergy(allergyTypeId: number) {
    setForm((previous) => {
      const selected = previous.allergyTypeIds.includes(allergyTypeId);
      return {
        ...previous,
        allergyTypeIds: selected
          ? previous.allergyTypeIds.filter((id) => id !== allergyTypeId)
          : [...previous.allergyTypeIds, allergyTypeId],
      };
    });
  }

  function updateAllergyOtherText(allergyTypeId: number, value: string) {
    setForm((previous) => ({
      ...previous,
      allergyOtherTexts: { ...previous.allergyOtherTexts, [allergyTypeId]: value },
    }));
  }

  // "없음"/"선택 안 함"으로 되돌리면 고른 항목이 남아 있어도 서버로 안 나가지만(toPetRequest),
  // 다시 "있음"을 골랐을 때 이전 선택이 되살아나면 사용자가 놀란다 - 여부를 바꿀 때 비운다.
  function updateHasAllergy(value: PetFormValues["hasAllergy"]) {
    setForm((previous) => ({ ...previous, hasAllergy: value, allergyTypeIds: [], allergyOtherTexts: {} }));
  }

  const selectedRequiresTextTypes = allergyTypes.filter(
    (type) => type.requiresText && form.allergyTypeIds.includes(type.allergyTypeId),
  );

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validatePetForm(form, allergyTypes);
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;
    onSubmit(toPetRequest(form, allergyTypes));
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5">
      <ImageUploadField
        label="사진"
        initialImageUrl={initialImageUrl}
        onObjectKeyChange={(objectKey) => update("imageObjectKey", objectKey)}
        onUploadingChange={setUploadingImage}
        disabled={submitting}
      />

      <div>
        <label htmlFor="pet-name" className="mb-1.5 block text-sm font-bold text-ink">
          이름<span className="ml-1 text-primary-strong">*</span>
        </label>
        <Input id="pet-name" value={form.name} onChange={(e) => update("name", e.target.value)} maxLength={50} placeholder="예: 초코" />
      </div>

      <div>
        <label htmlFor="pet-species" className="mb-1.5 block text-sm font-bold text-ink">
          종<span className="ml-1 text-primary-strong">*</span>
        </label>
        <Select id="pet-species" value={form.speciesChoice} onChange={(e) => updateSpecies(e.target.value as PetFormValues["speciesChoice"])}>
          <option value="">종 선택</option>
          <option value="DOG">강아지</option>
          <option value="CAT">고양이</option>
          <option value="OTHER">기타(직접 입력)</option>
        </Select>
      </div>

      {form.speciesChoice === "OTHER" && (
        <div>
          <label htmlFor="pet-species-other" className="mb-1.5 block text-sm font-bold text-ink">기타 종</label>
          <Input id="pet-species-other" value={form.speciesOther} onChange={(e) => update("speciesOther", e.target.value)} maxLength={20} placeholder="예: 물고기, 햄스터" />
        </div>
      )}

      {form.speciesChoice !== "OTHER" && form.speciesChoice !== "" && <div>
        <label htmlFor="pet-breed" className="mb-1.5 block text-sm font-bold text-ink">품종</label>
        <Select id="pet-breed" value={form.breedChoice} onChange={(e) => update("breedChoice", e.target.value)}>
          <option value="">선택 안 함</option>
          {breeds.map((breed) => <option key={breed} value={breed}>{breed}</option>)}
          <option value="OTHER">기타(직접 입력)</option>
        </Select>
      </div>}

      {(form.speciesChoice === "OTHER" || form.breedChoice === "OTHER") && (
        <div>
          <label htmlFor="pet-breed-other" className="mb-1.5 block text-sm font-bold text-ink">품종</label>
          <Input id="pet-breed-other" value={form.breedOther} onChange={(e) => update("breedOther", e.target.value)} maxLength={50} placeholder="품종을 직접 입력해 주세요" />
        </div>
      )}

      <div>
        <label htmlFor="pet-birthDate" className="mb-1.5 block text-sm font-bold text-ink">생년월일</label>
        <Input
          id="pet-birthDate"
          type="date"
          value={form.birthDate}
          max={maxBirthDate()}
          onChange={(event) => update("birthDate", event.target.value)}
          onKeyDown={(event) => clearDateOnDelete(event, form.birthDate, () => update("birthDate", ""))}
        />
      </div>

      <div>
        <label htmlFor="pet-gender" className="mb-1.5 block text-sm font-bold text-ink">성별</label>
        <Select id="pet-gender" value={form.gender} onChange={(e) => update("gender", e.target.value as PetFormValues["gender"])}>
          <option value="">선택 안 함</option>
          <option value="MALE">수컷</option>
          <option value="FEMALE">암컷</option>
        </Select>
      </div>

      <div>
        <label htmlFor="pet-isNeutered" className="mb-1.5 block text-sm font-bold text-ink">중성화 여부</label>
        <Select id="pet-isNeutered" value={form.isNeutered} onChange={(e) => update("isNeutered", e.target.value as PetFormValues["isNeutered"])}>
          <option value="">선택 안 함</option>
          <option value="true">완료</option>
          <option value="false">안 함</option>
        </Select>
      </div>

      <div>
        <label htmlFor="pet-hasAllergy" className="mb-1.5 block text-sm font-bold text-ink">알레르기</label>
        <Select id="pet-hasAllergy" value={form.hasAllergy} onChange={(e) => updateHasAllergy(e.target.value as PetFormValues["hasAllergy"])}>
          <option value="">선택 안 함</option>
          <option value="true">있어요</option>
          <option value="false">없어요</option>
        </Select>
        {allergyLoadFailed && form.hasAllergy === "true" && (
          <p className="mt-2 text-sm text-muted">알레르기 항목을 불러오지 못했어요. 나머지 정보를 먼저 저장하고 다시 시도해 주세요.</p>
        )}
      </div>

      {form.hasAllergy === "true" && allergyTypes.length > 0 && (
        <fieldset className="space-y-4 rounded-button border border-line bg-page p-4">
          <legend className="px-1 text-sm font-bold text-ink">어떤 알레르기가 있나요?</legend>
          {allergyCategoryOrder.map((category) => {
            const typesInCategory = allergyTypes.filter((type) => type.category === category);
            if (typesInCategory.length === 0) return null;
            return (
              <div key={category}>
                <p className="mb-2 text-xs font-bold text-muted">{allergyCategoryLabels[category]}</p>
                <div className="flex flex-wrap gap-2">
                  {typesInCategory.map((type) => {
                    const selected = form.allergyTypeIds.includes(type.allergyTypeId);
                    return (
                      <button
                        key={type.allergyTypeId}
                        type="button"
                        aria-pressed={selected}
                        onClick={() => toggleAllergy(type.allergyTypeId)}
                        className={`rounded-full border px-3 py-1.5 text-xs font-bold transition ${
                          selected ? "border-primary-strong bg-primary-soft text-primary-strong" : "border-line bg-card text-muted hover:bg-card"
                        }`}
                      >
                        {type.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}

          {/* requiresText=true인 항목을 고른 경우에만 직접 입력칸이 붙는다. '기타'를 코드로
              특별 취급하지 않고 마스터의 값에 따라 그려지므로, 나중에 그런 항목이 늘어도
              이 화면은 그대로 동작한다. */}
          {selectedRequiresTextTypes.map((type) => (
            <div key={`other-${type.allergyTypeId}`}>
              <label htmlFor={`pet-allergy-other-${type.allergyTypeId}`} className="mb-1.5 block text-sm font-bold text-ink">
                {type.label} 내용<span className="ml-1 text-primary-strong">*</span>
              </label>
              <Input
                id={`pet-allergy-other-${type.allergyTypeId}`}
                value={form.allergyOtherTexts[type.allergyTypeId] ?? ""}
                onChange={(event) => updateAllergyOtherText(type.allergyTypeId, event.target.value)}
                maxLength={100}
                placeholder="예: 특정 사료 브랜드, 약 성분"
              />
            </div>
          ))}
        </fieldset>
      )}

      {errors.length > 0 && (
        <ul className="space-y-1 rounded-button bg-primary-soft p-3 text-sm font-bold text-primary-strong">
          {errors.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      )}
      {submitError && <p className="text-sm font-bold text-primary-strong">{submitError}</p>}

      <div className="flex justify-end gap-2 pt-2">
        {onCancel && (
          <Button type="button" variant="outline" onClick={onCancel}>
            취소
          </Button>
        )}
        <Button type="submit" disabled={submitting || uploadingImage}>
          {submitting ? "저장 중…" : uploadingImage ? "이미지 업로드 중…" : submitLabel}
        </Button>
      </div>
    </form>
  );
}
