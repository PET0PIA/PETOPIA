import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { today } from "../../utils/date";
import { ApiError } from "../../api/client";
import { getMe, updateMe, type UpdateUserRequest, type UserMe } from "../../api/user";

interface FormState {
  nickname: string;
  birthDate: string;
  phone: string;
  gender: "남성" | "여성";
  address: string;
}

const PHONE_PATTERN = /^01[0-9]-?\d{3,4}-?\d{4}$/;

function toFormState(me: UserMe): FormState {
  return { nickname: me.nickname, birthDate: me.birthDate, phone: me.phone, gender: me.gender, address: me.address };
}

function validate(form: FormState): string[] {
  const errors: string[] = [];
  if (form.nickname.trim() === "") errors.push("닉네임을 입력해 주세요.");
  if (form.birthDate !== "" && form.birthDate >= today()) errors.push("생년월일은 오늘 이전 날짜여야 해요.");
  if (!PHONE_PATTERN.test(form.phone)) errors.push("휴대폰 번호 형식이 올바르지 않아요. (예: 010-1234-5678)");
  if (form.address.trim() === "") errors.push("주소를 입력해 주세요.");
  return errors;
}

/**
 * 바뀐 필드만 담아 PATCH 요청을 만든다. 서버는 안 보낸 필드를 그대로 두는(null=미변경)
 * 방식이라, 여기서 안 바뀐 값을 걸러내면 "내가 뭘 바꿨는지"가 요청에 그대로 드러나고
 * 트래픽도 줄어든다.
 */
function diff(original: FormState, form: FormState): UpdateUserRequest {
  const payload: UpdateUserRequest = {};
  if (form.nickname !== original.nickname) payload.nickname = form.nickname.trim();
  if (form.birthDate !== original.birthDate) payload.birthDate = form.birthDate;
  if (form.phone !== original.phone) payload.phone = form.phone;
  if (form.gender !== original.gender) payload.gender = form.gender;
  if (form.address !== original.address) payload.address = form.address.trim();
  return payload;
}

export function EditProfilePage() {
  const navigate = useNavigate();
  const [original, setOriginal] = useState<FormState | null>(null);
  const [form, setForm] = useState<FormState | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getMe()
      .then((me) => {
        if (!alive) return;
        setOriginal(toFormState(me));
        setForm(toFormState(me));
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "내 정보를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => (previous ? { ...previous, [key]: value } : previous));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form || !original) return;

    const validationErrors = validate(form);
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;

    const payload = diff(original, form);
    if (Object.keys(payload).length === 0) {
      // 바뀐 게 없으면 요청을 아예 안 보낸다.
      navigate("/mypage");
      return;
    }

    setSubmitting(true);
    setSubmitError(null);
    try {
      await updateMe(payload);
      navigate("/mypage");
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "정보 수정에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <PageContainer className="py-10">
        <p className="py-16 text-center text-sm text-muted">불러오는 중이에요…</p>
      </PageContainer>
    );
  }

  if (loadError || !form) {
    return (
      <PageContainer className="py-10">
        <p className="py-16 text-center text-sm text-muted">{loadError ?? "내 정보를 불러오지 못했어요."}</p>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="마이페이지" title="내 정보 수정" description="바뀐 항목만 저장돼요." />
      <Card className="mx-auto max-w-lg p-8">
        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label htmlFor="profile-nickname" className="mb-1.5 block text-sm font-bold text-ink">
              닉네임<span className="ml-1 text-primary-strong">*</span>
            </label>
            <Input id="profile-nickname" value={form.nickname} maxLength={50} onChange={(e) => update("nickname", e.target.value)} />
          </div>
          <div>
            <label htmlFor="profile-birthDate" className="mb-1.5 block text-sm font-bold text-ink">생년월일</label>
            <Input id="profile-birthDate" type="date" value={form.birthDate} max={today()} onChange={(e) => update("birthDate", e.target.value)} />
          </div>
          <div>
            <label htmlFor="profile-phone" className="mb-1.5 block text-sm font-bold text-ink">
              휴대폰 번호<span className="ml-1 text-primary-strong">*</span>
            </label>
            <Input id="profile-phone" value={form.phone} onChange={(e) => update("phone", e.target.value)} placeholder="010-1234-5678" />
          </div>
          <div>
            <label htmlFor="profile-gender" className="mb-1.5 block text-sm font-bold text-ink">성별</label>
            <Select id="profile-gender" value={form.gender} onChange={(e) => update("gender", e.target.value as FormState["gender"])}>
              <option value="남성">남성</option>
              <option value="여성">여성</option>
            </Select>
          </div>
          <div>
            <label htmlFor="profile-address" className="mb-1.5 block text-sm font-bold text-ink">
              주소<span className="ml-1 text-primary-strong">*</span>
            </label>
            <Input id="profile-address" value={form.address} onChange={(e) => update("address", e.target.value)} />
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
            <Button type="button" variant="outline" onClick={() => navigate("/mypage")}>
              취소
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "저장 중…" : "저장"}
            </Button>
          </div>
        </form>
      </Card>
    </PageContainer>
  );
}
