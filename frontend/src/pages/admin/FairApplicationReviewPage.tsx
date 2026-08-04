import { AlertCircle, Check, Search, X } from "lucide-react";
import { useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  getFairApplication,
  reviewFairApplication,
  type FairApplicationDetail,
} from "../../api/fair";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { Input } from "../../components/ui/Input";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Textarea } from "../../components/ui/Textarea";

const statusLabels: Record<string, string> = {
  RECEIVED: "심사 대기",
  REJECTED: "반려됨",
  EXPIRED: "만료됨",
  PAYMENT_PENDING: "개설비 결제 대기",
  PREPARING: "준비 중",
  IN_PROGRESS: "진행 중",
  ENDED: "종료",
};

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

function formatDate(value: string | null) {
  return value ?? "-";
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold text-muted">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

export function FairApplicationReviewPage() {
  const [fairIdInput, setFairIdInput] = useState("");
  const [detail, setDetail] = useState<FairApplicationDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reviewing, setReviewing] = useState(false);

  async function handleLoad(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(fairIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setLoadError("행사 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    setLoading(true);
    setLoadError(null);
    setReviewError(null);
    try {
      const data = await getFairApplication(parsed);
      setDetail(data);
    } catch (error) {
      setDetail(null);
      setLoadError(error instanceof ApiError ? error.message : "신청서를 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }

  async function handleApprove() {
    if (!detail) return;
    setReviewing(true);
    setReviewError(null);
    try {
      const result = await reviewFairApplication(detail.fairId, { decision: "APPROVE" });
      setDetail({ ...detail, status: result.status, reviewedAt: result.reviewedAt, paymentDueAt: result.paymentDueAt, rejectReason: result.rejectReason });
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "승인 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  async function handleReject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!detail) return;
    if (rejectReason.trim() === "") {
      setReviewError("반려 사유를 입력해 주세요.");
      return;
    }

    setReviewing(true);
    setReviewError(null);
    try {
      const result = await reviewFairApplication(detail.fairId, { decision: "REJECT", rejectReason: rejectReason.trim() });
      setDetail({ ...detail, status: result.status, reviewedAt: result.reviewedAt, paymentDueAt: result.paymentDueAt, rejectReason: result.rejectReason });
      setRejectDialogOpen(false);
      setRejectReason("");
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "반려 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  const isPendingReview = detail?.status === "RECEIVED";

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="전체 운영" title="행사 등록 신청 검토" description="신청서를 확인하고 승인 또는 반려해요." />

      <form onSubmit={handleLoad} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
        <div className="flex-1">
          <span className="mb-1.5 block text-sm font-bold text-ink">검토할 행사(신청서) ID</span>
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

      {!detail && !loading && (
        <EmptyState title="신청서 ID를 먼저 입력해 주세요." description="검토할 행사 신청서의 ID를 입력하고 불러오기를 누르면 상세 내용이 표시돼요." />
      )}

      {loading && <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {detail && !loading && (
        <div className="space-y-6">
          <Card className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="mb-2 flex items-center gap-2">
                <h2 className="text-lg font-extrabold">{detail.name}</h2>
                <Badge tone={isPendingReview ? "sun" : detail.status === "REJECTED" ? "neutral" : "leaf"}>{statusLabels[detail.status] ?? detail.status}</Badge>
              </div>
              <p className="text-sm text-muted">신청서 #{detail.fairId} · 신청자 #{detail.applicantUserId}</p>
              {detail.rejectReason && <p className="mt-2 text-sm text-primary-strong">반려 사유: {detail.rejectReason}</p>}
              {detail.paymentDueAt && <p className="mt-2 text-sm text-muted">개설비 결제 기한: {formatDateTime(detail.paymentDueAt)}</p>}
            </div>
            {isPendingReview && (
              <div className="flex shrink-0 gap-2">
                <Button variant="outline" onClick={() => setRejectDialogOpen(true)} disabled={reviewing}><X size={16} />반려</Button>
                <Button onClick={handleApprove} disabled={reviewing}><Check size={16} />{reviewing ? "처리 중..." : "승인"}</Button>
              </div>
            )}
          </Card>

          {reviewError && (
            <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <p>{reviewError}</p>
            </div>
          )}

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">기본 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-2">
              <Field label="카테고리" value={detail.category ?? "-"} />
              <Field label="포스터 이미지" value={detail.posterImageUrl ?? "-"} />
              <Field label="행사 소개" value={detail.description ?? "-"} />
              <Field label="유의사항" value={detail.noticeText ?? "-"} />
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">장소</h3>
            <dl className="grid gap-4 sm:grid-cols-2">
              <Field label="장소명" value={detail.placeName ?? "-"} />
              <Field label="실내/실외" value={detail.indoorOutdoor === "INDOOR" ? "실내" : detail.indoorOutdoor === "OUTDOOR" ? "실외" : "-"} />
              <Field label="주소" value={detail.address ?? "-"} />
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">일정</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="참가업체 모집" value={`${formatDate(detail.vendorRecruitStartDate)} ~ ${formatDate(detail.vendorRecruitEndDate)}`} />
              <Field label="사전예약" value={`${formatDate(detail.reservationStartDate)} ~ ${formatDate(detail.reservationEndDate)}`} />
              <Field label="행사 운영" value={`${formatDate(detail.operationStartDate)} ~ ${formatDate(detail.operationEndDate)}`} />
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">예약 정책</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="예약금" value={detail.reservationFee !== null ? `${detail.reservationFee.toLocaleString("ko-KR")}원` : "-"} />
              <Field label="취소 가능 기한" value={detail.reservationCancelDeadlineHours !== null ? `${detail.reservationCancelDeadlineHours}시간` : "-"} />
              <Field label="변경 가능 기한" value={detail.reservationChangeDeadlineHours !== null ? `${detail.reservationChangeDeadlineHours}시간` : "-"} />
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">담당자 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="이름" value={detail.managerName} />
              <Field label="연락처" value={detail.managerPhone ?? "-"} />
              <Field label="이메일" value={detail.managerEmail} />
            </dl>
          </Card>
        </div>
      )}

      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} title="신청 반려">
        <form onSubmit={handleReject} className="space-y-4">
          <div>
            <span className="mb-1.5 block text-sm font-bold text-ink">반려 사유<span className="ml-1 text-primary-strong">*</span></span>
            <Textarea value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} placeholder="신청자에게 안내할 반려 사유를 입력해 주세요." required />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setRejectDialogOpen(false)}>취소</Button>
            <Button type="submit" disabled={reviewing}>{reviewing ? "처리 중..." : "반려 확정"}</Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}
