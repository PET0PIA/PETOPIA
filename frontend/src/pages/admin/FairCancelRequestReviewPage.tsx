import { AlertCircle, Check, Search, X } from "lucide-react";
import { useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  getFairCancelRequests,
  reviewFairCancelRequest,
  type FairCancelRequestItem,
} from "../../api/fair";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Input } from "../../components/ui/Input";
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

  const [fairIdInput, setFairIdInput] = useState("");
  const [fairId, setFairId] = useState<number | null>(null);
  const [requests, setRequests] = useState<FairCancelRequestItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [reviewingId, setReviewingId] = useState<number | null>(null);
  const [reviewError, setReviewError] = useState<string | null>(null);

  const [rejectTarget, setRejectTarget] = useState<FairCancelRequestItem | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  async function loadRequests(targetFairId: number) {
    setLoading(true);
    setLoadError(null);
    setReviewError(null);
    try {
      const data = await getFairCancelRequests(targetFairId);
      setRequests(data);
    } catch (error) {
      setRequests([]);
      setLoadError(error instanceof ApiError ? error.message : "취소 신청 이력을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }

  function handleLoadFair(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(fairIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setLoadError("행사 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    setFairId(parsed);
    void loadRequests(parsed);
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

      <form onSubmit={handleLoadFair} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
        <div className="flex-1">
          <span className="mb-1.5 block text-sm font-bold text-ink">조회할 행사 ID</span>
          <Input type="number" min={1} value={fairIdInput} onChange={(event) => setFairIdInput(event.target.value)} placeholder="예: 1" />
        </div>
        <Button type="submit" variant="outline"><Search size={16} />불러오기</Button>
      </form>

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
        <EmptyState title="행사 ID를 먼저 입력해 주세요." description="검토할 행사의 ID를 입력하고 불러오기를 누르면 취소 신청 이력이 표시돼요." />
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
            <span className="mb-1.5 block text-sm font-bold text-ink">반려 사유<span className="ml-1 text-primary-strong">*</span></span>
            <Textarea value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} placeholder="행사 관리자에게 안내할 반려 사유를 입력해 주세요." required />
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
