import { AlertCircle, MailCheck } from "lucide-react";
import { useRef, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ApiError } from "../../api/client";
import { checkEmailAvailable, resendVerification, signup, verifyEmail, type EmailSignupRequest } from "../../api/auth";
import { maxBirthDate, today } from "../../utils/date";
import { SignupWelcomeCard } from "./SignupWelcomeCard";
import { SignupAgreements } from "../../components/auth/SignupAgreements";

interface FormState {
  email: string;
  password: string;
  passwordConfirm: string;
  nickname: string;
  birthDate: string;
  phone: string;
  gender: "" | "남성" | "여성";
  address: string;
  agreedTerms: boolean;
  agreedPrivacy: boolean;
}

const initialForm: FormState = {
  email: "",
  password: "",
  passwordConfirm: "",
  nickname: "",
  birthDate: "",
  phone: "",
  gender: "",
  address: "",
  agreedTerms: false,
  agreedPrivacy: false,
};

const PASSWORD_PATTERN = /^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#$%^&*]).{8,}$/;
const PHONE_PATTERN = /^01[0-9]-?\d{3,4}-?\d{4}$/;

// 회원가입 폼 작성 -> 이메일 인증까지 한 페이지 안에서 단계 전환으로 처리한다.
// (별도 라우트로 분리했을 때는 이메일 인증 단계에서 새로고침하면 location.state가
//  날아가 "다시 회원가입하라"는 화면이 뜨는 문제가 있었음)
type Step = "form" | "verify" | "verified";

function label(text: string, htmlFor: string, required = false) {
  return <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</label>;
}

// 성별처럼 라디오 그룹 전체를 아우르는 제목은 특정 input 하나에 매어둘 수 없어서 label() 대신 이걸 쓴다
// (진짜로 접근성을 제대로 하려면 <fieldset><legend>이 맞지만, 지금 범위 밖이라 시각적 표기만 맞춤)
function groupLabel(text: string, required = false) {
  return <span className="mb-1.5 block text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</span>;
}

type EmailCheckStatus = "idle" | "checking" | "available" | "unavailable";

