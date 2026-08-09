import { AlertCircle, CheckCircle2 } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ApiError } from "../../api/client";
import { resetPassword } from "../../api/auth";

const PASSWORD_PATTERN = /^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#$%^&*]).{8,}$/;

export function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  // 메일 링크가 아니라 주소를 직접 쳐서 들어오는 등 token이 없으면 재설정 자체가 불가능하다.
  if (!token) {
    return (
      <PageContainer className="py-10">
        <Card className="mx-auto max-w-md p-8 text-center">
          <p className="text-sm text-muted">유효하지 않은 링크예요. 비밀번호 찾기를 다시 진행해 주세요.</p>
          <Button className="mt-6" onClick={() => navigate("/forgot-password")}>비밀번호 찾기로 이동</Button>
        </Card>
      </PageContainer>
    );
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!PASSWORD_PATTERN.test(password)) {
      setSubmitError("비밀번호는 영문, 숫자, 특수문자(!@#$%^&*)를 포함한 8자 이상이어야 해요.");
      return;
    }
    if (password !== passwordConfirm) {
      setSubmitError("비밀번호가 일치하지 않아요.");
      return;
    }

    setSubmitting(true);
    setSubmitError(null);
    try {
      await resetPassword(token!, password);
      setDone(true);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "비밀번호 재설정에 실패했어요. 링크가 만료됐을 수 있어요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <PageContainer className="py-10">
        <Card className="mx-auto max-w-md p-8 text-center">
          <div className="mx-auto mb-4 grid size-14 place-items-center rounded-full bg-leaf-soft text-ink">
            <CheckCircle2 size={26} />
          </div>
          <h1 className="text-xl font-extrabold">비밀번호가 변경됐어요.</h1>
          <p className="mt-2 text-sm leading-6 text-muted">새 비밀번호로 로그인해 주세요.</p>
          <Button className="mt-6 w-full" onClick={() => navigate("/login")}>로그인하러 가기</Button>
        </Card>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="PETOPIA" title="비밀번호 재설정" description="새로 사용할 비밀번호를 입력해 주세요." />

      <Card className="mx-auto max-w-md p-8">
        {submitError && (
          <div className="mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <p>{submitError}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label htmlFor="reset-password-new" className="mb-1.5 block text-sm font-bold text-ink">새 비밀번호</label>
            <Input
              id="reset-password-new"
              type="password"
              autoComplete="new-password"
              required
              placeholder="영문, 숫자, 특수문자(!@#$%^&*)를 포함한 8자 이상 입력"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="reset-password-confirm" className="mb-1.5 block text-sm font-bold text-ink">새 비밀번호 확인</label>
            <Input
              id="reset-password-confirm"
              type="password"
              autoComplete="new-password"
              required
              value={passwordConfirm}
              onChange={(event) => setPasswordConfirm(event.target.value)}
            />
          </div>
          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? "변경 중..." : "비밀번호 변경"}
          </Button>
        </form>
      </Card>

      <p className="mt-4 text-center text-sm text-muted">
        <Link to="/login" className="font-bold text-primary-strong">로그인으로 돌아가기</Link>
      </p>
    </PageContainer>
  );
}
