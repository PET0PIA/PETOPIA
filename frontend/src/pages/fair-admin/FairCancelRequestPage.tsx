import { AlertCircle, Ban } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import { createFairCancelRequest, getFairCancelRequests, type FairCancelRequestItem } from "../../api/fair";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Table } from "../../components/ui/Table";
import { Textarea } from "../../components/ui/Textarea";
import { useConfirm } from "../../components/ui/useConfirm";
import { useFairSelector } from "../../contexts/FairSelectorContext";

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

export function FairCancelRequestPage() {
  const { confirm, confirmDialog } = useConfirm();

  // 콘솔 상단 바의 "관리 행사" 선택기가 현재 행사를 정한다.
  const { fairId } = useFairSelector();

  const [requests, setRequests] = useState<FairCancelRequestItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // 상단 선택기의 행사가 바뀌면 그 행사의 취소 신청 이력을 다시 불러오고 작성 폼을 초기화한다.
  useEffect(() => {
    setSubmitError(null);
    setReason("");
    if (fairId === null) return;
    const currentFairId = fairId;
    let ignore = false;
    setLoading(true);
    setLoadError(null);
    getFairCancelRequests(currentFairId)
      .then((data) => { if (!ignore) setRequests(data); })
      .catch((error) => {
        if (!ignore) {
          setRequests([]);
          setLoadError(error instanceof ApiError ? error.message : "취소 신청 이력을 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [fairId]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (fairId === null) return;
    if (reason.trim() === "") {
      setSubmitError("취소 사유를 입력해 주세요.");
      return;
    }

    const proceed = await confirm({
      title: "행사 취소를 신청할까요?",
      description: "취소 신청은 SUPER_ADMIN 검토를 거쳐 승인되면 즉시 취소가 확정돼요. 신중히 진행해 주세요.",
      confirmLabel: "취소 신청",
    });
    if (!proceed) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const created = await createFairCancelRequest(fairId, reason.trim());
      setRequests((previous) => [created, ...previous]);
      setReason("");
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "취소 신청에 실패했어요.");
    } finally {
      setSubmitting(false);
    }
  }

  const pendingRequest = requests.find((request) => request.status === "PENDING") ?? null;

  return (
    <div className="mx-auto max-w-4xl py-2">
      <PageHeader
        eyebrow="행사 관리자"
        title="행사 취소 신청"
        description="담당 행사를 더 이상 진행할 수 없을 때 취소를 신청해요. 신청은 SUPER_ADMIN 검토 후 승인되면 확정돼요."
      />

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 취소 신청 이력이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      )}

      {fairId !== null && loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>
      )}

      {fairId !== null && !loading && (
        <div className="space-y-6">
          {pendingRequest ? (
            <Card className="flex items-start gap-3 p-5 text-sm">
              <Ban size={18} className="mt-0.5 shrink-0 text-muted" />
              <div>
                <p className="font-bold text-ink">이미 접수된 취소 신청이 심사를 기다리고 있어요.</p>
                <p className="mt-1 text-muted">사유: {pendingRequest.reason}</p>
                <p className="mt-1 text-xs text-muted">신청일: {formatDateTime(pendingRequest.createdAt)}</p>
              </div>
            </Card>
          ) : (
            <Card className="p-6">
              <h3 className="mb-4 text-sm font-extrabold text-muted">새 취소 신청</h3>
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label htmlFor="reason" className="mb-1.5 block text-sm font-bold text-ink">취소 사유<span className="ml-1 text-primary-strong">*</span></label>
                  <Textarea
                    id="reason"
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    placeholder="예: 행사 장소 계약 취소로 더 이상 진행이 불가능해요."
                    required
                  />
                </div>
                {submitError && <p className="text-sm font-bold text-primary-strong">{submitError}</p>}
                <div className="flex justify-end">
                  <Button type="submit" disabled={submitting}>{submitting ? "신청 중..." : "취소 신청"}</Button>
                </div>
              </form>
            </Card>
          )}

          <div>
            <h3 className="mb-3 text-sm font-extrabold text-muted">신청 이력</h3>
            {requests.length === 0 ? (
              <EmptyState title="취소 신청 이력이 없어요." description="이 행사에 대해 접수된 취소 신청이 아직 없어요." />
            ) : (
              <Table>
                <thead>
                  <tr className="border-b border-line text-xs font-bold text-muted">
                    <th className="px-4 py-3">사유</th>
                    <th className="px-4 py-3">상태</th>
                    <th className="px-4 py-3">신청일</th>
                    <th className="px-4 py-3">검토일</th>
                  </tr>
                </thead>
                <tbody>
                  {requests.map((request) => (
                    <tr key={request.fairCancelRequestId} className="border-b border-line last:border-0">
                      <td className="px-4 py-3">
                        <p className="text-ink">{request.reason}</p>
                        {request.rejectReason && (
                          <p className="mt-2 flex items-start gap-1.5 rounded-button bg-primary-soft px-2.5 py-1.5 text-sm font-bold text-primary-strong">
                            <AlertCircle size={14} className="mt-0.5 shrink-0" />
                            반려 사유: {request.rejectReason}
                          </p>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <Badge tone={statusTones[request.status] ?? "neutral"}>
                          {statusLabels[request.status] ?? request.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-muted">{formatDateTime(request.createdAt)}</td>
                      <td className="px-4 py-3 text-muted">{formatDateTime(request.reviewedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </div>
        </div>
      )}

      {confirmDialog}
    </div>
  );
}
