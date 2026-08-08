import { AlertCircle } from "lucide-react";
import { useState, type FormEvent, type ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { GoogleIcon, NaverIcon } from "../../components/icons/OAuthIcons";
import { ApiError } from "../../api/client";
import { oauthLoginUrl, type OAuthProvider } from "../../api/auth";
import { useAuth } from "../../contexts/AuthContext";

// 각 사 로그인 버튼 가이드라인에 맞춘 색상이라, 공용 Button의 variant를 안 쓰고 독립적으로 스타일링한다
// (variant가 주는 bg-card 등과 브랜드 배경색이 유틸 클래스 순서 문제로 충돌할 수 있어서).
const OAUTH_BUTTONS: { provider: OAuthProvider; label: string; icon: ReactNode; className: string }[] = [
  {
    provider: "google",
    label: "Google로 계속하기",
    icon: <GoogleIcon />,
    className: "border border-[#dadce0] bg-white text-[#3c4043] hover:bg-[#f7f8f8]",
  },
  {
    provider: "naver",
    label: "네이버로 계속하기",
    icon: <NaverIcon />,
    className: "bg-[#03C75A] text-white hover:opacity-90",
  },
];

// fetch가 아니라 브라우저 자체를 이동시켜야 한다 - 서버가 302로 동의화면까지 보내주는 흐름이라
function startOAuthLogin(provider: OAuthProvider) {
  window.location.href = oauthLoginUrl(provider);
}

function label(text: string) {
  return <span className="mb-1.5 block text-sm font-bold text-ink">{text}</span>;
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setSubmitError(null);
    try {
      await login({ email: email.trim(), password });
      navigate(from, { replace: true });
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "로그인에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="PETOPIA" title="로그인" description="이메일과 비밀번호로 로그인해 주세요." />

      <Card className="mx-auto max-w-md p-8">
        {submitError && (
          <div className="mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <p>{submitError}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            {label("이메일")}
            <Input
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <div>
            {label("비밀번호")}
            <Input
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? "로그인 중..." : "로그인"}
          </Button>
        </form>

        <div className="my-6 flex items-center gap-3">
          <div className="h-px flex-1 bg-line" />
          <span className="text-xs text-muted">또는</span>
          <div className="h-px flex-1 bg-line" />
        </div>

        <div className="space-y-2">
          {OAUTH_BUTTONS.map(({ provider, label: buttonLabel, icon, className }) => (
            <button
              key={provider}
              type="button"
              onClick={() => startOAuthLogin(provider)}
              className={`inline-flex min-h-11 w-full items-center justify-center gap-3 rounded-button px-4 text-sm font-bold transition disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
            >
              {icon}
              {buttonLabel}
            </button>
          ))}
        </div>
      </Card>

      <p className="mt-4 text-center text-sm text-muted">
        아직 계정이 없으신가요? <Link to="/signup" className="font-bold text-primary-strong">회원가입</Link>
      </p>
      <p className="mt-2 text-center text-sm text-muted">
        비밀번호를 잊으셨나요? <Link to="/forgot-password" className="font-bold text-primary-strong">비밀번호 찾기</Link>
      </p>
    </PageContainer>
  );
}
