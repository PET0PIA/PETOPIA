import { AlertCircle, Check, Paperclip, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../api/client";
import {
  approveCancelRequest,
  getApplicationDetail,
  getCancelRequestsForFair,
  rejectCancelRequest,
  type ApplicationDetail,
  type CancelRequestStatus,
  type CancelRequestSummary,
} from "../../api/application";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { FairSelectorBar } from "../../components/fair-admin/FairSelectorBar";
import { useFairSelector } from "../../hooks/useFairSelector";
import { useConfirm } from "../../components/ui/useConfirm";

const REVIEW_TABS: { status: CancelRequestStatus; label: string }[] = [
  { status: "REQUESTED", label: "처리 대기" },
  { status: "APPROVED", label: "승인됨" },
  { status: "REJECTED", label: "반려됨" },
];

const statusTones: Record<CancelRequestStatus, "primary" | "sun" | "leaf" | "neutral"> = {
  REQUESTED: "sun",
  APPROVED: "leaf",
  REJECTED: "neutral",
};
const statusLabels: Record<CancelRequestStatus, string> = {
  REQUESTED: "처리 대기",
  APPROVED: "승인됨",
  REJECTED: "반려됨",
};

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

export function CancelRequestReviewPage() {
  const { fairId, setFairId, selectableFairs } = useFairSelector();
  const { confirm, confirmDialog } = useConfirm();
  const [activeStatus, setActiveStatus] = useState<CancelRequestStatus>("REQUESTED");
  const [queue, setQueue] = useState<CancelRequestSummary[]>([]);
  const [queueLoading, setQueueLoading] = useState(false);
  const [queueError, setQueueError] = useState<string | null>(null);

  const [selectedRequest, setSelectedRequest] = useState<CancelRequestSummary | null>(null);
  const [reviewing, setReviewing] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);

  const [applicationDetail, setApplicationDetail] = useState<ApplicationDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const queueRequestIdRef = useRef(0);
  const detailRequestIdRef = useRef(0);

  async function loadQueue(currentFairId: number, status: CancelRequestStatus) {
    const requestId = ++queueRequestIdRef.current;
    setQueueLoading(true);
    setQueueError(null);
    try {
      const data = await getCancelRequestsForFair(currentFairId, status);
      if (requestId !== queueRequestIdRef.current) return;
      setQueue(data);
    } catch (error) {
      if (requestId !== queueRequestIdRef.current) return;
      setQueue([]);
      setQueueError(error instanceof ApiError ? error.message : "취소 요청 목록을 불러오지 못했어요.");
    } finally {
      if (requestId === queueRequestIdRef.current) setQueueLoading(false);
    }
  }

  useEffect(() => {
    setSelectedRequest(null);
    if (fairId === null) return;
    void loadQueue(fairId, activeStatus);
  }, [fairId, activeStatus]);

  useEffect(() => {
  setApplicationDetail(null);
  setDetailError(null);
  if (!selectedRequest) return;

  const requestId = ++detailRequestIdRef.current;
  setDetailLoading(true);
  getApplicationDetail(selectedRequest.applicationId)
    .then((data) => {
      if (requestId !== detailRequestIdRef.current) return;
      setApplicationDetail(data);
    })
    .catch((error: unknown) => {
      if (requestId !== detailRequestIdRef.current) return;
      setDetailError(error instanceof ApiError ? error.message : "신청 정보를 불러오지 못했어요.");
    })
    .finally(() => {
      if (requestId === detailRequestIdRef.current) setDetailLoading(false);
    });
}, [selectedRequest]);

  async function handleApprove() {
    if (!selectedRequest || fairId === null) return;
    const proceed = await confirm({
      title: "취소 요청을 승인할까요?",
      description: "승인하면 신청이 취소 처리돼요. 이미 확정(부스 생성)된 상태였다면 부스도 함께 삭제돼요.",
      confirmLabel: "승인",
    });
    if (!proceed) return;

    setReviewing(true);
    setReviewError(null);
    try {
      await approveCancelRequest(selectedRequest.applicationId);
      setSelectedRequest(null);
      void loadQueue(fairId, activeStatus);
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "승인 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  async function handleReject() {
    if (!selectedRequest || fairId === null) return;
    const proceed = await confirm({
      title: "취소 요청을 반려할까요?",
      description: "반려하면 신청은 기존 상태 그대로 유지돼요.",
    });
    if (!proceed) return;

    setReviewing(true);
    setReviewError(null);
    try {
      await rejectCancelRequest(selectedRequest.applicationId);
      setSelectedRequest(null);
      void loadQueue(fairId, activeStatus);
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "반려 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="참가업체 관리" title="참가 취소 요청 심사" description="신청자가 보낸 취소 요청을 확인하고 승인 또는 반려해요." />

      <FairSelectorBar
        selectableFairs={selectableFairs}
        fairId={fairId}
        onFairIdChange={(id) => {
          if (!reviewing) setFairId(id);
        }}
      />

      {fairId !== null && (
        <div className="mb-6">
          <div className="mb-3 flex gap-1 border-b border-line">
            {REVIEW_TABS.map((tab) => (
              <button
                key={tab.status}
                type="button"
                disabled={reviewing}
                onClick={() => setActiveStatus(tab.status)}
                className={`px-3 py-2 text-sm font-bold disabled:cursor-not-allowed disabled:opacity-60 ${
                  activeStatus === tab.status
                    ? "border-b-2 border-primary-strong text-ink"
                    : "text-muted hover:text-ink"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {queueLoading ? (
            <div className="surface grid min-h-24 place-items-center text-sm text-muted">불러오는 중이에요...</div>
          ) : queueError ? (
            <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <p>{queueError}</p>
            </div>
          ) : queue.length === 0 ? (
            <EmptyState title="해당 상태의 취소 요청이 없어요." />
          ) : (
            <Table>
              <thead>
                <tr className="border-b border-line text-xs font-bold text-muted">
                  <th className="px-4 py-3">사업자명</th>
                  <th className="px-4 py-3">요청일</th>
                  <th className="px-4 py-3" aria-label="상세" />
                </tr>
              </thead>
              <tbody>
                {queue.map((request) => (
                  <tr key={request.cancelRequestId} className="border-b border-line last:border-0 hover:bg-page">
                    <td className="px-4 py-3 font-bold text-ink">{request.businessName}</td>
                    <td className="px-4 py-3 text-muted">{formatDateTime(request.requestedAt)}</td>
                    <td className="px-4 py-3 text-right">
                      <Button variant="outline" onClick={() => setSelectedRequest(request)} disabled={reviewing}>
                        상세보기
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </div>
      )}

      {selectedRequest && (
        <Card className="space-y-4 p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="mb-2 flex items-center gap-2">
                <h2 className="text-lg font-extrabold">{selectedRequest.businessName}</h2>
                <Badge tone={statusTones[selectedRequest.status]}>{statusLabels[selectedRequest.status]}</Badge>
              </div>
              <p className="text-sm text-muted">신청서 #{selectedRequest.applicationId} · 요청일 {formatDateTime(selectedRequest.requestedAt)}</p>
              {selectedRequest.decidedAt && <p className="mt-1 text-sm text-muted">처리일 {formatDateTime(selectedRequest.decidedAt)}</p>}
            </div>
            <div className="flex shrink-0 gap-2">
              {selectedRequest.status === "REQUESTED" && (
                <>
                  <Button variant="outline" onClick={handleReject} disabled={reviewing}><X size={16} />반려</Button>
                  <Button onClick={handleApprove} disabled={reviewing}><Check size={16} />승인</Button>
                </>
              )}
              <Button variant="outline" onClick={() => setSelectedRequest(null)}>닫기</Button>
            </div>
          </div>

          {reviewError && (
            <div className="flex items-start gap-3 border-t border-line pt-4 text-sm text-primary-strong">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <p>{reviewError}</p>
            </div>
          )}

          <div className="space-y-5 border-t border-line pt-5">
            {detailLoading ? (
              <p className="text-sm text-muted">신청 정보를 불러오는 중이에요...</p>
            ) : detailError ? (
              <p className="text-sm text-primary-strong">{detailError}</p>
            ) : applicationDetail?.applicationId === selectedRequest.applicationId ? (
              <>
                <div className="rounded-card border border-line bg-page p-4">
                  <h3 className="mb-3 text-sm font-extrabold text-muted">선택한 부스 슬롯</h3>
                  <ul className="space-y-2 text-sm text-ink">
                    {applicationDetail.slots.map((slot) => (
                      <li key={slot.boothSlotsId} className="flex justify-between">
                        <span>{slot.slotNumber}</span>
                        <span className="text-muted">{slot.priceAtSelection.toLocaleString()}원</span>
                      </li>
                    ))}
                  </ul>
                  {applicationDetail.finalPrice !== null && (
                    <div className="mt-3 flex justify-between border-t border-line pt-3 text-sm font-bold text-ink">
                      <span>참가비</span>
                      <span>{applicationDetail.finalPrice.toLocaleString()}원</span>
                    </div>
                  )}
                </div>

                <div className="rounded-card border border-line bg-page p-5">
                  <h3 className="mb-3 text-sm font-extrabold text-muted">신청 내용</h3>
                  <dl className="space-y-5 text-sm">
                    <div>
                      <dt className="mb-1.5 text-xs font-bold text-muted">참가 목적</dt>
                      <dd className="text-ink">{applicationDetail.purpose}</dd>
                    </div>
                    <div>
                      <dt className="mb-1.5 text-xs font-bold text-muted">판매·전시 품목</dt>
                      <dd className="text-ink">{applicationDetail.itemsDesc}</dd>
                    </div>
                    {applicationDetail.attachmentUrl && (
                      <div>
                        <dt className="mb-1.5 text-xs font-bold text-muted">첨부파일</dt>
                        <dd>
                          <a
                            href={applicationDetail.attachmentUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 font-bold text-primary-strong hover:underline"
                          >
                            <Paperclip size={14} />첨부파일 보기
                          </a>
                        </dd>
                      </div>
                    )}
                  </dl>
                </div>
              </>
            ) : null}

            <div className="rounded-card border border-line bg-page p-4">
              <h3 className="mb-3 text-sm font-extrabold text-muted">취소 사유</h3>
              <p className="whitespace-pre-wrap text-sm text-ink">{selectedRequest.reason}</p>
            </div>
          </div>
        </Card>
      )}

      {confirmDialog}
    </div>
  );
}