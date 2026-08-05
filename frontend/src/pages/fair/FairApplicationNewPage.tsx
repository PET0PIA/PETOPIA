import { AlertCircle, CheckCircle2, Send } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ImageUploadField } from "../../components/ui/ImageUploadField";
import { Select } from "../../components/ui/Select";
import { Textarea } from "../../components/ui/Textarea";
import { ApiError } from "../../api/client";
import { createFairApplication, type CreateFairApplicationRequest, type CreateFairApplicationResponse } from "../../api/fair";

interface FormState {
  name: string;
  description: string;
  category: "" | "DOG" | "CAT" | "ETC";
  noticeText: string;
  placeName: string;
  address: string;
  indoorOutdoor: "" | "INDOOR" | "OUTDOOR";
  vendorRecruitStartDate: string;
  vendorRecruitEndDate: string;
  reservationStartDate: string;
  reservationEndDate: string;
  operationStartDate: string;
  operationEndDate: string;
  reservationFee: string;
  reservationCancelDeadlineHours: string;
  reservationChangeDeadlineHours: string;
  managerName: string;
  managerPhone: string;
  managerEmail: string;
}

const initialForm: FormState = {
  name: "",
  description: "",
  category: "",
  noticeText: "",
  placeName: "",
  address: "",
  indoorOutdoor: "",
  vendorRecruitStartDate: "",
  vendorRecruitEndDate: "",
  reservationStartDate: "",
  reservationEndDate: "",
  operationStartDate: "",
  operationEndDate: "",
  reservationFee: "",
  reservationCancelDeadlineHours: "",
  reservationChangeDeadlineHours: "",
  managerName: "",
  managerPhone: "",
  managerEmail: "",
};

function label(text: string, required = false) {
  return <span className="mb-1.5 block text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</span>;
}

function validate(form: FormState): string[] {
  const errors: string[] = [];
  if (form.name.trim() === "") errors.push("행사명을 입력해 주세요.");
  if (form.managerName.trim() === "") errors.push("담당자 이름을 입력해 주세요.");
  if (form.managerEmail.trim() === "") errors.push("담당자 이메일을 입력해 주세요.");
  if (form.reservationFee !== "" && Number(form.reservationFee) < 0) errors.push("예약금은 0 이상이어야 해요.");

  const periods: Array<[string, string, string]> = [
    [form.vendorRecruitStartDate, form.vendorRecruitEndDate, "참가업체 모집 기간"],
    [form.reservationStartDate, form.reservationEndDate, "예약 기간"],
    [form.operationStartDate, form.operationEndDate, "운영 기간"],
  ];
  for (const [start, end, periodLabel] of periods) {
    if (start !== "" && end !== "" && end < start) {
      errors.push(`${periodLabel}의 종료일이 시작일보다 빠를 수 없어요.`);
    }
  }
  return errors;
}

function toRequest(form: FormState, posterImageObjectKey: string | null): CreateFairApplicationRequest {
  return {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    category: form.category || undefined,
    posterImageObjectKey: posterImageObjectKey ?? undefined,
    noticeText: form.noticeText.trim() || undefined,
    placeName: form.placeName.trim() || undefined,
    address: form.address.trim() || undefined,
    indoorOutdoor: form.indoorOutdoor || undefined,
    vendorRecruitStartDate: form.vendorRecruitStartDate || undefined,
    vendorRecruitEndDate: form.vendorRecruitEndDate || undefined,
    reservationStartDate: form.reservationStartDate || undefined,
    reservationEndDate: form.reservationEndDate || undefined,
    operationStartDate: form.operationStartDate || undefined,
    operationEndDate: form.operationEndDate || undefined,
    reservationFee: form.reservationFee === "" ? undefined : Number(form.reservationFee),
    reservationCancelDeadlineHours: form.reservationCancelDeadlineHours === "" ? undefined : Number(form.reservationCancelDeadlineHours),
    reservationChangeDeadlineHours: form.reservationChangeDeadlineHours === "" ? undefined : Number(form.reservationChangeDeadlineHours),
    managerName: form.managerName.trim(),
    managerPhone: form.managerPhone.trim() || undefined,
    managerEmail: form.managerEmail.trim(),
  };
}

