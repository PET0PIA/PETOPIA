import { AlertCircle, ChevronLeft, Paperclip, Pencil, XCircle } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { useEffect } from "react";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { Textarea } from "../../components/ui/Textarea";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import { createVendorFeePayment } from "../../api/payment";
import { useAuth } from "../../contexts/AuthContext";
import { getApplicationDetail, submitCancelRequest, type ApplicationDetail, type ApplicationStatus } from "../../api/application";
import {
  ALL_PAYMENT_METHODS,
  isTossConfigured,
  requestVendorFeePayment,
  type PaymentMethodOption,
} from "../../payments/toss";
import { PaymentMethodPicker } from "../../components/payment/PaymentMethodPicker";

const statusLabels: Record<ApplicationStatus, string> = {
  PENDING_REVIEW: "심사 대기",
  PAYMENT_PENDING: "참가비 결제 대기",
  CONFIRMED: "확정",
  REJECTED: "반려됨",
  CANCELED: "취소됨",
};
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

function BackLink() {
  return (
    <Link
      to="/participations/me"
      className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink"
    >
      <ChevronLeft size={16} />
      참가 신청 현황
    </Link>
  );
}

export function ApplicationDetailPage() {
  const { applicationId } = useParams<{ applicationId: string }>();
  const id = Number(applicationId);
  const idValid = Number.isInteger(id) && id > 0;

  if (!idValid) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="잘못된 신청서 주소예요."
            description="신청서 주소가 올바르지 않아요. 목록에서 다시 선택해 주세요."
            actionTo="/participations/me"
            actionLabel="참가 신청 현황으로"
          />
        </div>
      </div>
    );
  }

  // id별로 key를 줘서, 다른 신청서 상세로 이동할 때(같은 라우트라 컴포넌트가 재사용됨)
  // 이전 신청서의 상태가 잔류하지 않고 완전히 새로 마운트되게 한다
  // (MyFairApplicationDetailPage와 동일한 이유).
  return <ApplicationDetailContent key={id} id={id} />;
}

