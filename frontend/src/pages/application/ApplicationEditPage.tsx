import { AlertCircle, ChevronLeft, Paperclip, Send } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { Textarea } from "../../components/ui/Textarea";
import { AttachmentUploadField } from "../../components/ui/AttachmentUploadField";
import { ApiError } from "../../api/client";
import { getApplicationDetail, updateApplication, type ApplicationDetail } from "../../api/application";

interface FormState {
  purpose: string;
  itemsDesc: string;
  managerName: string;
  managerPhone: string;
  managerEmail: string;
}

function label(htmlFor: string, text: string, required = false) {
  return <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</label>;
}

function validate(form: FormState): string[] {
  const errors: string[] = [];
  if (form.purpose.trim() === "") errors.push("참가 목적을 입력해 주세요.");
  else if (form.purpose.length > 200) errors.push("참가 목적은 200자 이하로 입력해 주세요.");
  if (form.itemsDesc.trim() === "") errors.push("판매·전시 품목을 입력해 주세요.");
  else if (form.itemsDesc.length > 500) errors.push("판매·전시 품목은 500자 이하로 입력해 주세요.");
  if (form.managerName.trim() === "") errors.push("신청 담당자명을 입력해 주세요.");
  else if (form.managerName.length > 50) errors.push("신청 담당자명은 50자 이하로 입력해 주세요.");
  if (form.managerPhone.trim() === "") errors.push("신청 담당자 연락처를 입력해 주세요.");
  else if (form.managerPhone.length > 20) errors.push("신청 담당자 연락처는 20자 이하로 입력해 주세요.");
  if (form.managerEmail.trim() === "") errors.push("신청 담당자 이메일을 입력해 주세요.");
  else if (form.managerEmail.length > 100) errors.push("신청 담당자 이메일은 100자 이하로 입력해 주세요.");
  return errors;
}

export function ApplicationEditPage() {
  const { applicationId } = useParams<{ applicationId: string }>();
  const id = Number(applicationId);
  const idValid = Number.isInteger(id) && id > 0;

  if (!idValid) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="잘못된 신청서 주소예요." description="신청서 주소가 올바르지 않아요." actionTo="/participations/me" actionLabel="참가 신청 현황으로" />
      </PageContainer>
    );
  }

  return <ApplicationEditContent key={id} id={id} />;
}

