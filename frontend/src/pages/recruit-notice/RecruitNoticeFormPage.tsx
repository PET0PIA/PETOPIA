import { AlertCircle, ArrowLeft, Send } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
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
import { EmptyState } from "../../components/common/EmptyState";
import { useFairSelector } from "../../contexts/FairSelectorContext";

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
  const { fairId: fairIdParam } = useParams<{ fairId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();

  // 사이드바(운영 메뉴)에서 fairId 없이 들어온 경우, 콘솔 상단 바의 "관리 행사" 선택기가 정한 행사를 쓴다
  // (다른 fair-admin 페이지와 동일한 useFairSelector 패턴).
  const { fairId: selectedFairId } = useFairSelector();

  const fairId = fairIdParam ?? (selectedFairId !== null ? String(selectedFairId) : undefined);

  const [form, setForm] = useState<FormState>(initialForm);
  const [existingImageUrl, setExistingImageUrl] = useState<string | null>(null);
  const [imageObjectKey, setImageObjectKey] = useState<string | null>(null);
  const [imageUploading, setImageUploading] = useState(false);
  const [loading, setLoading] = useState(Boolean(fairIdParam));

  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loadedForm, setLoadedForm] = useState<FormState>(initialForm);

  useEffect(() => {
    if (!fairId) return;
    let ignore = false;

    // fairId가 바뀔 때마다 이전 행사의 폼 데이터가 남지 않도록 먼저 초기화한다.
    // (없는 경우 404로 조용히 빠지는 아래 catch 분기 때문에, 초기화 안 하면 이전 행사
    // 내용이 그대로 남아있는 채로 새 행사에 저장해버릴 위험이 있다.)
    setForm(initialForm);
    setLoadedForm(initialForm);
    setExistingImageUrl(null);
    setImageObjectKey(null);

    setLoading(true);
    setLoadError(null);
    getRecruitNotice(Number(fairId))
      .then((notice) => {
        if (ignore) return;
        const loaded = {
          title: notice.title,
          content: notice.content,
          recruitDeadline: notice.recruitDeadline.slice(0, 16),
        };
        setForm(loaded);
        setLoadedForm(loaded);
        setExistingImageUrl(notice.imageUrl);
      })
      .catch((error) => {
        if (ignore) return;
        if (error instanceof ApiError && error.status === 404) return;
        setLoadError(error instanceof ApiError ? error.message : "모집 공고를 불러오지 못했어요.");
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
      await upsertRecruitNotice(Number(fairId), payload);
      navigate(`/fairs/${fairId}/recruit-notice`);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "모집 공고를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  const isDirty =
    form.title !== loadedForm.title ||
    form.content !== loadedForm.content ||
    form.recruitDeadline !== loadedForm.recruitDeadline ||
    imageObjectKey !== null;

  return (
    <PageContainer className="py-10">
      {fairIdParam && (
        <Link to={`/fairs/${fairId}/recruit-notice`} className="mb-4 inline-flex items-center gap-1 text-sm font-bold text-muted hover:text-ink">
          <ArrowLeft size={16} />공고로 돌아가기
        </Link>
      )}

      <PageHeader
        eyebrow="행사 관리자"
        title="참가업체 모집 공고 작성/수정"
        description={fairId ? "*는 필수 입력이에요." : "상단 바에서 관리할 행사를 선택해 주세요."}
      />

      {!fairId && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 모집 공고 작성 화면이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      )}

      {fairId && loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {fairId && !loading && loadError && (
        <EmptyState title="모집 공고 정보를 불러올 수 없어요" description={loadError} />
      )}

      {fairId && !loading && !loadError && (
        <>
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
                key={fairId}
                label="공고 이미지"
                initialImageUrl={existingImageUrl}
                onObjectKeyChange={setImageObjectKey}
                onUploadingChange={setImageUploading}
              />
            </Card>

            <div className="flex justify-end">
              <Button type="submit" disabled={submitting || imageUploading || !user || !isDirty}>
                <Send size={16} />
                {submitting ? "저장 중..." : "저장"}
              </Button>
            </div>
          </form>
        </>
      )}
    </PageContainer>
  );
}