function ApplicationDetailContent({ id }: { id: number }) {
  const { user } = useAuth();
  const { confirm, confirmDialog } = useConfirm();
  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethodOption>("CARD");

  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const tossReady = isTossConfigured();

  useEffect(() => {
    let alive = true;
    getApplicationDetail(id)
      .then((res) => {
        if (alive) setDetail(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "신청서를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [id]);

  async function handlePay() {
    if (!detail || !user || detail.finalPrice === null) return;

    const proceed = await confirm({
      title: "참가비를 결제할까요?",
      description: `참가비 ${detail.finalPrice.toLocaleString()}원을 결제해요. 결제 후 신청이 확정돼요.`,
      confirmLabel: "결제",
    });
    if (!proceed) return;

    setPaying(true);
    setPayError(null);
    try {
      const created = await createVendorFeePayment(
        detail.applicationId,
        { fairId: detail.fairId, businessId: detail.businessId, amount: detail.finalPrice },
        user.userId
      );
      await requestVendorFeePayment({
        paymentId: created.paymentId,
        applicationId: detail.applicationId,
        orderId: created.orderId,
        amount: created.amount,
        orderName: `참가비 결제 (행사 #${detail.fairId})`,
        method: paymentMethod,
      });
      // 리다이렉트가 시작됐으므로 paying을 되돌리지 않는다(예약금 쪽과 동일 패턴).
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "결제를 준비하지 못했어요. 잠시 후 다시 시도해 주세요.");
      setPaying(false);
    }
  }

  async function handleCancelSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!detail) return;
    if (cancelReason.trim() === "") {
      setCancelError("취소 사유를 입력해 주세요.");
      return;
    }

    setCanceling(true);
    setCancelError(null);
    try {
      await submitCancelRequest(detail.applicationId, cancelReason.trim());
      setDetail({ ...detail, cancelRequestStatus: "REQUESTED", cancelable: false });
      setCancelDialogOpen(false);
      setCancelReason("");
    } catch (err) {
      setCancelError(err instanceof ApiError ? err.message : "취소 요청을 제출하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setCanceling(false);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <p className="py-16 text-center text-sm text-muted">신청서를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (loadError || !detail) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="신청서를 찾을 수 없어요."
            description={loadError ?? "목록에서 신청서를 다시 선택해 주세요."}
            actionTo="/participations/me"
            actionLabel="참가 신청 현황으로"
          />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <BackLink />

      <div className="mt-4 mb-6 flex items-start justify-between gap-4">
        <div>
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <Badge tone={statusTones[detail.status]}>{statusLabels[detail.status]}</Badge>
            {detail.cancelRequestStatus === "REQUESTED" && <Badge tone="sun">취소 요청중</Badge>}
            {detail.cancelRequestStatus === "REJECTED" && <Badge tone="neutral">취소 요청 반려</Badge>}
          </div>
          <h1 className="text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">참가 신청서</h1>
        </div>
        {detail.cancelRequestStatus === "REJECTED" && detail.cancelDecidedAt && (
          <p className="mt-2 text-sm text-muted">
            취소 요청이 {formatDateTime(detail.cancelDecidedAt)}에 반려됐어요. 필요하면 다시 취소 요청을 보낼 수 있어요.
          </p>
        )}
        {detail.cancelable && (
          <Button
            type="button"
            variant="outline"
            onClick={() => setCancelDialogOpen(true)}
            className="shrink-0"
          >
            <XCircle size={16} />취소 요청하기
          </Button>
        )}
        {detail.status === "PENDING_REVIEW" && (
          <Link
            to={`/participations/me/${detail.applicationId}/edit`}
            className="inline-flex min-h-11 items-center gap-2 rounded-button border border-line bg-card px-4 text-sm font-bold text-ink hover:bg-page"
          >
            <Pencil size={16} />
            수정하기
          </Link>
        )}
      </div>

      <div className="space-y-6">
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
          {(() => {
            const originalTotal = detail.slots.reduce((sum, slot) => sum + slot.priceAtSelection, 0);
            const discount = detail.finalPrice !== null ? originalTotal - detail.finalPrice : 0;
            return (
              <>
                <div className="flex justify-between border-t border-line pt-3 text-sm font-bold text-ink">
                  <span>합계</span>
                  <span>{originalTotal.toLocaleString()}원</span>
                </div>
                {detail.finalPrice !== null && discount > 0 && (
                  <div className="flex justify-between text-sm text-primary-strong">
                    <span>할인 적용</span>
                    <span>-{discount.toLocaleString()}원</span>
                  </div>
                )}
                {detail.finalPrice !== null && (
                  <div className="flex justify-between border-t border-line pt-3 text-sm font-extrabold text-ink">
                    <span>최종 참가비</span>
                    <span>{detail.finalPrice.toLocaleString()}원</span>
                  </div>
                )}
              </>
            );
          })()}

          {detail.status === "PAYMENT_PENDING" && detail.cancelRequestStatus !== "REQUESTED" && (
            <div className="border-t border-line pt-4">
              {tossReady ? (
                <PaymentMethodPicker
                  value={paymentMethod}
                  onChange={setPaymentMethod}
                  options={ALL_PAYMENT_METHODS}
                  disabled={paying}
                />
              ) : (
                <p className="rounded-button border border-dashed border-line bg-page p-4 text-center text-sm text-muted">
                  결제 설정이 없어요. 결제 클라이언트 키가 주입되지 않았어요.
                </p>
              )}
              {payError && (
                <div className="mt-3 flex items-start gap-2 text-sm text-primary-strong">
                  <AlertCircle size={16} className="mt-0.5 shrink-0" />
                  <p>{payError}</p>
                </div>
              )}
              <div className="mt-3 flex justify-end">
                <Button type="button" onClick={handlePay} disabled={paying || !tossReady}>
                  {paying ? "결제창을 여는 중..." : "결제하기"}
                </Button>
              </div>
            </div>
          )}
        </Card>

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">신청 내용</h3>
          <dl className="space-y-4">
            <Field label="신청 사업자" value={detail.businessName} />
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
                    첨부파일 보기
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

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">진행 이력</h3>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Field label="제출일시" value={formatDateTime(detail.submittedAt)} />
            <Field label="심사일시" value={formatDateTime(detail.reviewedAt)} />
          </dl>
        </Card>
      </div>

      <Dialog open={cancelDialogOpen} onClose={() => setCancelDialogOpen(false)} title="참가 취소 요청">
        <form onSubmit={handleCancelSubmit} className="space-y-4">
          <p className="text-sm text-muted">취소 요청을 보내면 행사 담당자가 확인 후 승인/반려를 결정해요.</p>
          <div className="flex items-start gap-2 rounded-button bg-primary-soft p-3 text-sm text-primary-strong">
            <AlertCircle size={16} className="mt-0.5 shrink-0" />
            <p>한 번 제출한 취소 요청은 철회할 수 없어요. 신중하게 결정해 주세요.</p>
          </div>
          <div>
            <label htmlFor="cancelReason" className="mb-1.5 block text-sm font-bold text-ink">취소 사유<span className="ml-1 text-primary-strong">*</span></label>
            <Textarea id="cancelReason" value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} placeholder="취소하려는 이유를 입력해 주세요." required />
          </div>
          {cancelError && <p className="text-sm font-bold text-primary-strong">{cancelError}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setCancelDialogOpen(false)}>닫기</Button>
            <Button type="submit" disabled={canceling}>{canceling ? "제출 중..." : "취소 요청 제출"}</Button>
          </div>
        </form>
      </Dialog>
      {confirmDialog}
    </div>
  );
}