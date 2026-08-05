import { AlertCircle, Search } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  getAuditLogs,
  type ActionType,
  type ActorType,
  type AuditLogListResponse,
  type AuditLogQuery,
  type TargetType,
} from "../../api/audit";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";

type FilterKind = "target" | "actor" | "action";

const filterKindLabels: Record<FilterKind, string> = {
  target: "대상별",
  actor: "행위자별",
  action: "액션 타입별",
};

const targetTypeLabels: Record<TargetType, string> = {
  ACCOUNT: "계정",
  FAIR: "행사",
  RESERVATION: "예약",
  SETTLEMENT: "정산",
  COMMISSION_RATE: "수수료율",
};

const actionTypeLabels: Record<ActionType, string> = {
  LOGIN_FAIL: "로그인 실패",
  ROLE_CHANGE: "권한 변경",
  ACCOUNT_DEACTIVATE: "계정 비활성화",
  FAIR_APPROVE: "행사 승인",
  FAIR_REJECT: "행사 반려",
  FAIR_CANCEL_APPROVE: "행사 취소 승인",
  PAYMENT_COMPLETION_RECEIVED: "결제 완료 수신",
  SETTLEMENT_CONFIRM: "정산 확정",
  COMMISSION_RATE_UPDATE: "수수료율 변경",
};

const actorTypeLabels: Record<ActorType, string> = {
  USER: "사용자",
  ADMIN: "관리자",
  SYSTEM: "시스템",
  PAYMENT: "결제",
};

function formatDateTime(value: string) {
  return new Date(value).toLocaleString("ko-KR");
}

function isValidId(value: string) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0;
}

export function AuditLogPage() {
  const [filterKind, setFilterKind] = useState<FilterKind>("target");
  const [targetTypeInput, setTargetTypeInput] = useState<TargetType | "">("");
  const [targetIdInput, setTargetIdInput] = useState("");
  const [actorIdInput, setActorIdInput] = useState("");
  const [actionTypeInput, setActionTypeInput] = useState<ActionType | "">("");

  const [appliedQuery, setAppliedQuery] = useState<AuditLogQuery | null>(null);
  const [page, setPage] = useState(0);
  const [response, setResponse] = useState<AuditLogListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  async function load(query: AuditLogQuery, pageArg: number) {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await getAuditLogs({ ...query, page: pageArg, size: 20 });
      setResponse(data);
    } catch (error) {
      setResponse(null);
      setLoadError(error instanceof ApiError ? error.message : "감사 로그를 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (appliedQuery) load(appliedQuery, page);
  }, [appliedQuery, page]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (filterKind === "target") {
      if (targetTypeInput === "" || !isValidId(targetIdInput)) {
        setLoadError("대상 종류와 대상 ID를 모두 선택/입력해 주세요.");
        return;
      }
      setAppliedQuery({ targetType: targetTypeInput, targetId: Number(targetIdInput) });
    } else if (filterKind === "actor") {
      if (!isValidId(actorIdInput)) {
        setLoadError("행위자 사용자 ID를 숫자로 입력해 주세요.");
        return;
      }
      setAppliedQuery({ actorUserId: Number(actorIdInput) });
    } else {
      if (actionTypeInput === "") {
        setLoadError("액션 타입을 선택해 주세요.");
        return;
      }
      setAppliedQuery({ actionType: actionTypeInput });
    }

    setLoadError(null);
    setPage(0);
  }

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader eyebrow="전체 운영" title="감사 로그" description="관리자·시스템의 주요 조치 이력을 조회해요." />

      <form onSubmit={handleSubmit} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
        <div className="w-full sm:w-44">
          <span className="mb-1.5 block text-sm font-bold text-ink">조회 조건</span>
          <Select value={filterKind} onChange={(event) => setFilterKind(event.target.value as FilterKind)}>
            {Object.entries(filterKindLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>

        {filterKind === "target" && (
          <>
            <div className="w-full sm:w-40">
              <span className="mb-1.5 block text-sm font-bold text-ink">대상 종류</span>
              <Select value={targetTypeInput} onChange={(event) => setTargetTypeInput(event.target.value as TargetType)}>
                <option value="">선택</option>
                {Object.entries(targetTypeLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </div>
            <div className="flex-1">
              <span className="mb-1.5 block text-sm font-bold text-ink">대상 ID</span>
              <Input type="number" min={1} value={targetIdInput} onChange={(event) => setTargetIdInput(event.target.value)} placeholder="예: 1" />
            </div>
          </>
        )}

        {filterKind === "actor" && (
          <div className="flex-1">
            <span className="mb-1.5 block text-sm font-bold text-ink">행위자 사용자 ID</span>
            <Input type="number" min={1} value={actorIdInput} onChange={(event) => setActorIdInput(event.target.value)} placeholder="예: 1" />
          </div>
        )}

        {filterKind === "action" && (
          <div className="flex-1">
            <span className="mb-1.5 block text-sm font-bold text-ink">액션 타입</span>
            <Select value={actionTypeInput} onChange={(event) => setActionTypeInput(event.target.value as ActionType)}>
              <option value="">선택</option>
              {Object.entries(actionTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
        )}

        <Button type="submit" variant="outline">
          <Search size={16} />
          조회
        </Button>
      </form>

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {!appliedQuery && !loading && (
        <EmptyState title="조회 조건을 선택해 주세요" description="대상, 행위자, 액션 타입 중 하나를 골라 조회하면 감사 로그가 표시돼요." />
      )}

      {loading && <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {appliedQuery && !loading && response && response.items.length === 0 && (
        <EmptyState title="조건에 해당하는 감사 로그가 없어요" description="다른 조회 조건으로 다시 시도해 보세요." />
      )}

      {appliedQuery && !loading && response && response.items.length > 0 && (
        <div>
          <Table>
            <thead>
              <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                <th className="px-4 py-3">발생 시각</th>
                <th className="px-4 py-3">행위자</th>
                <th className="px-4 py-3">액션</th>
                <th className="px-4 py-3">대상</th>
                <th className="px-4 py-3">변경 전</th>
                <th className="px-4 py-3">변경 후</th>
              </tr>
            </thead>
            <tbody>
              {response.items.map((row) => (
                <tr key={row.auditId} className="border-b border-line last:border-b-0">
                  <td className="whitespace-nowrap px-4 py-3 text-ink">{formatDateTime(row.occurredAt)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">
                    {actorTypeLabels[row.actorType] ?? row.actorType}
                    {row.actorRole ? ` · ${row.actorRole}` : ""} (#{row.userId})
                  </td>
                  <td className="whitespace-nowrap px-4 py-3">
                    <Badge tone="primary" className="whitespace-nowrap">{actionTypeLabels[row.actionType] ?? row.actionType}</Badge>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">
                    {targetTypeLabels[row.targetType] ?? row.targetType} #{row.targetId}
                  </td>
                  <td className="min-w-52 whitespace-pre-wrap break-all px-4 py-3 font-mono text-xs text-muted">
                    {row.beforeValue ?? "-"}
                  </td>
                  <td className="min-w-52 whitespace-pre-wrap break-all px-4 py-3 font-mono text-xs text-muted">
                    {row.afterValue ?? "-"}
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>

          <div className="mt-4 flex items-center justify-between text-sm text-muted">
            <p>
              페이지 {response.page + 1} · 총 {response.totalElements.toLocaleString("ko-KR")}건
            </p>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={response.page === 0}>
                이전
              </Button>
              <Button variant="outline" onClick={() => setPage((current) => current + 1)} disabled={!response.hasNext}>
                다음
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
