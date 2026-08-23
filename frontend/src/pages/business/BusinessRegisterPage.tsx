import { AlertCircle, Send } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { AttachmentUploadField } from "../../components/ui/AttachmentUploadField";
import { ApiError } from "../../api/client";
import { registerBusiness, type BusinessRegisterRequest } from "../../api/business";
import { useAuth } from "../../contexts/AuthContext";
import { BusinessTermsAgreement } from "../../components/business/BusinessTermsAgreement";
import { DateInput } from "../../components/ui/DateInput";

interface FormState {
  name: string;
  ceoName: string;
  bizRegNo: string;
  startDate: string;
  address: string;
  phone: string;
  website: string;
  agreedTerms: boolean;
  agreedPrivacy: boolean;
}

const initialForm: FormState = {
  name: "",
  ceoName: "",
  bizRegNo: "",
  startDate: "",
  address: "",
  phone: "",
  website: "",
  agreedTerms: false,
  agreedPrivacy: false,
};

function label(text: string, required = false) {
  return <span className="mb-1.5 block text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</span>;
}

function validate(form: FormState, documentObjectKey: string | null): string[] {
  const errors: string[] = [];
  if (form.name.trim() === "") errors.push("업체명을 입력해 주세요.");
  if (form.ceoName.trim() === "") errors.push("대표자명을 입력해 주세요.");
  if (!/^\d{10}$/.test(form.bizRegNo.trim())) errors.push("사업자등록번호는 숫자 10자리로 입력해 주세요.");
  if (form.startDate.trim() === "") errors.push("개업일자를 입력해 주세요.");
  if (form.address.trim() === "") errors.push("사업장 주소를 입력해 주세요.");
  if (form.phone.trim() === "") errors.push("연락처를 입력해 주세요.");
  if (!documentObjectKey) errors.push("사업자등록증을 첨부해 주세요.");
  if (!form.agreedTerms) errors.push("사업자 등록 이용약관에 동의해 주세요.");
  if (!form.agreedPrivacy) errors.push("개인정보 수집·이용에 동의해 주세요.");
  return errors;
}

function toRequest(form: FormState, documentObjectKey: string): BusinessRegisterRequest {
  return {
    name: form.name.trim(),
    ceoName: form.ceoName.trim(),
    bizRegNo: form.bizRegNo.trim(),
    startDate: form.startDate,
    address: form.address.trim(),
    phone: form.phone.trim(),
    website: form.website.trim() || undefined,
    businessRegDocKey: documentObjectKey,
    agreedTerms: form.agreedTerms,
    agreedPrivacy: form.agreedPrivacy, // 추가
  };
}

export function BusinessRegisterPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  // 다른 화면(예: 참가 신청서에서 사업자 미등록)에서 넘어왔으면 등록 후 그 화면으로 돌려보낼 경로.
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? null;
  const [form, setForm] = useState<FormState>(initialForm);
  const [documentObjectKey, setDocumentObjectKey] = useState<string | null>(null);
  const [documentUploading, setDocumentUploading] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  if (user?.role === "EVENT_ADMIN") {
    return (
      <PageContainer className="py-10">
        <div className="surface mx-auto max-w-lg p-8 text-center">
          <AlertCircle className="mx-auto text-muted" size={36} />
          <h1 className="mt-4 text-xl font-extrabold text-ink">사업자를 등록할 수 없는 계정이에요</h1>
          <p className="mt-2 text-sm leading-6 text-muted">
            행사 관리자와 참가업체 권한은 동시에 사용할 수 없어 사업자 등록을 신청할 수 없어요.
          </p>
          <Button type="button" className="mt-6" onClick={() => navigate("/")}>
            홈으로 이동
          </Button>
        </div>
      </PageContainer>
    );
  }

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validate(form, documentObjectKey);
    if (documentUploading) validationErrors.push("첨부파일 업로드가 끝날 때까지 잠시만 기다려 주세요.");
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;
    if (!user) {
      setSubmitError("로그인 후 이용할 수 있어요.");
      return;
    }
    if (!documentObjectKey) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const response = await registerBusiness(toRequest(form, documentObjectKey));
      if (from) {
        // 참가 신청 등에서 넘어온 경우: 등록을 마쳤으니 원래 화면(신청서)으로 돌려보낸다.
        // 신청서가 다시 마운트되며 사업자 목록을 새로 불러오므로 방금 등록한 사업자로 바로 신청할 수 있다.
        navigate(from, { replace: true });
      } else {
        // 일반 진입: 등록 성공 시 바로 상세 페이지로 이동해서 심사 대기 안내를 보여준다.
        navigate(`/businesses/${response.businessId}`, { state: { justRegistered: true } });
      }
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "사업자 등록에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="PETOPIA 참여 업체"
        title="사업자 등록을 신청해요"
        description="국세청 진위확인 후 사업자등록증을 첨부해 신청하면, 관리자 심사 후 승인돼요. *는 필수 입력이에요."
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
                <DateInput value={form.startDate} onChange={(value) => update("startDate", value)} required />
                <p className="mt-1 text-xs text-muted">숫자만 입력하면 자동으로 - 가 붙어요.</p>
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
            <AttachmentUploadField
              label="사업자등록증 *"
              onObjectKeyChange={setDocumentObjectKey}
              onUploadingChange={setDocumentUploading}
            />
          </Card>
        </section>

        <BusinessTermsAgreement
          agreedTerms={form.agreedTerms}
          agreedPrivacy={form.agreedPrivacy}
          onTermsChange={(checked) => update("agreedTerms", checked)}
          onPrivacyChange={(checked) => update("agreedPrivacy", checked)}
        />

        <div className="flex justify-end">
          <Button type="submit" disabled={submitting || documentUploading}>
            <Send size={16} />
            {submitting ? "등록 중..." : "사업자 등록"}
          </Button>
        </div>
      </form>
    </PageContainer>
  );
}
