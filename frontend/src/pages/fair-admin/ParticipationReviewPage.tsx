import { AlertCircle, Check, Paperclip, X } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  approveApplication,
  getApplicationDetail,
  getApplicationsForFair,
  rejectApplication,
  type ApplicationDetail,
  type ApplicationReviewSummary,
  type ApplicationStatus,
} from "../../api/application";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { Input } from "../../components/ui/Input";
import { Table } from "../../components/ui/Table";
import { Textarea } from "../../components/ui/Textarea";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { FairSelectorBar } from "../../components/fair-admin/FairSelectorBar";
import { useFairSelector } from "../../hooks/useFairSelector";
import { useConfirm } from "../../components/ui/useConfirm";

const statusLabels: Record<ApplicationStatus, string> = {
  PENDING_REVIEW: "심사 대기",
  PAYMENT_PENDING: "참가비 결제 대기",
  CONFIRMED: "확정",
  REJECTED: "반려됨",
  CANCELED: "취소됨",
};

const REVIEW_TABS: { status: ApplicationStatus; label: string }[] = [
  { status: "PENDING_REVIEW", label: "심사 대기" },
  { status: "PAYMENT_PENDING", label: "결제 대기" },
  { status: "CONFIRMED", label: "확정" },
  { status: "REJECTED", label: "반려" },
];

const statusTones: Record<ApplicationStatus, "primary" | "sun" | "leaf" | "neutral"> = {
  PENDING_REVIEW: "sun",
  PAYMENT_PENDING: "primary",
  CONFIRMED: "leaf",
  REJECTED: "neutral",
  CANCELED: "neutral",
};

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold text-muted">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

