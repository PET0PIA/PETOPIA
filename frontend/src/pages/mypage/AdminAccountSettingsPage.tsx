import { KeyRound, LayoutDashboard, UserRound } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMe, type UserMe } from "../../api/user";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";

const roleLabels: Record<"SUPER_ADMIN" | "EVENT_ADMIN", string> = {
  SUPER_ADMIN: "최고 관리자",
  EVENT_ADMIN: "박람회 관리자",
};

export function AdminAccountSettingsPage() {
  const navigate = useNavigate();
  const [me, setMe] = useState<UserMe | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getMe()
      .then((result) => {
        if (active) setMe(result);
      })
      .catch(() => {
        if (active) setError("계정 정보를 불러오지 못했어요.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  if (loading) {
    return (
      <PageContainer className="py-10">
        <p className="py-16 text-center text-sm text-muted">계정 정보를 불러오는 중이에요...</p>
      </PageContainer>
    );
  }

  if (error || !me || (me.role !== "SUPER_ADMIN" && me.role !== "EVENT_ADMIN")) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="계정 정보를 불러오지 못했어요." description={error ?? undefined} />
      </PageContainer>
    );
  }

  const consolePath = me.role === "SUPER_ADMIN" ? "/admin" : "/fair-admin";

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="계정 설정" title="계정 관리" description="관리자 계정 정보와 보안 설정을 확인할 수 있어요." />

      <Card className="mx-auto max-w-2xl p-8">
        <div className="mb-7 flex items-center gap-3 border-b border-line pb-6">
          <div className="grid size-11 place-items-center rounded-full bg-page text-ink">
            <UserRound size={21} aria-hidden="true" />
          </div>
          <div>
            <p className="font-extrabold text-ink">{me.nickname}</p>
            <p className="text-sm text-muted">{roleLabels[me.role]}</p>
          </div>
        </div>

        <dl className="grid gap-5 text-sm sm:grid-cols-2">
          <div>
            <dt className="font-bold text-muted">이름</dt>
            <dd className="mt-1 text-ink">{me.nickname}</dd>
          </div>
          <div>
            <dt className="font-bold text-muted">로그인 아이디</dt>
            <dd className="mt-1 break-all text-ink">{me.email}</dd>
          </div>
          <div>
            <dt className="font-bold text-muted">계정 유형</dt>
            <dd className="mt-1 text-ink">{roleLabels[me.role]}</dd>
          </div>
        </dl>

        <div className="mt-8 flex flex-col gap-2 border-t border-line pt-6 sm:flex-row sm:justify-end">
          {me.passwordChangeAvailable && (
            <Button variant="outline" className="shadow-sm" onClick={() => navigate("/mypage/password")}>
              <KeyRound size={16} aria-hidden="true" />
              비밀번호 변경
            </Button>
          )}
          <Button onClick={() => navigate(consolePath)}>
            <LayoutDashboard size={16} aria-hidden="true" />
            관리자 콘솔로 이동
          </Button>
        </div>
      </Card>
    </PageContainer>
  );
}
