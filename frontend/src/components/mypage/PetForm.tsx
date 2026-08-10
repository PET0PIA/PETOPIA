import { useState, type FormEvent } from "react";
import { Button } from "../ui/Button";
import { ImageUploadField } from "../ui/ImageUploadField";
import { Input } from "../ui/Input";
import { Select } from "../ui/Select";
import { maxBirthDate } from "../../utils/date";
import { emptyPetForm, toPetRequest, validatePetForm, type PetFormValues } from "./petFormValues";
import type { PetRequest } from "../../api/pet";

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

  function update<K extends keyof PetFormValues>(key: K, value: PetFormValues[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validatePetForm(form);
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;
    onSubmit(toPetRequest(form));
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
        <Input id="pet-species" value={form.species} onChange={(e) => update("species", e.target.value)} maxLength={20} placeholder="예: 강아지, 고양이, 햄스터" />
      </div>

      <div>
        <label htmlFor="pet-breed" className="mb-1.5 block text-sm font-bold text-ink">품종</label>
        <Input id="pet-breed" value={form.breed} onChange={(e) => update("breed", e.target.value)} maxLength={50} placeholder="예: 말티즈" />
      </div>

      <div>
        <label htmlFor="pet-birthDate" className="mb-1.5 block text-sm font-bold text-ink">생년월일</label>
        <Input id="pet-birthDate" type="date" value={form.birthDate} max={maxBirthDate()} onChange={(e) => update("birthDate", e.target.value)} />
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
