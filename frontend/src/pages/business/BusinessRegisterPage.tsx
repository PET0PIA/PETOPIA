import { AlertCircle, CheckCircle2, Send } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ApiError } from "../../api/client";
import { registerBusiness, type Business, type BusinessRegisterRequest } from "../../api/business";
import { useAuth } from "../../contexts/AuthContext";

interface FormState {
  name: string;
  ceoName: string;
  bizRegNo: string;
  startDate: string;
  address: string;
  phone: string;
  website: string;
}

const initialForm: FormState = {
  name: "",
  ceoName: "",
  bizRegNo: "",
  startDate: "",
  address: "",
  phone: "",
  website: "",
};

function label(text: string, required = false) {
  return <span className="mb-1.5 block text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</span>;
}

function validate(form: FormState): string[] {
  const errors: string[] = [];
  if (form.name.trim() === "") errors.push("업체명을 입력해 주세요.");
  if (form.ceoName.trim() === "") errors.push("대표자명을 입력해 주세요.");
  if (!/^\d{10}$/.test(form.bizRegNo.trim())) errors.push("사업자등록번호는 숫자 10자리로 입력해 주세요.");
  if (form.startDate.trim() === "") errors.push("개업일자를 입력해 주세요.");
  if (form.address.trim() === "") errors.push("사업장 주소를 입력해 주세요.");
  if (form.phone.trim() === "") errors.push("연락처를 입력해 주세요.");
  return errors;
}

function toRequest(form: FormState): BusinessRegisterRequest {
  return {
    name: form.name.trim(),
    ceoName: form.ceoName.trim(),
    bizRegNo: form.bizRegNo.trim(),
    startDate: form.startDate,
    address: form.address.trim(),
    phone: form.phone.trim(),
    website: form.website.trim() || undefined,
  };
}

export function BusinessRegisterPage() {
  const { user } = useAuth();
  const [form, setForm] = useState<FormState>(initialForm);
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<Business | null>(null);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validate(form);
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;
    if (!user) {
      setSubmitError("로그인 후 이용할 수 있어요.");
      return;
    }

    setSubmitting(true);
    setSubmitError(null);
    try {
      const response = await registerBusiness(toRequest(form), user.userId);
      setResult(response);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "사업자 등록에 실패했어요. 잠시 후 다시 시도해 주세요.");
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
          <h1 className="text-xl font-extrabold">사업자 등록이 완료됐어요.</h1>
          <p className="mt-2 text-sm leading-6 text-muted">
            {result.name}(#{result.businessId})의 진위확인이 완료됐어요. 이제 부스 참가 신청을 진행할 수 있어요.
          </p>
          <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
            <Link to="/participations/new" className="inline-flex min-h-11 items-center justify-center rounded-button bg-primary-strong px-4 text-sm font-bold text-white hover:opacity-90">참여 부스 신청하기</Link>
            <Link to="/" className="inline-flex min-h-11 items-center justify-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page">홈으로 이동</Link>
          </div>
        </div>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="PETOPIA 참여 업체"
        title="사업자 등록을 신청해요"
        description="국세청 진위확인을 거쳐 바로 승인돼요. *는 필수 입력이에요."
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
          <Card className="space-y-5 p-6">
            <div className="grid gap-5 sm:grid-cols-2">
              <div>
                {label("업체명", true)}
                <Input value={form.name} onChange={(event) => update("name", event.target.value)} placeholder="예: 멍냥사료" required />
              </div>
              <div>
                {label("대표자명", true)}
                <Input value={form.ceoName} onChange={(event) => update("ceoName", event.target.value)} required />
              </div>
              <div>
                {label("사업자등록번호", true)}
                <Input value={form.bizRegNo} onChange={(event) => update("bizRegNo", event.target.value)} placeholder="숫자 10자리" required />
              </div>
              <div>
                {label("개업일자", true)}
                <Input type="date" value={form.startDate} onChange={(event) => update("startDate", event.target.value)} required />
              </div>
              <div>
                {label("연락처", true)}
                <Input value={form.phone} onChange={(event) => update("phone", event.target.value)} placeholder="010-0000-0000" required />
              </div>
              <div>
                {label("웹사이트")}
                <Input value={form.website} onChange={(event) => update("website", event.target.value)} placeholder="https://" />
              </div>
            </div>
            <div>
              {label("사업장 주소", true)}
              <Input value={form.address} onChange={(event) => update("address", event.target.value)} required />
            </div>
          </Card>
        </section>

        <div className="flex justify-end">
          <Button type="submit" disabled={submitting}>
            <Send size={16} />
            {submitting ? "등록 중..." : "사업자 등록"}
          </Button>
        </div>
      </form>
    </PageContainer>
  );
}