function validate(form: FormState, emailCheckStatus: EmailCheckStatus): string[] {
  const errors: string[] = [];
  if (form.email.trim() === "") errors.push("이메일을 입력해 주세요.");
  else if (emailCheckStatus === "unavailable") errors.push("이미 사용 중인 이메일이에요. 다른 이메일을 입력해 주세요.");
  else if (emailCheckStatus !== "available") errors.push("이메일 중복 확인을 먼저 해 주세요.");
  if (!PASSWORD_PATTERN.test(form.password)) errors.push("비밀번호는 영문, 숫자, 특수문자(!@#$%^&*)를 포함한 8자 이상이어야 해요.");
  if (form.password !== form.passwordConfirm) errors.push("비밀번호가 일치하지 않아요.");
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

function toRequest(form: FormState): EmailSignupRequest {
  return {
    email: form.email.trim(),
    password: form.password,
    passwordConfirm: form.passwordConfirm,
    nickname: form.nickname.trim(),
    birthDate: form.birthDate,
    phone: form.phone.trim(),
    gender: form.gender as "남성" | "여성",
    address: form.address.trim(),
    agreedTerms: form.agreedTerms,
    agreedPrivacy: form.agreedPrivacy,
  };
}

export function SignupPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>("form");

  const [form, setForm] = useState<FormState>(initialForm);
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [emailCheckStatus, setEmailCheckStatus] = useState<EmailCheckStatus>("idle");
  const [emailCheckError, setEmailCheckError] = useState<string | null>(null);
  // 이메일 변경/재확인마다 증가시켜서, 응답이 늦게 와도 그 사이 값이 바뀌었으면 무시하기 위한 용도
  // (A 확인 중 B로 바꾸면 A의 응답이 B 칸에 그대로 반영되는 경쟁 상태 방지)
  const emailCheckRequestId = useRef(0);

  const [code, setCode] = useState("");
  const [verifying, setVerifying] = useState(false);
  const [verifyError, setVerifyError] = useState<string | null>(null);
  const [resending, setResending] = useState(false);
  const [resendMessage, setResendMessage] = useState<string | null>(null);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
    // 이메일을 바꾸면 이전 중복확인 결과는 더 이상 유효하지 않다. 진행 중이던 요청의 응답도 무시해야 하므로 요청 ID도 갱신한다.
    if (key === "email") {
      emailCheckRequestId.current += 1;
      setEmailCheckStatus("idle");
      setEmailCheckError(null);
    }
  }

  async function handleCheckEmail() {
    if (form.email.trim() === "") return;
    const requestId = ++emailCheckRequestId.current;
    setEmailCheckStatus("checking");
    setEmailCheckError(null);
    try {
      const { available } = await checkEmailAvailable(form.email.trim());
      // 응답 도착 전에 이메일이 바뀌었거나 재확인이 또 눌렸으면(요청 ID가 바뀌었으면) 이 응답은 버린다.
      if (emailCheckRequestId.current !== requestId) return;
      setEmailCheckStatus(available ? "available" : "unavailable");
    } catch (error) {
      if (emailCheckRequestId.current !== requestId) return;
      setEmailCheckStatus("idle");
      setEmailCheckError(error instanceof ApiError ? error.message : "이메일 확인에 실패했어요.");
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validate(form, emailCheckStatus);
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      await signup(toRequest(form));
      setStep("verify");
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "회원가입에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleVerifySubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setVerifying(true);
    setVerifyError(null);
    try {
      await verifyEmail(form.email.trim(), code.trim());
      setStep("verified");
    } catch (error) {
      setVerifyError(error instanceof ApiError ? error.message : "인증에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setVerifying(false);
    }
  }

  async function handleResend() {
    setResending(true);
    setResendMessage(null);
    setVerifyError(null);
    try {
      await resendVerification(form.email.trim());
      setResendMessage("인증 코드를 다시 보냈어요.");
    } catch (error) {
      setVerifyError(error instanceof ApiError ? error.message : "재전송에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setResending(false);
    }
  }

  if (step === "verified") {
    return (
      <PageContainer className="py-10">
        <SignupWelcomeCard
          registerLabel="로그인하고 반려동물 등록하기"
          laterLabel="로그인하러 가기"
          onRegisterPet={() => navigate("/login", { state: { from: "/mypage/pets/new" } })}
          onLater={() => navigate("/login")}
        />
      </PageContainer>
    );
  }

  if (step === "verify") {
    return (
      <PageContainer className="py-10">
        <PageHeader eyebrow="PETOPIA" title="이메일 인증" description={`${form.email}로 보낸 인증 코드를 입력해 주세요.`} />

        <Card className="mx-auto max-w-md p-8">
          {verifyError && (
            <div className="mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <p>{verifyError}</p>
            </div>
          )}
          {resendMessage && (
            <div className="mb-6 flex items-start gap-3 border-line bg-page p-4 text-sm text-muted">
              <MailCheck size={18} className="mt-0.5 shrink-0" />
              <p>{resendMessage}</p>
            </div>
          )}

          <form onSubmit={handleVerifySubmit} className="space-y-5">
            <div>
              <label htmlFor="signup-code" className="mb-1.5 block text-sm font-bold text-ink">인증 코드</label>
              <Input id="signup-code" required value={code} onChange={(event) => setCode(event.target.value)} />
            </div>
            <Button type="submit" className="w-full" disabled={verifying}>
              {verifying ? "확인 중..." : "인증하기"}
            </Button>
            <Button type="button" variant="outline" className="w-full" onClick={handleResend} disabled={resending}>
              {resending ? "재전송 중..." : "인증 코드 재전송"}
            </Button>
          </form>
        </Card>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="PETOPIA" title="회원가입" description="가입 후 이메일로 받은 인증 코드까지 입력해야 로그인할 수 있어요." />

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
            {label("이메일", "signup-email", true)}
            <div className="flex gap-2">
              <Input id="signup-email" type="email" required value={form.email} onChange={(event) => update("email", event.target.value)} />
              <Button type="button" variant="outline" className="shrink-0 whitespace-nowrap" onClick={handleCheckEmail} disabled={emailCheckStatus === "checking"}>
                중복확인
              </Button>
            </div>
            {emailCheckStatus === "available" && <p className="mt-1.5 text-sm text-primary-strong">사용할 수 있는 이메일이에요.</p>}
            {emailCheckStatus === "unavailable" && <p className="mt-1.5 text-sm text-muted">이미 사용 중인 이메일이에요.</p>}
            {emailCheckError && <p className="mt-1.5 text-sm text-muted">{emailCheckError}</p>}
          </div>

          <div>
            {label("비밀번호", "signup-password", true)}
            <Input
              id="signup-password"
              type="password"
              autoComplete="new-password"
              required
              placeholder="영문, 숫자, 특수문자(!@#$%^&*)를 포함한 8자 이상 입력"
              value={form.password}
              onChange={(event) => update("password", event.target.value)}
            />
          </div>
          <div>
            {label("비밀번호 확인", "signup-password-confirm", true)}
            <Input id="signup-password-confirm" type="password" autoComplete="new-password" required value={form.passwordConfirm} onChange={(event) => update("passwordConfirm", event.target.value)} />
          </div>
          <div>
            {label("닉네임", "signup-nickname", true)}
            <Input id="signup-nickname" placeholder="실명을 입력해 주세요" required value={form.nickname} onChange={(event) => update("nickname", event.target.value)} />
          </div>
          <div>
            {label("생년월일", "signup-birth-date", true)}
            <Input id="signup-birth-date" type="date" max={maxBirthDate()} required value={form.birthDate} onChange={(event) => update("birthDate", event.target.value)} />
          </div>
          <div>
            {label("휴대폰 번호", "signup-phone", true)}
            <Input id="signup-phone" placeholder="010-1234-5678" required value={form.phone} onChange={(event) => update("phone", event.target.value)} />
          </div>
          <div>
            {groupLabel("성별", true)}
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
            {label("주소", "signup-address", true)}
            <Input id="signup-address" placeholder="예) 서울특별시 노원구" required value={form.address} onChange={(event) => update("address", event.target.value)} />
          </div>

          <SignupAgreements
            agreedTerms={form.agreedTerms}
            agreedPrivacy={form.agreedPrivacy}
            onTermsChange={(checked) => update("agreedTerms", checked)}
            onPrivacyChange={(checked) => update("agreedPrivacy", checked)}
          />

          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? "가입 중..." : "회원가입"}
          </Button>
        </form>
      </Card>
    </PageContainer>
  );
}
