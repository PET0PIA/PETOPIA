import { AlertCircle, Send } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { Textarea } from "../../components/ui/Textarea";
import { ImageUploadField } from "../../components/ui/ImageUploadField";
import { ApiError } from "../../api/client";
import { getRecruitNotice, upsertRecruitNotice, type RecruitNoticeUpsertRequest } from "../../api/recruitNotice";
import { useAuth } from "../../contexts/AuthContext";

interface FormState {
  title: string;
  content: string;
  recruitDeadline: string;
}

const initialForm: FormState = { title: "", content: "", recruitDeadline: "" };

function label(text: string, required = false) {
  return <span className="mb-1.5 block text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</span>;
}

function validate(form: FormState): string[] {
  const errors: string[] = [];
  if (form.title.trim() === "") errors.push("공고 제목을 입력해 주세요.");
  if (form.content.trim() === "") errors.push("공고 본문을 입력해 주세요.");
  if (form.recruitDeadline === "") errors.push("모집 마감일시를 입력해 주세요.");
  return errors;
}

// datetime-local 값(초 없음)에 서버가 기대하는 초 단위를 붙여 보낸다.
function toIsoDateTime(value: string): string {
  return value.length === 16 ? `${value}:00` : value;
}

export function RecruitNoticeFormPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState<FormState>(initialForm);
  const [existingImageUrl, setExistingImageUrl] = useState<string | null>(null);
  const [imageObjectKey, setImageObjectKey] = useState<string | null>(null);
  const [imageUploading, setImageUploading] = useState(false);
  const [loading, setLoading] = useState(true);

  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (!fairId) return;
    let ignore = false;

    getRecruitNotice(Number(fairId))
      .then((notice) => {
        if (ignore) return;
        setForm({
          title: notice.title,
          content: notice.content,
          recruitDeadline: notice.recruitDeadline.slice(0, 16),
        });
        setExistingImageUrl(notice.imageUrl);
      })
      .catch(() => {
        // 아직 공고가 없으면(작성 모드) 빈 폼 그대로 둔다
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId]);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validate(form);
    if (imageUploading) validationErrors.push("이미지 업로드가 끝날 때까지 잠시만 기다려 주세요.");
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;
    if (!fairId || !user) return;

    const payload: RecruitNoticeUpsertRequest = {
      title: form.title.trim(),
      content: form.content.trim(),
      recruitDeadline: toIsoDateTime(form.recruitDeadline),
      imageObjectKey: imageObjectKey ?? undefined,
    };

    setSubmitting(true);
    setSubmitError(null);
    try {
      await upsertRecruitNotice(Number(fairId), payload, user.userId);
      navigate(`/fairs/${fairId}/recruit-notice`);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "모집 공고를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <PageContainer className="py-10"><p className="text-sm text-muted">불러오는 중...</p></PageContainer>;
  }

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="행사 관리자"
        title="참가업체 모집 공고 작성/수정"
        description="*는 필수 입력이에요."
      />

      {errors.length > 0 && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <ul className="space-y-1">{errors.map((message) => <li key={message}>{message}</li>)}</ul>
        </div>
      )}
      {submitError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{submitError}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        <Card className="space-y-5 p-6">
          <div>
            {label("공고 제목", true)}
            <Input value={form.title} onChange={(event) => update("title", event.target.value)} placeholder="예: 2026 멍냥페스타 참가업체 모집" required />
          </div>
          <div>
            {label("공고 본문", true)}
            <Textarea value={form.content} onChange={(event) => update("content", event.target.value)} placeholder="모집 대상, 참가 조건 등을 안내해 주세요." />
          </div>
          <div>
            {label("모집 마감일시", true)}
            <Input type="datetime-local" value={form.recruitDeadline} onChange={(event) => update("recruitDeadline", event.target.value)} required />
          </div>
          <ImageUploadField
            label="공고 이미지"
            initialImageUrl={existingImageUrl}
            onObjectKeyChange={setImageObjectKey}
            onUploadingChange={setImageUploading}
          />
        </Card>

        <div className="flex justify-end">
          <Button type="submit" disabled={submitting || imageUploading}>
            <Send size={16} />
            {submitting ? "저장 중..." : "저장"}
          </Button>
        </div>
      </form>
    </PageContainer>
  );
}