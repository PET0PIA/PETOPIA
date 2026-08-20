import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ApiError } from "../../api/client";
import { changePassword } from "../../api/auth";
import { getMe } from "../../api/user";

const PASSWORD_PATTERN = /^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#$%^&*]).{8,}$/;

export function PasswordChangePage() {
  const navigate = useNavigate();
  const [checkingAccess, setCheckingAccess] = useState(true);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPasswordConfirm, setNewPasswordConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getMe()
      .then((me) => {
        if (!active) return;
        if (!me.passwordChangeAvailable) {
          navigate("/mypage", { replace: true });
          return;
        }
        setCheckingAccess(false);
      })
      .catch(() => {
        if (active) navigate("/mypage", { replace: true });
      });
    return () => {
      active = false;
    };
  }, [navigate]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!PASSWORD_PATTERN.test(newPassword)) {
      setSubmitError("새 비밀번호는 영문, 숫자, 특수문자(!@#$%^&*)를 포함한 8자 이상이어야 해요.");
      return;
    }
    if (newPassword !== newPasswordConfirm) {
      setSubmitError("새 비밀번호가 일치하지 않아요.");
      return;
    }
    if (newPassword === currentPassword) {
      setSubmitError("현재 비밀번호와 다른 비밀번호를 입력해 주세요.");
      return;
    }

    setSubmitting(true);
    setSubmitError(null);
    try {
      await changePassword(currentPassword, newPassword);
      navigate("/mypage");
    } catch (error) {
      setSubmitError(
        error instanceof ApiError ? error.message : "비밀번호 변경에 실패했어요. 잠시 후 다시 시도해 주세요."
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (checkingAccess) {
    return (
      <PageContainer className="py-10">
        <p className="py-16 text-center text-sm text-muted">계정 정보를 확인하고 있어요...</p>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="마이페이지" title="비밀번호 변경" description="현재 비밀번호를 확인한 뒤 새 비밀번호로 바꿔요." />
      <Card className="mx-auto max-w-lg p-8">
        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label htmlFor="password-current" className="mb-1.5 block text-sm font-bold text-ink">현재 비밀번호</label>
            <Input
              id="password-current"
              type="password"
              autoComplete="current-password"
              required
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="password-new" className="mb-1.5 block text-sm font-bold text-ink">새 비밀번호</label>
            <Input
              id="password-new"
              type="password"
              autoComplete="new-password"
              required
              placeholder="영문, 숫자, 특수문자(!@#$%^&*)를 포함한 8자 이상 입력"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="password-new-confirm" className="mb-1.5 block text-sm font-bold text-ink">새 비밀번호 확인</label>
            <Input
              id="password-new-confirm"
              type="password"
              autoComplete="new-password"
              required
              value={newPasswordConfirm}
              onChange={(event) => setNewPasswordConfirm(event.target.value)}
            />
          </div>

          {submitError && <p className="text-sm font-bold text-primary-strong">{submitError}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => navigate("/mypage")}>
              취소
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "변경 중…" : "변경"}
            </Button>
          </div>
        </form>
      </Card>
    </PageContainer>
  );
}