export function ParticipationReviewPage() {
  const { fairId, setFairId, selectableFairs } = useFairSelector();
  const { confirm, confirmDialog } = useConfirm();

  const [activeStatus, setActiveStatus] = useState<ApplicationStatus>("PENDING_REVIEW");
  const [queue, setQueue] = useState<ApplicationReviewSummary[]>([]);
  const [queueLoading, setQueueLoading] = useState(false);
  const [queueError, setQueueError] = useState<string | null>(null);

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [finalPriceInput, setFinalPriceInput] = useState("");
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reviewing, setReviewing] = useState(false);

  const queueRequestIdRef = useRef(0);
  const detailRequestIdRef = useRef(0);

  async function loadQueue(currentFairId: number, status: ApplicationStatus) {
    const requestId = ++queueRequestIdRef.current;
    setQueueLoading(true);
    setQueueError(null);
    try {
      const data = await getApplicationsForFair(currentFairId, status);
      if (requestId !== queueRequestIdRef.current) return; // 그 사이 다른 요청이 시작됐으면 이 응답은 버린다
      setQueue(data);
    } catch (error) {
      if (requestId !== queueRequestIdRef.current) return;
      setQueue([]);
      setQueueError(error instanceof ApiError ? error.message : "신청 목록을 불러오지 못했어요.");
    } finally {
      if (requestId === queueRequestIdRef.current) setQueueLoading(false);
    }
  }

  useEffect(() => {
    setSelectedId(null);
    setDetail(null);
    if (fairId === null) return;
    void loadQueue(fairId, activeStatus);
  }, [fairId, activeStatus]);

  async function openApplication(applicationId: number) {
    const requestId = ++detailRequestIdRef.current;
    setSelectedId(applicationId);
    setDetailLoading(true);
    setDetailError(null);
    setReviewError(null);
    try {
      const data = await getApplicationDetail(applicationId);
      if (requestId !== detailRequestIdRef.current) return; // 그 사이 다른 신청서를 열었으면 이 응답은 버린다
      setDetail(data);
    } catch (error) {
      if (requestId !== detailRequestIdRef.current) return;
      setDetail(null);
      setDetailError(error instanceof ApiError ? error.message : "신청서를 불러오지 못했어요.");
    } finally {
      if (requestId === detailRequestIdRef.current) setDetailLoading(false);
    }
  }

  function openApproveDialog() {
    setReviewError(null);
    setFinalPriceInput("");
    setApproveDialogOpen(true);
  }

  function openRejectDialog() {
    setReviewError(null);
    setRejectReason("");
    setRejectDialogOpen(true);
  }

  async function handleApproveSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedId || fairId === null) return;

    // 비워두면 슬롯 가격 합계로 서버가 자동 계산한다(ApplicationApproveRequest.finalPrice 참고).
    let finalPrice: number | undefined;
    if (finalPriceInput.trim() !== "") {
      const parsed = Number(finalPriceInput);
      if (!Number.isInteger(parsed) || parsed < 0) {
        setReviewError("참가비는 0 이상의 숫자로 입력해 주세요.");
        return;
      }
      finalPrice = parsed;
    }

    const proceed = await confirm({
      title: "신청을 승인할까요?",
      description: finalPrice !== undefined
        ? `참가비 ${finalPrice.toLocaleString()}원으로 승인해요. 승인하면 신청자에게 결제 안내가 발송돼요.`
        : "참가비는 선택한 슬롯 가격 합계로 자동 계산돼요. 승인하면 신청자에게 결제 안내가 발송돼요.",
      confirmLabel: "승인",
      danger: false,
    });
    if (!proceed) return;

    setReviewing(true);
    setReviewError(null);
    try {
      const result = await approveApplication(selectedId, finalPrice);
      setDetail((previous) => previous && {
        ...previous,
        status: result.status,
        finalPrice: result.finalPrice,
        reviewedAt: result.reviewedAt,
      });
      setApproveDialogOpen(false);
      void loadQueue(fairId, activeStatus);
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "승인 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  async function handleRejectSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedId || fairId === null) return;
    if (rejectReason.trim() === "") {
      setReviewError("반려 사유를 입력해 주세요.");
      return;
    }

    const proceed = await confirm({
      title: "신청을 반려할까요?",
      description: "반려하면 신청자에게 반려 사유가 담긴 안내가 발송돼요.",
      confirmLabel: "반려",
    });
    if (!proceed) return;

    setReviewing(true);
    setReviewError(null);
    try {
      const result = await rejectApplication(selectedId, rejectReason.trim());
      setDetail((previous) => previous && {
        ...previous,
        status: result.status,
        rejectReason: result.rejectReason,
        reviewedAt: result.reviewedAt,
      });
      setRejectDialogOpen(false);
      void loadQueue(fairId, activeStatus);
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "반려 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  const isPendingReview = detail?.status === "PENDING_REVIEW";

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="참가업체 관리" title="참가 신청 심사" description="신청서를 확인하고 승인 또는 반려해요." />

      <FairSelectorBar selectableFairs={selectableFairs} fairId={fairId} onFairIdChange={setFairId} />

      {fairId !== null && (
        <div className="mb-6">
          <div className="mb-3 flex gap-1 border-b border-line">
            {REVIEW_TABS.map((tab) => (
              <button
                key={tab.status}
                type="button"
                onClick={() => setActiveStatus(tab.status)}
                className={`px-3 py-2 text-sm font-bold ${
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
            <EmptyState title="해당 상태의 신청서가 없어요." />
          ) : (
            <Table>
              <thead>
                <tr className="border-b border-line text-xs font-bold text-muted">
                  <th className="px-4 py-3">사업자명</th>
                  <th className="px-4 py-3">신청일</th>
                  <th className="px-4 py-3">참가비</th>
                  <th className="px-4 py-3" aria-label="상세" />
                </tr>
              </thead>
              <tbody>
                {queue.map((application) => (
                  <tr key={application.applicationId} className="border-b border-line last:border-0 hover:bg-page">
                    <td className="px-4 py-3 font-bold text-ink">{application.businessName}</td>
                    <td className="px-4 py-3 text-muted">{formatDateTime(application.submittedAt)}</td>
                    <td className="px-4 py-3 text-muted">
                      {application.finalPrice !== null ? `${application.finalPrice.toLocaleString()}원` : "-"}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button variant="outline" onClick={() => void openApplication(application.applicationId)}>
                        {activeStatus === "PENDING_REVIEW" ? "심사하기" : "상세보기"}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </div>
      )}

      {detailLoading && <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {detailError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{detailError}</p>
        </div>
      )}

      {detail && !detailLoading && (
        <div className="space-y-6">
          <Card className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="mb-2 flex items-center gap-2">
                <h2 className="text-lg font-extrabold">신청서 #{detail.applicationId}</h2>
                <Badge tone={statusTones[detail.status]}>
                  {statusLabels[detail.status]}
                </Badge>
              </div>
              {detail.rejectReason && <p className="mt-2 text-sm text-primary-strong">반려 사유: {detail.rejectReason}</p>}
              {detail.finalPrice !== null && <p className="mt-2 text-sm text-muted">참가비: {detail.finalPrice.toLocaleString()}원</p>}
            </div>
            <div className="flex shrink-0 gap-2">
              {isPendingReview && (
                <>
                  <Button variant="outline" onClick={openRejectDialog} disabled={reviewing}><X size={16} />반려</Button>
                  <Button onClick={openApproveDialog} disabled={reviewing}><Check size={16} />승인</Button>
                </>
              )}
              <Button
                variant="outline"
                onClick={() => {
                  setSelectedId(null);
                  setDetail(null);
                }}
              >
                닫기
              </Button>
            </div>
          </Card>

          {reviewError && (
            <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <p>{reviewError}</p>
            </div>
          )}

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">선택한 부스 슬롯</h3>
            <ul className="space-y-1 text-sm text-ink">
              {detail.slots.map((slot) => (
                <li key={slot.boothSlotsId} className="flex justify-between">
                  <span>{slot.slotNumber}</span>
                  <span className="text-muted">{slot.priceAtSelection.toLocaleString()}원</span>
                </li>
              ))}
            </ul>
            <div className="flex justify-between border-t border-line pt-3 text-sm font-bold text-ink">
              <span>합계</span>
              <span>{detail.slots.reduce((sum, slot) => sum + slot.priceAtSelection, 0).toLocaleString()}원</span>
            </div>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">신청 내용</h3>
            <dl className="space-y-4">
              <Field label="참가 목적" value={detail.purpose} />
              <Field label="판매·전시 품목" value={detail.itemsDesc} />
              {detail.attachmentUrl && (
                <div>
                  <dt className="text-xs font-bold text-muted">첨부파일</dt>
                  <dd className="mt-1 text-sm text-ink">
                    <a
                      href={detail.attachmentUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 font-bold text-primary-strong hover:underline"
                    >
                      <Paperclip size={14} />
                      첨부파일 다운로드
                    </a>
                  </dd>
                </div>
              )}
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">담당자 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="이름" value={detail.managerName} />
              <Field label="연락처" value={detail.managerPhone} />
              <Field label="이메일" value={detail.managerEmail} />
            </dl>
          </Card>
        </div>
      )}

      <Dialog open={approveDialogOpen} onClose={() => setApproveDialogOpen(false)} title="신청 승인">
        <form onSubmit={handleApproveSubmit} className="space-y-4">
          <div>
            <label htmlFor="finalPriceInput" className="mb-1.5 block text-sm font-bold text-ink">참가비(원, 선택)</label>
            <Input
              id="finalPriceInput"
              type="number"
              min={0}
              value={finalPriceInput}
              onChange={(event) => setFinalPriceInput(event.target.value)}
              placeholder="비워두면 슬롯 가격 합계로 자동 계산돼요"
            />
          </div>
          {reviewError && <p className="text-sm font-bold text-primary-strong">{reviewError}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setApproveDialogOpen(false)}>취소</Button>
            <Button type="submit" disabled={reviewing}>{reviewing ? "처리 중..." : "승인"}</Button>
          </div>
        </form>
      </Dialog>

      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} title="신청 반려">
        <form onSubmit={handleRejectSubmit} className="space-y-4">
          <div>
            <label htmlFor="rejectReason" className="mb-1.5 block text-sm font-bold text-ink">반려 사유<span className="ml-1 text-primary-strong">*</span></label>
            <Textarea id="rejectReason" value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} placeholder="신청자에게 안내할 반려 사유를 입력해 주세요." required />
          </div>
          {reviewError && <p className="text-sm font-bold text-primary-strong">{reviewError}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setRejectDialogOpen(false)}>취소</Button>
            <Button type="submit" disabled={reviewing}>{reviewing ? "처리 중..." : "반려 확정"}</Button>
          </div>
        </form>
      </Dialog>

      {confirmDialog}
    </div>
  );
}