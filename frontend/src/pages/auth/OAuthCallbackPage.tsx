import { AlertCircle } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ApiError } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import { maxBirthDate, today } from "../../utils/date";

const PHONE_PATTERN = /^01[0-9]-?\d{3,4}-?\d{4}$/;

/**
 * 소셜 로그인 성공 후 백엔드가 돌려보내는 착지 지점.
 * type=login  -> code로 바로 토큰 교환 (기존 유저)
 * type=signup -> code(tempKey)로 프로필/약관 마저 받아 가입 마무리 (신규 유저)
 */
export function OAuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const type = searchParams.get("type");
  const code = searchParams.get("code");

  if (!type || !code) {
    return (
      <PageContainer className="py-10">
        <Card className="mx-auto max-w-md p-8 text-center">
          <p className="text-sm text-muted">잘못된 접근이에요. 로그인을 다시 시도해 주세요.</p>
          <Link to="/login" className="mt-6 inline-block text-sm font-bold text-primary-strong">로그인으로 이동</Link>
        </Card>
      </PageContainer>
    );
  }

  if (type === "signup") return <OAuthSignupCompleteForm tempKey={code} />;
  return <OAuthLoginExchange code={code} />;
}

function OAuthLoginExchange({ code }: { code: string }) {
  const navigate = useNavigate();
  const { loginWithOAuthCode } = useAuth();
  const [error, setError] = useState<string | null>(null);
  // effect가 두 번 실행되는 환경(StrictMode)에서도 code(1회용)를 두 번 소모하지 않도록 방지
  const attempted = useRef(false);

  useEffect(() => {
    if (attempted.current) return;
    attempted.current = true;
    loginWithOAuthCode(code)
      .then(() => navigate("/", { replace: true }))
      .catch((err) => setError(err instanceof ApiError ? err.message : "로그인에 실패했어요. 다시 시도해 주세요."));
  }, [code, loginWithOAuthCode, navigate]);

  return (
    <PageContainer className="py-10">
      <Card className="mx-auto max-w-md p-8 text-center">
        {error ? (
          <>
            <div className="mb-4 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-left text-sm text-primary-strong">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <p>{error}</p>
            </div>
            <Link to="/login" className="text-sm font-bold text-primary-strong">로그인으로 돌아가기</Link>
          </>
        ) : (
          <p className="text-sm text-muted">로그인 처리 중이에요...</p>
        )}
      </Card>
    </PageContainer>
  );
}

interface SignupFormState {
  nickname: string;
  birthDate: string;
  phone: string;
  gender: "" | "남성" | "여성";
  address: string;
  agreedTerms: boolean;
  agreedPrivacy: boolean;
}

const initialForm: SignupFormState = {
  nickname: "",
  birthDate: "",
  phone: "",
  gender: "",
  address: "",
  agreedTerms: false,
  agreedPrivacy: false,
};

function label(text: string, required = false) {
  return <span className="mb-1.5 block text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</span>;
}

function validate(form: SignupFormState): string[] {
  const errors: string[] = [];
  if (form.nickname.trim() === "") errors.push("닉네임을 입력해 주세요.");
  if (form.birthDate === "") errors.push("생년월일을 입력해 주세요.");
  else if (form.birthDate >= today()) errors.push("생년월일은 오늘 이전 날짜여야 해요.");
  if (!PHONE_PATTERN.test(form.phone)) errors.push("휴대폰 번호 형식이 올바르지 않아요. (예: 010-1234-5678)");
  if (form.gender === "") errors.push("성별을 선택해 주세요.");
  if (form.address.trim() === "") errors.push("주소를 입력해 주세요.");
  if (!form.agreedTerms) errors.push("이용약관에 동의해 주세요.");
  if (!form.agreedPrivacy) errors.push("개인정보 처리방침에 동의해 주세요.");
  return errors;
}

function OAuthSignupCompleteForm({ tempKey }: { tempKey: string }) {
  const navigate = useNavigate();
  const { completeOAuthSignup } = useAuth();
  const [form, setForm] = useState<SignupFormState>(initialForm);
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  function update<K extends keyof SignupFormState>(key: K, value: SignupFormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validate(form);
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      await completeOAuthSignup({
        tempKey,
        nickname: form.nickname.trim(),
        birthDate: form.birthDate,
        phone: form.phone.trim(),
        gender: form.gender as "남성" | "여성",
        address: form.address.trim(),
        agreedTerms: form.agreedTerms,
        agreedPrivacy: form.agreedPrivacy,
      });
      navigate("/", { replace: true });
    } catch (error) {
      setSubmitError(
        error instanceof ApiError
          ? error.message
          : "가입을 마무리하지 못했어요. 처음부터 다시 시도해 주세요.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="PETOPIA" title="추가 정보 입력" description="소셜 인증은 끝났어요. 가입 완료를 위해 몇 가지만 더 입력해 주세요." />

      <Card className="mx-auto max-w-lg p-8">
        {(errors.length > 0 || submitError) && (
          <div className="mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <ul className="space-y-1">
              {submitError && <li>{submitError}</li>}
              {errors.map((message) => <li key={message}>{message}</li>)}
            </ul>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            {label("닉네임", true)}
            <Input placeholder="실명을 입력해 주세요" required value={form.nickname} onChange={(event) => update("nickname", event.target.value)} />
          </div>
          <div>
            {label("생년월일", true)}
            <Input type="date" max={maxBirthDate()} required value={form.birthDate} onChange={(event) => update("birthDate", event.target.value)} />
          </div>
          <div>
            {label("휴대폰 번호", true)}
            <Input placeholder="010-1234-5678" required value={form.phone} onChange={(event) => update("phone", event.target.value)} />
          </div>
          <div>
            {label("성별", true)}
            <div className="flex gap-4">
              {(["남성", "여성"] as const).map((option) => (
                <label key={option} className="flex items-center gap-2 text-sm text-ink">
                  <input type="radio" name="gender" value={option} checked={form.gender === option} onChange={() => update("gender", option)} />
                  {option}
                </label>
              ))}
            </div>
          </div>
          <div>
            {label("주소", true)}
            <Input placeholder="예) 서울특별시 노원구" required value={form.address} onChange={(event) => update("address", event.target.value)} />
          </div>

          <div className="space-y-2 border-t border-line pt-5">
            <label className="flex items-center gap-2 text-sm text-ink">
              <input type="checkbox" checked={form.agreedTerms} onChange={(event) => update("agreedTerms", event.target.checked)} />
              이용약관에 동의합니다. <span className="text-primary-strong">*</span>
            </label>
            <label className="flex items-center gap-2 text-sm text-ink">
              <input type="checkbox" checked={form.agreedPrivacy} onChange={(event) => update("agreedPrivacy", event.target.checked)} />
              개인정보 처리방침에 동의합니다. <span className="text-primary-strong">*</span>
            </label>
          </div>

          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? "가입 완료 중..." : "가입 완료"}
          </Button>
        </form>
      </Card>
    </PageContainer>
  );
}
