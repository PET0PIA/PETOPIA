import { AlertCircle } from "lucide-react";
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ApiError } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import petopiaLogoOriginal from "../../assets/petopia-logo-original.png";

function label(text: string, htmlFor: string) {
  return <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-bold text-ink">{text}</label>;
}

// SUPER_ADMIN 전용 로그인. 공개 네비게이션 어디에도 링크를 걸지 않는다 - 일반 로그인 화면과
// 공격 표면을 분리하려는 의도(관리자 로그인 폼이 공개 홈페이지에 노출되지 않게).
export function AdminLoginPage() {
  const { loginAsAdmin } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setSubmitError(null);
    try {
      await loginAsAdmin({ email: email.trim(), password });
      navigate("/admin", { replace: true });
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "로그인에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen bg-page">
      <header className="border-b border-line bg-card">
        <div className="page-shell flex h-[72px] items-center gap-3">
          <img src={petopiaLogoOriginal} alt="" className="h-14 w-auto object-contain" />
          <span className="text-2xl font-black tracking-tight text-ink">관리자 로그인</span>
        </div>
      </header>

      <PageContainer className="flex items-center justify-center py-16">
        <div className="w-full">
          <p className="mb-8 text-center text-sm text-muted">관리자 로그인 페이지입니다.</p>

          <Card className="mx-auto max-w-md p-8">
            {submitError && (
              <div className="mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
                <AlertCircle size={18} className="mt-0.5 shrink-0" />
                <p>{submitError}</p>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                {label("아이디", "admin-email")}
                <Input
                  id="admin-email"
                  type="text"
                  autoComplete="username"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </div>
              <div>
                {label("비밀번호", "admin-password")}
                <Input
                  id="admin-password"
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
          </Card>
        </div>
      </PageContainer>
    </div>
  );
}
