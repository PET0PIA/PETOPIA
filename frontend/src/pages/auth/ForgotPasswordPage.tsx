import { AlertCircle, MailCheck } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ApiError } from "../../api/client";
import { requestPasswordReset } from "../../api/auth";

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setSubmitError(null);
    try {
      await requestPasswordReset(email.trim());
      setSent(true);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "요청에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (sent) {
    return (
      <PageContainer className="py-10">
        <Card className="mx-auto max-w-md p-8 text-center">
          <div className="mx-auto mb-4 grid size-14 place-items-center rounded-full bg-leaf-soft text-ink">
            <MailCheck size={26} />
          </div>
          <h1 className="text-xl font-extrabold">메일을 확인해 주세요.</h1>
          <p className="mt-2 text-sm leading-6 text-muted">
            {email} 주소로 비밀번호 재설정 링크를 보냈어요. 가입한 이메일이 맞다면 잠시 후 메일함에서 확인할 수 있어요.
          </p>
          <Link to="/login" className="mt-6 inline-block text-sm font-bold text-primary-strong">
            로그인으로 돌아가기
          </Link>
        </Card>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="PETOPIA" title="비밀번호 찾기" description="가입한 이메일로 비밀번호 재설정 링크를 보내드려요." />

      <Card className="mx-auto max-w-md p-8">
        {submitError && (
          <div className="mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <p>{submitError}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label htmlFor="forgot-password-email" className="mb-1.5 block text-sm font-bold text-ink">이메일</label>
            <Input id="forgot-password-email" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
          </div>
          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting ? "전송 중..." : "재설정 링크 받기"}
          </Button>
        </form>
      </Card>

      <p className="mt-4 text-center text-sm text-muted">
        비밀번호가 기억나셨나요? <Link to="/login" className="font-bold text-primary-strong">로그인</Link>
      </p>
    </PageContainer>
  );
}