function ApplicationEditContent({ id }: { id: number }) {
  const navigate = useNavigate();

  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [form, setForm] = useState<FormState>({ purpose: "", itemsDesc: "", managerName: "", managerPhone: "", managerEmail: "" });
  const [attachmentObjectKey, setAttachmentObjectKey] = useState<string | undefined>(undefined);
  const [attachmentUploading, setAttachmentUploading] = useState(false);

  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const errorsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (errors.length > 0) {
      errorsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [errors]);

  useEffect(() => {
    let alive = true;
    getApplicationDetail(id)
      .then((res) => {
        if (!alive) return;
        setDetail(res);
        setForm({
          purpose: res.purpose,
          itemsDesc: res.itemsDesc,
          managerName: res.managerName,
          managerPhone: res.managerPhone,
          managerEmail: res.managerEmail,
        });
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "신청서를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [id]);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validate(form);
    if (attachmentUploading) validationErrors.push("첨부파일 업로드가 끝날 때까지 잠시만 기다려 주세요.");
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      await updateApplication(id, {
        purpose: form.purpose.trim(),
        itemsDesc: form.itemsDesc.trim(),
        managerName: form.managerName.trim(),
        managerPhone: form.managerPhone.trim(),
        managerEmail: form.managerEmail.trim(),
        attachmentObjectKey,
      });
      navigate(`/participations/me/${id}`);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "신청서를 수정하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <PageContainer className="py-10"><p className="text-sm text-muted">불러오는 중...</p></PageContainer>;
  }

  if (loadError || !detail) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="신청서를 찾을 수 없어요." description={loadError ?? "목록에서 신청서를 다시 선택해 주세요."} actionTo="/participations/me" actionLabel="참가 신청 현황으로" />
      </PageContainer>
    );
  }

  if (detail.status !== "PENDING_REVIEW") {
    return (
      <PageContainer className="py-10">
        <EmptyState
          title="지금은 수정할 수 없어요."
          description="심사 대기 중인 신청서만 수정할 수 있어요."
          actionTo={`/participations/me/${id}`}
          actionLabel="신청서 상세로"
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <Link to={`/participations/me/${id}`} className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink">
        <ChevronLeft size={16} />
        신청서 상세로
      </Link>

      <div className="mt-4">
        <PageHeader eyebrow={detail.businessName} title="참가 신청서 수정" description="부스 슬롯은 수정할 수 없어요. 슬롯을 바꾸려면 취소 요청 후 다시 신청해 주세요." />
      </div>

      {errors.length > 0 && (
        <div ref={errorsRef} className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong scroll-mt-[88px]">
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
          <SectionHeader title="신청 내용" description="심사에 참고할 참가 목적과 판매·전시 품목이에요." />
          <Card className="space-y-5 p-6">
            <div>
              {label("purpose", "참가 목적", true)}
              <Textarea id="purpose" value={form.purpose} onChange={(event) => update("purpose", event.target.value)} maxLength={200} />
            </div>
            <div>
              {label("itemsDesc", "판매·전시 품목", true)}
              <Textarea id="itemsDesc" value={form.itemsDesc} onChange={(event) => update("itemsDesc", event.target.value)} maxLength={500} />
            </div>
            {detail.attachmentUrl && (
              <p className="text-sm text-muted">
                기존 첨부파일:{" "}
                <a href={detail.attachmentUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 font-bold text-primary-strong hover:underline">
                  <Paperclip size={14} />
                  보기
                </a>
              </p>
            )}
            <AttachmentUploadField
              label="첨부파일 교체 (선택, 새로 올리면 기존 파일을 대체해요)"
              onObjectKeyChange={(key) => setAttachmentObjectKey(key ?? undefined)}
              onUploadingChange={setAttachmentUploading}
            />
          </Card>
        </section>

        <section>
          <SectionHeader title="담당자 정보" description="심사 결과와 결제 안내를 받을 연락처예요." />
          <Card className="space-y-5 p-6">
            <div className="grid gap-5 sm:grid-cols-3">
              <div>
                {label("managerName", "담당자 이름", true)}
                <Input id="managerName" value={form.managerName} onChange={(event) => update("managerName", event.target.value)} maxLength={50} required />
              </div>
              <div>
                {label("managerPhone", "담당자 연락처", true)}
                <Input
                  id="managerPhone"
                  type="tel"
                  value={form.managerPhone}
                  onChange={(event) => update("managerPhone", event.target.value)}
                  placeholder="010-0000-0000 (- 없이 입력 가능)"
                  pattern="\d{2,3}-?\d{3,4}-?\d{4}"
                  maxLength={20}
                  required
                />
                <p className="mt-1 text-xs text-muted">- 없이 숫자만 입력해도 돼요.</p>
              </div>
              <div>
                {label("managerEmail", "담당자 이메일", true)}
                <Input id="managerEmail" type="email" value={form.managerEmail} onChange={(event) => update("managerEmail", event.target.value)} maxLength={100} required />
                <p className="mt-1 text-xs text-muted">참가 신청 승인·반려 등 알림 메일이 이 주소로 발송돼요.</p>
              </div>
            </div>
          </Card>
        </section>

        <div className="flex justify-end">
          <Button type="submit" disabled={submitting || attachmentUploading}>
            <Send size={16} />
            {submitting ? "저장 중..." : attachmentUploading ? "파일 업로드 중..." : "수정 완료"}
          </Button>
        </div>
      </form>
    </PageContainer>
  );
}