import { AlertCircle, Check, Paperclip, X } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  approveBusiness,
  getBusinessesForReview,
  getBusinessReviewDetail,
  rejectBusiness,
  revokeBusiness,
  type BusinessApprovalStatus,
  type BusinessReviewDetail,
  type BusinessReviewSummary,
} from "../../api/business";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { Table } from "../../components/ui/Table";
import { Textarea } from "../../components/ui/Textarea";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { useConfirm } from "../../components/ui/useConfirm";

const statusLabels: Record<BusinessApprovalStatus, string> = {
  PENDING_REVIEW: "심사 대기",
  APPROVED: "승인됨",
  REJECTED: "반려됨",
  REVOKED: "취소됨",
};

const REVIEW_TABS: { status: BusinessApprovalStatus; label: string }[] = [
  { status: "PENDING_REVIEW", label: "심사 대기" },
  { status: "APPROVED", label: "승인" },
  { status: "REJECTED", label: "반려" },
  { status: "REVOKED", label: "취소" },
];

const statusTones: Record<BusinessApprovalStatus, "primary" | "sun" | "leaf" | "neutral"> = {
  PENDING_REVIEW: "sun",
  APPROVED: "leaf",
  REJECTED: "primary",
  REVOKED: "primary",
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

export function BusinessReviewPage() {
  const { confirm, confirmDialog } = useConfirm();

  const [activeStatus, setActiveStatus] = useState<BusinessApprovalStatus>("PENDING_REVIEW");
  const [queue, setQueue] = useState<BusinessReviewSummary[]>([]);
  const [queueLoading, setQueueLoading] = useState(false);
  const [queueError, setQueueError] = useState<string | null>(null);

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<BusinessReviewDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [revokeDialogOpen, setRevokeDialogOpen] = useState(false);
  const [revokeReason, setRevokeReason] = useState("");
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reviewing, setReviewing] = useState(false);

  const queueRequestIdRef = useRef(0);
  const detailRequestIdRef = useRef(0);

  async function loadQueue(status: BusinessApprovalStatus) {
    const requestId = ++queueRequestIdRef.current;
    setQueueLoading(true);
    setQueueError(null);
    try {
      const data = await getBusinessesForReview(status);
      if (requestId !== queueRequestIdRef.current) return; // 그 사이 다른 요청이 시작됐으면 이 응답은 버린다
      setQueue(data);
    } catch (error) {
      if (requestId !== queueRequestIdRef.current) return;
      setQueue([]);
      setQueueError(error instanceof ApiError ? error.message : "사업자 목록을 불러오지 못했어요.");
    } finally {
      if (requestId === queueRequestIdRef.current) setQueueLoading(false);
    }
  }

  useEffect(() => {
    detailRequestIdRef.current += 1;
    setSelectedId(null);
    setDetail(null);
    setDetailLoading(false);
    setDetailError(null);
    void loadQueue(activeStatus);
  }, [activeStatus]);

  async function openBusiness(businessId: number) {
    const requestId = ++detailRequestIdRef.current;
    setSelectedId(businessId);
    setDetailLoading(true);
    setDetailError(null);
    setReviewError(null);
    try {
      const data = await getBusinessReviewDetail(businessId);
      if (requestId !== detailRequestIdRef.current) return; // 그 사이 다른 사업자를 열었으면 이 응답은 버린다
      setDetail(data);
    } catch (error) {
      if (requestId !== detailRequestIdRef.current) return;
      setDetail(null);
      setDetailError(error instanceof ApiError ? error.message : "사업자 정보를 불러오지 못했어요.");
    } finally {
      if (requestId === detailRequestIdRef.current) setDetailLoading(false);
    }
  }

  async function handleApprove() {
    if (!selectedId) return;

    const proceed = await confirm({
      title: "사업자를 승인할까요?",
      description: "승인하면 신청자에게 VENDOR 권한이 부여되고, 부스 참가 신청을 진행할 수 있어요.",
      confirmLabel: "승인",
      danger: false,
    });
    if (!proceed) return;

    setReviewing(true);
    setReviewError(null);
    try {
      const result = await approveBusiness(selectedId);
      setDetail((previous) => previous && {
        ...previous,
        approvalStatus: result.approvalStatus,
        reviewedAt: result.reviewedAt,
      });
      void loadQueue(activeStatus);
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "승인 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  function openRejectDialog() {
    setReviewError(null);
    setRejectReason("");
    setRejectDialogOpen(true);
  }

  async function handleRejectSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedId) return;
    if (rejectReason.trim() === "") {
      setReviewError("반려 사유를 입력해 주세요.");
      return;
    }

    const proceed = await confirm({
      title: "사업자를 반려할까요?",
      description: "반려하면 신청자에게 반려 사유가 담긴 안내가 발송돼요.",
      confirmLabel: "반려",
    });
    if (!proceed) return;

    setReviewing(true);
    setReviewError(null);
    try {
      const result = await rejectBusiness(selectedId, rejectReason.trim());
      setDetail((previous) => previous && {
        ...previous,
        approvalStatus: result.approvalStatus,
        rejectReason: result.rejectReason,
        reviewedAt: result.reviewedAt,
      });
      setRejectDialogOpen(false);
      void loadQueue(activeStatus);
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "반려 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  function openRevokeDialog() {
    setReviewError(null);
    setRevokeReason("");
    setRevokeDialogOpen(true);
  }

  async function handleRevokeSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedId) return;
    if (revokeReason.trim() === "") {
      setReviewError("취소 사유를 입력해 주세요.");
      return;
    }

    const proceed = await confirm({
      title: "이 사업자를 취소 처리할까요?",
      description: "취소하면 이 사업자로 진행 중이던 참가 신청이 전부 취소되고(결제된 건은 환불), 다른 승인된 사업자가 없으면 VENDOR 권한도 회수돼요.",
      confirmLabel: "취소 처리",
    });
    if (!proceed) return;

    setReviewing(true);
    setReviewError(null);
    try {
      const result = await revokeBusiness(selectedId, revokeReason.trim());
      setDetail((previous) => previous && {
        ...previous,
        approvalStatus: result.approvalStatus,
        rejectReason: result.rejectReason,
        reviewedAt: result.reviewedAt,
      });
      setRevokeDialogOpen(false);
      void loadQueue(activeStatus);
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "취소 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  const isPendingReview = detail?.approvalStatus === "PENDING_REVIEW";
  const isApproved = detail?.approvalStatus === "APPROVED";

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="사업자 관리" title="사업자 등록 심사" description="첨부서류를 확인하고 승인·반려하거나, 승인된 사업자를 취소 처리해요." />

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
          <EmptyState title="해당 상태의 사업자가 없어요." />
        ) : (
          <Table>
            <thead>
              <tr className="border-b border-line text-xs font-bold text-muted">
                <th className="px-4 py-3">업체명</th>
                <th className="px-4 py-3">대표자명</th>
                <th className="px-4 py-3">사업자등록번호</th>
                <th className="px-4 py-3">신청일</th>
                <th className="px-4 py-3" aria-label="상세" />
              </tr>
            </thead>
            <tbody>
              {queue.map((business) => (
                <tr key={business.businessId} className="border-b border-line last:border-0 hover:bg-page">
                  <td className="px-4 py-3 font-bold text-ink">{business.name}</td>
                  <td className="px-4 py-3 text-muted">{business.ceoName}</td>
                  <td className="px-4 py-3 text-muted">{business.bizRegNo}</td>
                  <td className="px-4 py-3 text-muted">{formatDateTime(business.createdAt)}</td>
                  <td className="px-4 py-3 text-right">
                    <Button variant="outline" onClick={() => void openBusiness(business.businessId)}>
                      {activeStatus === "PENDING_REVIEW" ? "심사하기" : "상세보기"}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </div>

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
                <h2 className="text-lg font-extrabold">{detail.name}</h2>
                <Badge tone={statusTones[detail.approvalStatus]}>{statusLabels[detail.approvalStatus]}</Badge>
              </div>
              {detail.rejectReason && <p className="mt-2 text-sm text-primary-strong">사유: {detail.rejectReason}</p>}
            </div>
            <div className="flex shrink-0 gap-2">
              {isPendingReview && (
                <>
                  <Button variant="outline" onClick={openRejectDialog} disabled={reviewing}><X size={16} />반려</Button>
                  <Button onClick={() => void handleApprove()} disabled={reviewing}><Check size={16} />승인</Button>
                </>
              )}
              {isApproved && (
                <Button variant="outline" onClick={openRevokeDialog} disabled={reviewing}><X size={16} />취소 처리</Button>
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
            <h3 className="text-sm font-extrabold text-muted">사업자 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-2">
              <Field label="대표자명" value={detail.ceoName} />
              <Field label="사업자등록번호" value={detail.bizRegNo} />
              <Field label="개업일자" value={detail.startDate} />
              <Field label="연락처" value={detail.phone} />
              <Field label="사업장 주소" value={detail.address} />
              <Field label="웹사이트" value={detail.website ?? "-"} />
            </dl>
            {detail.documentUrl ? (
              <div>
                <dt className="text-xs font-bold text-muted">사업자등록증</dt>
                <dd className="mt-1 text-sm text-ink">
                  <a
                    href={detail.documentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 font-bold text-primary-strong hover:underline"
                  >
                    <Paperclip size={14} />
                    첨부서류 다운로드
                  </a>
                </dd>
              </div>
            ) : (
              <p className="text-xs text-muted">레거시 자동승인 건이라 첨부서류가 없어요.</p>
            )}
          </Card>
        </div>
      )}

      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} title="사업자 반려">
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

      <Dialog open={revokeDialogOpen} onClose={() => setRevokeDialogOpen(false)} title="사업자 취소 처리">
        <form onSubmit={handleRevokeSubmit} className="space-y-4">
          <div>
            <label htmlFor="revokeReason" className="mb-1.5 block text-sm font-bold text-ink">취소 사유<span className="ml-1 text-primary-strong">*</span></label>
            <Textarea id="revokeReason" value={revokeReason} onChange={(event) => setRevokeReason(event.target.value)} placeholder="예: 조작된 서류로 확인됨" required />
          </div>
          {reviewError && <p className="text-sm font-bold text-primary-strong">{reviewError}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setRevokeDialogOpen(false)}>취소</Button>
            <Button type="submit" disabled={reviewing}>{reviewing ? "처리 중..." : "취소 확정"}</Button>
          </div>
        </form>
      </Dialog>

      {confirmDialog}
    </div>
  );
}