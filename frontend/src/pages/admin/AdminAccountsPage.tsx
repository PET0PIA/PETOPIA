import { AlertCircle } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Table } from "../../components/ui/Table";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import { getAdminAccounts, updateAdminAccountStatus, type AdminAccount } from "../../api/adminAccount";

function formatPeriod(account: AdminAccount): string {
  if (!account.operationStartDate || !account.operationEndDate) return "-";
  return `${account.operationStartDate} ~ ${account.operationEndDate}`;
}

export function AdminAccountsPage() {
  const { confirm, confirmDialog } = useConfirm();

  const [accounts, setAccounts] = useState<AdminAccount[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [updatingUserId, setUpdatingUserId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const aliveRef = useRef(true);
  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
    };
  }, []);

  // 이펙트 본문에서 곧바로 setState를 호출하면 안 되므로(react-hooks/set-state-in-effect),
  // "로딩/에러 리셋"은 재시도 버튼의 이벤트 핸들러(handleRetry)에서만 하고, 이펙트는 이
  // fetch 함수만 그대로 호출한다 - 초기 상태(loading=true, loadError=null)로 이미 충분하다.
  function fetchAccounts() {
    getAdminAccounts()
      .then((result) => {
        if (aliveRef.current) setAccounts(result);
      })
      .catch((err: unknown) => {
        if (!aliveRef.current) return;
        setAccounts(null);
        setLoadError(err instanceof ApiError ? err.message : "관리자 계정 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (aliveRef.current) setLoading(false);
      });
  }

  function handleRetry() {
    setLoading(true);
    setLoadError(null);
    fetchAccounts();
  }

  useEffect(fetchAccounts, []);

  async function handleToggleStatus(account: AdminAccount) {
    const suspending = account.status === "ACTIVE";
    if (suspending) {
      const confirmed = await confirm({
        title: "계정을 정지할까요?",
        description: `${account.nickname}(${account.email})의 로그인을 막아요. 발급된 액세스 토큰도 즉시 차단됩니다.`,
      });
      if (!confirmed) return;
    }

    setActionError(null);
    setUpdatingUserId(account.userId);
    try {
      await updateAdminAccountStatus(account.userId, suspending ? "INACTIVE" : "ACTIVE");
      setAccounts((current) =>
        current?.map((item) => (item.userId === account.userId ? { ...item, status: suspending ? "INACTIVE" : "ACTIVE" } : item)) ?? null
      );
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "계정 상태 변경에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setUpdatingUserId(null);
    }
  }

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader eyebrow="전체 운영" title="관리자 계정 목록" description="행사 관리자(EVENT_ADMIN) 계정과 담당 행사, 정지 상태를 관리해요." />

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}
      {actionError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{actionError}</p>
        </div>
      )}

      {loading && <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {!loading && loadError && (
        <div className="text-right">
          <Button variant="outline" onClick={handleRetry}>
            다시 시도
          </Button>
        </div>
      )}

      {!loading && !loadError && accounts && accounts.length === 0 && (
        <EmptyState title="발급된 관리자 계정이 없어요" description="행사 신청이 승인되면 관리자 계정이 자동으로 발급돼요." />
      )}

      {!loading && !loadError && accounts && accounts.length > 0 && (
        <Table>
          <thead>
            <tr className="border-b border-line bg-page text-xs font-bold text-muted">
              <th className="px-4 py-3">이메일</th>
              <th className="px-4 py-3">닉네임</th>
              <th className="px-4 py-3">담당 행사</th>
              <th className="px-4 py-3">운영 기간</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3" aria-label="액션" />
            </tr>
          </thead>
          <tbody>
            {accounts.map((account) => (
              <tr key={account.userId} className="border-b border-line last:border-b-0">
                <td className="whitespace-nowrap px-4 py-3 text-ink">{account.email}</td>
                <td className="whitespace-nowrap px-4 py-3 text-ink">{account.nickname}</td>
                <td className="whitespace-nowrap px-4 py-3 text-ink">{account.fairName ?? "-"}</td>
                <td className="whitespace-nowrap px-4 py-3 text-muted">{formatPeriod(account)}</td>
                <td className="whitespace-nowrap px-4 py-3">
                  <Badge tone={account.status === "ACTIVE" ? "leaf" : "neutral"}>
                    {account.status === "ACTIVE" ? "정상" : "정지됨"}
                  </Badge>
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-right">
                  <Button
                    variant={account.status === "ACTIVE" ? "secondary" : "outline"}
                    disabled={updatingUserId === account.userId}
                    onClick={() => handleToggleStatus(account)}
                  >
                    {updatingUserId === account.userId ? "처리 중…" : account.status === "ACTIVE" ? "정지" : "정지 해제"}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      {confirmDialog}
    </div>
  );
}