export function FairApplicationNewPage() {
  const [form, setForm] = useState<FormState>(initialForm);
  const [posterImageObjectKey, setPosterImageObjectKey] = useState<string | null>(null);
  const [posterImageUploading, setPosterImageUploading] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<CreateFairApplicationResponse | null>(null);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validate(form);
    if (posterImageUploading) validationErrors.push("포스터 이미지 업로드가 끝날 때까지 잠시만 기다려 주세요.");
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const response = await createFairApplication(toRequest(form, posterImageObjectKey));
      setResult(response);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "신청서를 제출하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (result) {
    return (
      <PageContainer className="py-10">
        <div className="surface mx-auto max-w-lg p-8 text-center">
          <div className="mx-auto mb-4 grid size-14 place-items-center rounded-full bg-leaf-soft text-ink">
            <CheckCircle2 size={26} />
          </div>
          <h1 className="text-xl font-extrabold">신청이 접수되었어요.</h1>
          <p className="mt-2 text-sm leading-6 text-muted">
            {result.name} 신청서(#{result.fairId})가 접수되었어요. 담당자 검토 후 등록하신 이메일로 안내드릴게요.
          </p>
          <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
            <Link to="/fair-applications/me" className="inline-flex min-h-11 items-center justify-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page">내 행사 신청 목록</Link>
            <Link to="/" className="inline-flex min-h-11 items-center justify-center rounded-button bg-primary-strong px-4 text-sm font-bold text-white hover:opacity-90">홈으로 이동</Link>
          </div>
        </div>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="PETOPIA 행사 개최"
        title="행사 개최를 신청해요"
        description="아래 정보를 입력해 주시면 담당자 검토 후 개설비 결제 안내를 보내드려요. *는 필수 입력이에요."
      />

      {errors.length > 0 && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <ul className="space-y-1">
            {errors.map((message) => <li key={message}>{message}</li>)}
          </ul>
        </div>
      )}

      {submitError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{submitError}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-10">
        <section>
          <SectionHeader title="기본 정보" description="행사를 소개하는 내용이에요." />
          <Card className="space-y-5 p-6">
            <div>
              {label("행사명", true)}
              <Input value={form.name} onChange={(event) => update("name", event.target.value)} placeholder="예: 2026 서울 펫페어" required />
            </div>
            <div className="grid gap-5 sm:grid-cols-2">
              <div>
                {label("카테고리")}
                <Select value={form.category} onChange={(event) => update("category", event.target.value as FormState["category"])}>
                  <option value="">선택 안 함</option>
                  <option value="DOG">강아지</option>
                  <option value="CAT">고양이</option>
                  <option value="ETC">기타</option>
                </Select>
              </div>
              <ImageUploadField
                label="포스터 이미지"
                onObjectKeyChange={setPosterImageObjectKey}
                onUploadingChange={setPosterImageUploading}
              />
            </div>
            <div>
              {label("행사 소개")}
              <Textarea value={form.description} onChange={(event) => update("description", event.target.value)} placeholder="행사를 간단히 소개해 주세요." />
            </div>
            <div>
              {label("유의사항")}
              <Textarea value={form.noticeText} onChange={(event) => update("noticeText", event.target.value)} placeholder="방문객이 꼭 알아야 할 유의사항을 입력해 주세요." />
            </div>
          </Card>
        </section>

        <section>
          <SectionHeader title="장소" description="행사가 열리는 장소 정보예요." />
          <Card className="space-y-5 p-6">
            <div className="grid gap-5 sm:grid-cols-2">
              <div>
                {label("장소명")}
                <Input value={form.placeName} onChange={(event) => update("placeName", event.target.value)} placeholder="예: 서울 코엑스 C홀" />
              </div>
              <div>
                {label("실내/실외")}
                <Select value={form.indoorOutdoor} onChange={(event) => update("indoorOutdoor", event.target.value as FormState["indoorOutdoor"])}>
                  <option value="">선택 안 함</option>
                  <option value="INDOOR">실내</option>
                  <option value="OUTDOOR">실외</option>
                </Select>
              </div>
            </div>
            <div>
              {label("주소")}
              <Input value={form.address} onChange={(event) => update("address", event.target.value)} placeholder="상세 주소를 입력해 주세요." />
            </div>
          </Card>
        </section>

        <section>
          <SectionHeader title="일정" description="모집·예약·운영 기간을 각각 입력해 주세요." />
          <Card className="space-y-5 p-6">
            <div className="grid gap-5 sm:grid-cols-2">
              <div>
                {label("참가업체 모집 시작")}
                <Input type="date" value={form.vendorRecruitStartDate} onChange={(event) => update("vendorRecruitStartDate", event.target.value)} />
              </div>
              <div>
                {label("참가업체 모집 종료")}
                <Input type="date" value={form.vendorRecruitEndDate} onChange={(event) => update("vendorRecruitEndDate", event.target.value)} />
              </div>
              <div>
                {label("사전예약 시작")}
                <Input type="date" value={form.reservationStartDate} onChange={(event) => update("reservationStartDate", event.target.value)} />
              </div>
              <div>
                {label("사전예약 종료")}
                <Input type="date" value={form.reservationEndDate} onChange={(event) => update("reservationEndDate", event.target.value)} />
              </div>
              <div>
                {label("행사 운영 시작")}
                <Input type="date" value={form.operationStartDate} onChange={(event) => update("operationStartDate", event.target.value)} />
              </div>
              <div>
                {label("행사 운영 종료")}
                <Input type="date" value={form.operationEndDate} onChange={(event) => update("operationEndDate", event.target.value)} />
              </div>
            </div>
          </Card>
        </section>

        <section>
          <SectionHeader title="예약 정책" description="관람객 예약금과 취소·변경 가능 기한이에요." />
          <Card className="space-y-5 p-6">
            <div className="grid gap-5 sm:grid-cols-3">
              <div>
                {label("예약금(원)")}
                <Input type="number" min={0} value={form.reservationFee} onChange={(event) => update("reservationFee", event.target.value)} placeholder="0" />
              </div>
              <div>
                {label("취소 가능 기한(시간)")}
                <Input type="number" min={0} value={form.reservationCancelDeadlineHours} onChange={(event) => update("reservationCancelDeadlineHours", event.target.value)} />
              </div>
              <div>
                {label("변경 가능 기한(시간)")}
                <Input type="number" min={0} value={form.reservationChangeDeadlineHours} onChange={(event) => update("reservationChangeDeadlineHours", event.target.value)} />
              </div>
            </div>
          </Card>
        </section>

        <section>
          <SectionHeader title="담당자 정보" description="심사 결과와 개설비 결제 안내를 받을 연락처예요." />
          <Card className="space-y-5 p-6">
            <div className="grid gap-5 sm:grid-cols-3">
              <div>
                {label("담당자 이름", true)}
                <Input value={form.managerName} onChange={(event) => update("managerName", event.target.value)} required />
              </div>
              <div>
                {label("담당자 연락처")}
                <Input value={form.managerPhone} onChange={(event) => update("managerPhone", event.target.value)} placeholder="010-0000-0000" />
              </div>
              <div>
                {label("담당자 이메일", true)}
                <Input type="email" value={form.managerEmail} onChange={(event) => update("managerEmail", event.target.value)} required />
              </div>
            </div>
          </Card>
        </section>

        <div className="flex justify-end">
          <Button type="submit" disabled={submitting || posterImageUploading}>
            <Send size={16} />
            {submitting ? "제출 중..." : posterImageUploading ? "이미지 업로드 중..." : "신청서 제출"}
          </Button>
        </div>
      </form>
    </PageContainer>
  );
}
