import { AlertCircle, CalendarDays, CheckCircle2, FileText, MapPin, Send, Ticket, UserRound } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
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
import { HelpTip } from "../../components/ui/HelpTip";
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

function label(htmlFor: string, text: string, required = false, help?: string) {
  return (
    <div className="mb-1.5 flex items-center gap-1">
      <label htmlFor={htmlFor} className="text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</label>
      {help && <HelpTip text={help} />}
    </div>
  );
}

/** 일정의 한 기간: 제목 + [시작] ~ [종료]를 한 줄로. 날짜칸을 넉넉히 두고 반복 라벨을 없애 읽기 쉽게 한다. */
function PeriodFields({ title, startId, startValue, endId, endValue, onStart, onEnd }: {
  title: string;
  startId: string;
  startValue: string;
  endId: string;
  endValue: string;
  onStart: (value: string) => void;
  onEnd: (value: string) => void;
}) {
  return (
    <div>
      <p className="mb-1.5 text-sm font-bold text-ink">{title}</p>
      <div className="flex max-w-md items-center gap-3">
        <Input id={startId} type="date" aria-label={`${title} 시작일`} value={startValue} onChange={(event) => onStart(event.target.value)} className="min-w-0 flex-1" />
        <span className="shrink-0 text-sm text-muted" aria-hidden="true">~</span>
        <Input id={endId} type="date" aria-label={`${title} 종료일`} value={endValue} onChange={(event) => onEnd(event.target.value)} className="min-w-0 flex-1" />
      </div>
    </div>
  );
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
  const errorsRef = useRef<HTMLDivElement>(null);

  // 폼이 길어, 하단 제출 버튼에서 눌렀을 때 위쪽 오류를 놓칠 수 있다.
  // 검증 오류나 서버 오류가 생기면 오류 박스로 스크롤해 준다.
  useEffect(() => {
    if (errors.length > 0 || submitError) {
      errorsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [errors, submitError]);

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
     <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="PETOPIA 행사 개최"
        title="행사 개최를 신청해요"
        description="아래 정보를 입력해 주시면 담당자 검토 후 개설비 결제 안내를 보내드려요. *는 필수 입력이에요."
      />

      {/* 스크롤 목표 지점. 검증/서버 오류가 뜨면 위 useEffect가 이 영역으로 데려온다. */}
      <div ref={errorsRef} className="scroll-mt-24">
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
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        <section>
          <SectionHeader title="기본 정보" help="행사를 소개하는 내용이에요." icon={<FileText size={18} aria-hidden="true" />} />
          <Card className="p-6">
            <div className="flex flex-col gap-6 sm:flex-row sm:gap-8">
              {/* 포스터를 크게 왼쪽에 두고, 오른쪽 입력칸은 짧게 묶어 높이를 맞춘다. */}
              <div className="shrink-0">
                <ImageUploadField
                  label="포스터 이미지"
                  previewClassName="aspect-[4/5] w-56"
                  layout="stacked"
                  onObjectKeyChange={setPosterImageObjectKey}
                  onUploadingChange={setPosterImageUploading}
                />
              </div>
              <div className="flex-1 space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    {label("name", "행사명", true)}
                    <Input id="name" value={form.name} onChange={(event) => update("name", event.target.value)} placeholder="예: 2026 서울 펫페어" required />
                  </div>
                  <div>
                    {label("category", "카테고리")}
                    <Select id="category" value={form.category} onChange={(event) => update("category", event.target.value as FormState["category"])}>
                      <option value="">선택 안 함</option>
                      <option value="DOG">강아지</option>
                      <option value="CAT">고양이</option>
                      <option value="ETC">기타</option>
                    </Select>
                  </div>
                </div>
                <div>
                  {label("description", "행사 소개")}
                  <Textarea id="description" value={form.description} onChange={(event) => update("description", event.target.value)} placeholder="행사를 소개해 주세요." />
                </div>
                <div>
                  {label("noticeText", "관람 안내사항")}
                  <Textarea id="noticeText" value={form.noticeText} onChange={(event) => update("noticeText", event.target.value)} placeholder="방문객이 꼭 알아야 할 관람 안내사항을 입력해 주세요." />
                </div>
              </div>
            </div>
          </Card>
        </section>

        <section>
          <SectionHeader title="장소" help="행사가 열리는 장소 정보예요." icon={<MapPin size={18} aria-hidden="true" />} />
          <Card className="space-y-5 p-6">
            <div className="grid gap-5 sm:grid-cols-2">
              <div>
                {label("placeName", "장소명")}
                <Input id="placeName" value={form.placeName} onChange={(event) => update("placeName", event.target.value)} placeholder="예: 서울 코엑스 C홀" />
              </div>
              <div>
                {label("indoorOutdoor", "실내/실외")}
                <Select id="indoorOutdoor" value={form.indoorOutdoor} onChange={(event) => update("indoorOutdoor", event.target.value as FormState["indoorOutdoor"])}>
                  <option value="">선택 안 함</option>
                  <option value="INDOOR">실내</option>
                  <option value="OUTDOOR">실외</option>
                </Select>
              </div>
            </div>
            <div>
              {label("address", "주소")}
              <Input id="address" value={form.address} onChange={(event) => update("address", event.target.value)} placeholder="상세 주소를 입력해 주세요." />
            </div>
          </Card>
        </section>

        <section>
          <SectionHeader title="일정" help="아는 기간만 입력해도 돼요. 보통 참가업체 모집 → 관람객 사전예약 → 행사 운영 순서로 진행돼요." icon={<CalendarDays size={18} aria-hidden="true" />} />
          <Card className="space-y-5 p-6">
            <PeriodFields
              title="참가업체 모집"
              startId="vendorRecruitStartDate"
              startValue={form.vendorRecruitStartDate}
              endId="vendorRecruitEndDate"
              endValue={form.vendorRecruitEndDate}
              onStart={(value) => update("vendorRecruitStartDate", value)}
              onEnd={(value) => update("vendorRecruitEndDate", value)}
            />
            <PeriodFields
              title="관람객 사전예약"
              startId="reservationStartDate"
              startValue={form.reservationStartDate}
              endId="reservationEndDate"
              endValue={form.reservationEndDate}
              onStart={(value) => update("reservationStartDate", value)}
              onEnd={(value) => update("reservationEndDate", value)}
            />
            <PeriodFields
              title="행사 운영"
              startId="operationStartDate"
              startValue={form.operationStartDate}
              endId="operationEndDate"
              endValue={form.operationEndDate}
              onStart={(value) => update("operationStartDate", value)}
              onEnd={(value) => update("operationEndDate", value)}
            />
          </Card>
        </section>

        {/* 티켓 정책·담당자는 둘 다 짧아 나란히 둔다(높이가 비슷해 깔끔). 좁은 화면에선 위아래로 쌓인다. */}
        <div className="grid gap-6 lg:grid-cols-2 lg:items-start">
          <section>
            <SectionHeader title="티켓 정책" help="관람객 티켓 비용과 취소·변경 가능 기한이에요." icon={<Ticket size={18} aria-hidden="true" />} />
            <Card className="space-y-5 p-6">
              <div>
                {label("reservationFee", "티켓 비용(원)", false, "관람객이 예매 때 내는 티켓 비용이에요. 무료면 0.")}
                <Input id="reservationFee" type="number" min={0} value={form.reservationFee} onChange={(event) => update("reservationFee", event.target.value)} placeholder="0" />
              </div>
              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  {label("reservationCancelDeadlineHours", "취소 가능 기한(시간)", false, "방문 몇 시간 전까지 취소를 허용할지. 예: 24 = 하루 전까지.")}
                  <Input id="reservationCancelDeadlineHours" type="number" min={0} value={form.reservationCancelDeadlineHours} onChange={(event) => update("reservationCancelDeadlineHours", event.target.value)} placeholder="예: 24" />
                </div>
                <div>
                  {label("reservationChangeDeadlineHours", "변경 가능 기한(시간)", false, "방문 몇 시간 전까지 방문일 변경을 허용할지.")}
                  <Input id="reservationChangeDeadlineHours" type="number" min={0} value={form.reservationChangeDeadlineHours} onChange={(event) => update("reservationChangeDeadlineHours", event.target.value)} placeholder="예: 24" />
                </div>
              </div>
            </Card>
          </section>

          <section>
            <SectionHeader title="담당자 정보" help="심사 결과와 개설비 결제 안내를 받을 연락처예요." icon={<UserRound size={18} aria-hidden="true" />} />
            <Card className="space-y-5 p-6">
              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  {label("managerName", "담당자 이름", true)}
                  <Input id="managerName" value={form.managerName} onChange={(event) => update("managerName", event.target.value)} required />
                </div>
                <div>
                  {label("managerPhone", "담당자 연락처")}
                  <Input id="managerPhone" value={form.managerPhone} onChange={(event) => update("managerPhone", event.target.value)} placeholder="010-0000-0000" />
                </div>
              </div>
              <div>
                {label("managerEmail", "담당자 이메일", true)}
                <Input id="managerEmail" type="email" value={form.managerEmail} onChange={(event) => update("managerEmail", event.target.value)} required />
              </div>
            </Card>
          </section>
        </div>

        <div className="flex flex-col gap-3 border-t border-line pt-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-xs leading-5 text-muted">
            제출하면 담당자 검토 후 등록하신 이메일로 결과를 안내드려요.
          </p>
          <Button type="submit" disabled={submitting || posterImageUploading} className="w-full sm:w-auto">
            <Send size={16} />
            {submitting ? "제출 중..." : posterImageUploading ? "이미지 업로드 중..." : "신청서 제출"}
          </Button>
        </div>
      </form>
     </div>
    </PageContainer>
  );
}
