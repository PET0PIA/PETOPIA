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

const targetTypeLabels: Record<TargetType, string> = {
  ACCOUNT: "계정",
  FAIR: "행사",
  RESERVATION: "예약",
  SETTLEMENT: "정산",
  COMMISSION_RATE: "수수료율",
};

// 대상 ID는 대상 종류에 따라 실제로 가리키는 게 다르다(계정=회원번호, 행사=행사ID 등) -
// 어떤 숫자를 넣어야 하는지 감이 안 오는 필드라 예시를 같이 보여준다.
const targetIdHints: Record<TargetType, string> = {
  ACCOUNT: "회원번호. 계정 관리 목록에서 확인",
  FAIR: "행사 ID. 행사 상세 화면 URL에서 확인",
  RESERVATION: "예약 ID",
  SETTLEMENT: "정산 ID. 정산 목록에서 확인",
  COMMISSION_RATE: "수수료율 ID",
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
  const [targetTypeInput, setTargetTypeInput] = useState<TargetType | "">("");
  const [targetIdInput, setTargetIdInput] = useState("");
  const [actorIdInput, setActorIdInput] = useState("");
  const [actionTypeInput, setActionTypeInput] = useState<ActionType | "">("");
  const [startDateInput, setStartDateInput] = useState("");
  const [endDateInput, setEndDateInput] = useState("");

  // null이면 아직 한 번도 조회 버튼을 안 누른 상태 - 필터를 하나도 안 채워도 조회는 되지만
  // (백엔드가 조건 없으면 전체를 돌려줌), 페이지 진입 즉시 전체 조회를 쏘진 않는다.
  const [appliedQuery, setAppliedQuery] = useState<AuditLogQuery | null>(null);
  const [page, setPage] = useState(0);
  const [response, setResponse] = useState<AuditLogListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!appliedQuery) return;
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    getAuditLogs({ ...appliedQuery, page, size: 20 })
      .then((data) => { if (!ignore) setResponse(data); })
      .catch((error) => {
        if (!ignore) {
          setResponse(null);
          setLoadError(error instanceof ApiError ? error.message : "감사 로그를 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [appliedQuery, page]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    // 대상종류/ID는 짝으로만 의미가 있다 - 하나만 채워졌으면 조회 조건에서 빠뜨리지 않도록 막는다.
    const targetFilled = targetTypeInput !== "" || targetIdInput !== "";
    if (targetFilled && (targetTypeInput === "" || !isValidId(targetIdInput))) {
      setLoadError("대상 종류와 대상 ID는 함께 선택/입력해 주세요.");
      return;
    }
    if (actorIdInput !== "" && !isValidId(actorIdInput)) {
      setLoadError("행위자 사용자 ID를 숫자로 입력해 주세요.");
      return;
    }
    if (startDateInput && endDateInput && startDateInput > endDateInput) {
      setLoadError("시작일이 종료일보다 늦을 수 없어요.");
      return;
    }

    const query: AuditLogQuery = {};
    if (targetTypeInput !== "" && isValidId(targetIdInput)) {
      query.targetType = targetTypeInput;
      query.targetId = Number(targetIdInput);
    }
    if (actorIdInput !== "") query.actorUserId = Number(actorIdInput);
    if (actionTypeInput !== "") query.actionType = actionTypeInput;
    if (startDateInput) query.startDate = startDateInput;
    if (endDateInput) query.endDate = endDateInput;

    setAppliedQuery(query);
    setLoadError(null);
    setPage(0);
  }

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader eyebrow="전체 운영" title="감사 로그" description="관리자·시스템의 주요 조치 이력을 조회해요." />

      <form onSubmit={handleSubmit} className="surface mb-6 flex flex-col gap-4 p-5">
        <p className="text-xs text-muted">필요한 조건만 채워서 조합할 수 있어요. 전부 비워두면 전체를 최신순으로 보여줘요.</p>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <label htmlFor="target-type" className="mb-1.5 block text-sm font-bold text-ink">대상 종류</label>
            <Select
              id="target-type"
              value={targetTypeInput}
              onChange={(event) => {
                setTargetTypeInput(event.target.value as TargetType);
                setTargetIdInput(""); // 대상 종류가 바뀌면 이전 값은 의미가 없으니 비우고 새 placeholder를 보여준다
              }}
            >
              <option value="">전체</option>
              {Object.entries(targetTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label htmlFor="target-id" className="mb-1.5 block text-sm font-bold text-ink">대상 ID</label>
            <Input
              id="target-id"
              type="number"
              min={1}
              value={targetIdInput}
              onChange={(event) => setTargetIdInput(event.target.value)}
              disabled={targetTypeInput === ""}
              placeholder={targetTypeInput === "" ? "대상 종류를 먼저 선택하세요" : targetIdHints[targetTypeInput]}
            />
          </div>
          <div>
            <label htmlFor="actor-id" className="mb-1.5 block text-sm font-bold text-ink">행위자 사용자 ID</label>
            <Input id="actor-id" type="number" min={1} value={actorIdInput} onChange={(event) => setActorIdInput(event.target.value)} placeholder="예: 1" />
          </div>
          <div>
            <label htmlFor="action-type" className="mb-1.5 block text-sm font-bold text-ink">액션 타입</label>
            <Select id="action-type" value={actionTypeInput} onChange={(event) => setActionTypeInput(event.target.value as ActionType)}>
              <option value="">전체</option>
              {Object.entries(actionTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label htmlFor="start-date" className="mb-1.5 block text-sm font-bold text-ink">시작일</label>
            <Input id="start-date" type="date" value={startDateInput} onChange={(event) => setStartDateInput(event.target.value)} />
          </div>
          <div>
            <label htmlFor="end-date" className="mb-1.5 block text-sm font-bold text-ink">종료일</label>
            <Input id="end-date" type="date" value={endDateInput} onChange={(event) => setEndDateInput(event.target.value)} />
          </div>
        </div>

        <div className="flex justify-end">
          <Button type="submit" variant="outline">
            <Search size={16} />
            조회
          </Button>
        </div>
      </form>

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {!appliedQuery && !loading && (
        <EmptyState title="조회 버튼을 눌러 주세요" description="조건을 채우지 않고 조회하면 전체 로그를 최신순으로 보여줘요." />
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
