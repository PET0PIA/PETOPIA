import { KeyRound, Mail } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import { getMe, withdraw, type UserMe } from "../../api/user";
import { useAuth } from "../../contexts/AuthContext";

/** 계정 설정 - 로그인 정보(이메일·비밀번호)와 회원 탈퇴만 모아둔 화면.
 * 닉네임·연락처 같은 프로필 값은 "프로필 정보"(/mypage/edit)가 담당한다. */
export function AccountSettingsPage() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const { confirm, confirmDialog } = useConfirm();
  const [me, setMe] = useState<UserMe | null>(null);
  const [withdrawing, setWithdrawing] = useState(false);
  const [withdrawError, setWithdrawError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getMe()
      .then((result) => { if (alive) setMe(result); })
      .catch(() => { if (alive) setMe(null); /* 실패해도 탈퇴 기능은 쓸 수 있게 화면은 띄운다. */ });
    return () => { alive = false; };
  }, []);

  async function handleWithdraw() {
    const confirmed = await confirm({
      title: "정말 탈퇴하시겠어요?",
      description: "탈퇴하면 되돌릴 수 없어요. 진행 중인 예약이나 결제가 있으면 먼저 처리한 뒤 다시 시도해 주세요.",
      confirmLabel: "탈퇴",
    });
    if (!confirmed) return;

    setWithdrawing(true);
    setWithdrawError(null);
    try {
      await withdraw();
      // 백엔드가 토큰을 무효화했어도 프론트 세션 상태(user/status)를 지워야 로그인한 것처럼
      // 보이는 화면이 안 남는다. logout()은 실패해도 클라이언트 상태를 정리한다(AuthContext 참고).
      await logout();
      navigate("/");
    } catch (err) {
      setWithdrawError(err instanceof ApiError ? err.message : "탈퇴에 실패했어요. 잠시 후 다시 시도해 주세요.");
      setWithdrawing(false);
    }
  }

  return (
    <div>
      <PageHeader eyebrow="마이페이지" title="계정 설정" description="로그인에 쓰는 정보와 계정 상태를 관리해요." />

      <Card className="divide-y divide-line">
        <div className="flex flex-col gap-3 p-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex min-w-0 items-center gap-3">
            <Mail size={18} className="shrink-0 text-muted" aria-hidden="true" />
            <div className="min-w-0">
              <p className="font-bold text-ink">이메일</p>
              <p className="mt-0.5 truncate text-sm text-muted">{me?.email ?? "불러오는 중이에요…"}</p>
            </div>
          </div>
          <p className="shrink-0 text-sm text-muted">변경할 수 없어요</p>
        </div>

        <div className="flex flex-col gap-3 p-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex min-w-0 items-center gap-3">
            <KeyRound size={18} className="shrink-0 text-muted" aria-hidden="true" />
            <div className="min-w-0">
              <p className="font-bold text-ink">비밀번호</p>
              <p className="mt-0.5 text-sm text-muted">
                {me == null
                  ? "불러오는 중이에요…"
                  : me.passwordChangeAvailable
                    ? "주기적으로 바꾸면 계정을 더 안전하게 지킬 수 있어요."
                    : "소셜 로그인으로 만든 계정이라 비밀번호가 없어요."}
              </p>
            </div>
          </div>
          {/* 소셜 계정은 비밀번호 변경 자체가 막혀 있어 버튼을 아예 내지 않는다. */}
          {me?.passwordChangeAvailable && (
            <Link to="/mypage/password" className="shrink-0">
              <Button variant="outline" className="shadow-sm">비밀번호 변경</Button>
            </Link>
          )}
        </div>
      </Card>

      {/* 되돌릴 수 없는 액션이라 다른 항목과 같은 카드에 두지 않고 아래에 따로 뗀다(오클릭 방지). */}
      <section className="mt-8 border-t border-line pt-6">
        <h2 className="font-bold text-ink">회원 탈퇴</h2>
        <p className="mt-1 text-sm leading-6 text-muted">
          탈퇴하면 예약·신청 내역을 포함한 계정 정보를 다시 볼 수 없어요. 진행 중인 예약이나 결제가 있으면 먼저 정리해 주세요.
        </p>
        {withdrawError && <p className="mt-3 text-sm font-bold text-primary-strong">{withdrawError}</p>}
        <Button
          className="mt-4 text-red-600 hover:bg-red-50 hover:text-red-700"
          variant="ghost"
          onClick={handleWithdraw}
          disabled={withdrawing}
        >
          {withdrawing ? "탈퇴 처리 중…" : "회원 탈퇴"}
        </Button>
      </section>

      {confirmDialog}
    </div>
  );
}
