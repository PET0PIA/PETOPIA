import { AlertCircle, Check, X } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  getFairApplications,
  getFairCancelRequestQueue,
  getFairCancelRequests,
  reviewFairCancelRequest,
  type FairApplicationSummary,
  type FairCancelRequestItem,
  type FairCancelRequestQueueItem,
} from "../../api/fair";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { Textarea } from "../../components/ui/Textarea";
import { useConfirm } from "../../components/ui/useConfirm";

const statusLabels: Record<string, string> = {
  PENDING: "심사 대기",
  APPROVED: "승인됨(취소 확정)",
  REJECTED: "반려됨",
};
const statusTones: Record<string, "primary" | "sun" | "leaf" | "neutral"> = {
  PENDING: "sun",
  APPROVED: "neutral",
  REJECTED: "neutral",
};

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

export function FairCancelRequestReviewPage() {
  const { confirm, confirmDialog } = useConfirm();

  const [queue, setQueue] = useState<FairCancelRequestQueueItem[]>([]);
  const [queueLoading, setQueueLoading] = useState(true);
  const [queueError, setQueueError] = useState<string | null>(null);

  // 행사 ID를 직접 타이핑하지 않고 이름으로 찾도록, 전체 행사 목록(상태 무관)을 한 번 받아와
  // 검색용 드롭다운을 채운다.
  const [allFairs, setAllFairs] = useState<FairApplicationSummary[]>([]);
  const [allFairsError, setAllFairsError] = useState<string | null>(null);

  const [fairId, setFairId] = useState<number | null>(null);
  const [requests, setRequests] = useState<FairCancelRequestItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [reviewingId, setReviewingId] = useState<number | null>(null);
  const [reviewError, setReviewError] = useState<string | null>(null);

  const [rejectTarget, setRejectTarget] = useState<FairCancelRequestItem | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  // 행사를 빠르게 여러 번 바꿔 선택하면 먼저 보낸 요청의 응답이 나중에 도착할 수 있다 - 그
  // 응답으로 목록이 덮어써지면 지금 선택과 다른 행사의 취소 신청 이력이 보이면서, 승인·반려
  // 버튼은 여전히 지금 선택된(다른) fairId를 대상으로 동작하게 된다. 매 요청마다 번호를
  // 매겨서, 응답이 왔을 때 그게 여전히 "지금 선택"에 대한 요청인지 확인한다.
  const requestsRequestIdRef = useRef(0);

  // 승인/반려 후 큐를 새로고침할 때 재사용한다. 최초 마운트 시 큐를 받아오는 아래 useEffect는
  // queueLoading의 초기값이 이미 true라 이 함수 대신 별도로 fetch만 한다(react-hooks/set-state-in-effect
  // 회피 - effect 안에서 setState를 동기 호출하는 함수를 부르면 안 된다).
  async function loadQueue() {
    setQueueLoading(true);
    setQueueError(null);
    try {
      const data = await getFairCancelRequestQueue("PENDING");
      setQueue(data);
    } catch (error) {
      setQueue([]);
      setQueueError(error instanceof ApiError ? error.message : "취소 심사 대기 목록을 불러오지 못했어요.");
    } finally {
      setQueueLoading(false);
    }
  }

  useEffect(() => {
    let alive = true;
    getFairCancelRequestQueue("PENDING")
      .then((data) => {
        if (alive) setQueue(data);
      })
      .catch((error: unknown) => {
        if (!alive) return;
        setQueue([]);
        setQueueError(error instanceof ApiError ? error.message : "취소 심사 대기 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setQueueLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    let alive = true;
    getFairApplications()
      .then((data) => {
        if (alive) setAllFairs([...data].sort((a, b) => a.name.localeCompare(b.name, "ko")));
      })
      .catch((error: unknown) => {
        if (alive) setAllFairsError(error instanceof ApiError ? error.message : "행사 목록을 불러오지 못했어요.");
      });
    return () => {
      alive = false;
    };
  }, []);

  async function loadRequests(targetFairId: number) {
    const requestId = ++requestsRequestIdRef.current;
    setLoading(true);
    setLoadError(null);
    setReviewError(null);
    try {
      const data = await getFairCancelRequests(targetFairId);
      if (requestId !== requestsRequestIdRef.current) return; // 그 사이 다른 행사를 선택했으면 이 응답은 버린다
      setRequests(data);
    } catch (error) {
      if (requestId !== requestsRequestIdRef.current) return;
      setRequests([]);
      setLoadError(error instanceof ApiError ? error.message : "취소 신청 이력을 불러오지 못했어요.");
    } finally {
      if (requestId === requestsRequestIdRef.current) setLoading(false);
    }
  }

  function openFair(targetFairId: number) {
    setFairId(targetFairId);
    void loadRequests(targetFairId);
  }

  function handleSelectFair(value: string) {
    if (value === "") {
      requestsRequestIdRef.current += 1; // 진행 중이던 조회가 있었다면 응답이 와도 버려지도록 무효화한다
      setFairId(null);
      setRequests([]);
      return;
    }
    openFair(Number(value));
  }

  async function handleApprove(request: FairCancelRequestItem) {
    if (fairId === null) return;
    const proceed = await confirm({
      title: "취소 신청을 승인할까요?",
      description: "승인하면 이 행사가 즉시 취소 확정돼요. 되돌릴 수 없어요.",
      confirmLabel: "승인",
    });
    if (!proceed) return;

    setReviewingId(request.fairCancelRequestId);
    setReviewError(null);
    try {
      await reviewFairCancelRequest(fairId, request.fairCancelRequestId, { decision: "APPROVE" });
      await loadRequests(fairId);
      void loadQueue();
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "승인 처리에 실패했어요.");
    } finally {
      setReviewingId(null);
    }
  }

  async function handleReject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (fairId === null || !rejectTarget) return;
    if (rejectReason.trim() === "") {
      setReviewError("반려 사유를 입력해 주세요.");
      return;
    }

    setReviewingId(rejectTarget.fairCancelRequestId);
    setReviewError(null);
    try {
      await reviewFairCancelRequest(fairId, rejectTarget.fairCancelRequestId, {
        decision: "REJECT",
        rejectReason: rejectReason.trim(),
      });
      await loadRequests(fairId);
      void loadQueue();
      setRejectTarget(null);
      setRejectReason("");
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "반려 처리에 실패했어요.");
    } finally {
      setReviewingId(null);
    }
  }

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="전체 운영" title="행사 취소 신청 처리" description="행사 관리자가 신청한 취소를 검토하고 승인 또는 반려해요." />

      <div className="mb-6">
        <h2 className="mb-3 text-sm font-extrabold text-muted">심사 대기 중인 취소 신청</h2>
        {queueLoading ? (
          <div className="surface grid min-h-24 place-items-center text-sm text-muted">불러오는 중이에요...</div>
        ) : queueError ? (
          <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <p>{queueError}</p>
          </div>
        ) : queue.length === 0 ? (
          <EmptyState title="심사 대기 중인 취소 신청이 없어요." description="새 취소 신청이 들어오면 이곳에 표시돼요." />
        ) : (
          <Table>
            <thead>
              <tr className="border-b border-line text-xs font-bold text-muted">
                <th className="px-4 py-3">행사</th>
                <th className="px-4 py-3">사유</th>
                <th className="px-4 py-3">신청일</th>
                <th className="px-4 py-3" aria-label="심사" />
              </tr>
            </thead>
            <tbody>
              {queue.map((item) => (
                <tr key={item.fairCancelRequestId} className="border-b border-line last:border-0 hover:bg-page">
                  <td className="px-4 py-3 font-bold text-ink">{item.fairName}</td>
                  <td className="px-4 py-3 text-muted">{item.reason}</td>
                  <td className="px-4 py-3 text-muted">{formatDateTime(item.createdAt)}</td>
                  <td className="px-4 py-3 text-right">
                    <Button variant="outline" onClick={() => openFair(item.fairId)}>
                      심사하기
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </div>

      <div className="surface mb-6 p-5">
        <label htmlFor="fairSelect" className="mb-1.5 block text-sm font-bold text-ink">조회할 행사</label>
        <Select id="fairSelect" value={fairId ?? ""} onChange={(event) => handleSelectFair(event.target.value)}>
          <option value="">행사명으로 찾기...</option>
          {allFairs.map((fair) => (
            <option key={fair.fairId} value={fair.fairId}>{fair.name}</option>
          ))}
        </Select>
        {allFairsError && <p className="mt-1.5 text-xs font-bold text-primary-strong">{allFairsError}</p>}
      </div>

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {reviewError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{reviewError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="행사를 먼저 선택해 주세요." description="위 목록에서 행사를 선택하면 취소 신청 이력이 표시돼요." />
      )}

      {fairId !== null && loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>
      )}

      {fairId !== null && !loading && requests.length === 0 && !loadError && (
        <EmptyState title="취소 신청 이력이 없어요." description="이 행사에 대해 접수된 취소 신청이 아직 없어요." />
      )}

      {fairId !== null && !loading && requests.length > 0 && (
        <Table>
          <thead>
            <tr className="border-b border-line text-xs font-bold text-muted">
              <th className="px-4 py-3">사유</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3">신청일</th>
              <th className="px-4 py-3">검토일</th>
              <th className="px-4 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {requests.map((request) => (
              <tr key={request.fairCancelRequestId} className="border-b border-line last:border-0">
                <td className="px-4 py-3">
                  <p className="text-ink">{request.reason}</p>
                  {request.rejectReason && (
                    <p className="mt-1 text-xs text-primary-strong">반려 사유: {request.rejectReason}</p>
                  )}
                </td>
                <td className="px-4 py-3">
                  <Badge tone={statusTones[request.status] ?? "neutral"}>
                    {statusLabels[request.status] ?? request.status}
                  </Badge>
                </td>
                <td className="px-4 py-3 text-muted">{formatDateTime(request.createdAt)}</td>
                <td className="px-4 py-3 text-muted">{formatDateTime(request.reviewedAt)}</td>
                <td className="px-4 py-3">
                  {request.status === "PENDING" && (
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="outline"
                        onClick={() => {
                          setRejectTarget(request);
                          setRejectReason("");
                          setReviewError(null);
                        }}
                        disabled={reviewingId === request.fairCancelRequestId}
                      >
                        <X size={16} />반려
                      </Button>
                      <Button onClick={() => handleApprove(request)} disabled={reviewingId === request.fairCancelRequestId}>
                        <Check size={16} />
                        {reviewingId === request.fairCancelRequestId ? "처리 중..." : "승인"}
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <Dialog open={rejectTarget !== null} onClose={() => setRejectTarget(null)} title="취소 신청 반려">
        <form onSubmit={handleReject} className="space-y-4">
          <div>
            <label htmlFor="rejectReason" className="mb-1.5 block text-sm font-bold text-ink">반려 사유<span className="ml-1 text-primary-strong">*</span></label>
            <Textarea id="rejectReason" value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} placeholder="행사 관리자에게 안내할 반려 사유를 입력해 주세요." required />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setRejectTarget(null)}>취소</Button>
            <Button type="submit" disabled={reviewingId !== null}>{reviewingId !== null ? "처리 중..." : "반려 확정"}</Button>
          </div>
        </form>
      </Dialog>

      {confirmDialog}
    </div>
  );
}
