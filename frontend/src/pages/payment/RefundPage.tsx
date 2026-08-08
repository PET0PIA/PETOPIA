import { AlertCircle, Search } from "lucide-react";
import { useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import { getRefund, getRefundsByPayment, type RefundDetail, type RefundReason, type RequestedByDomain } from "../../api/refund";
import { Card } from "../../components/ui/Card";
import { Button } from "../../components/ui/Button";
import { Input } from "../../components/ui/Input";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { formatAmount, formatDateTime } from "./paymentDisplay";

const refundReasonLabels: Record<RefundReason, string> = {
  USER_CANCEL: "관람객 자진 예약취소",
  FAIR_CANCEL_USER: "행사취소로 인한 관람객예약 일괄취소",
  VENDOR_CANCEL: "참가업체 자진취소",
  FAIR_CANCEL_VENDOR: "행사취소로 인한 참가업체 환불",
  OPENING_FEE_MANUAL: "행사 개설비 환불(관리자 수동)",
};

const requestedByDomainLabels: Record<RequestedByDomain, string> = {
  RESERVATION: "예약 도메인",
  FAIR: "행사 도메인",
  VENDOR: "참여업체 도메인",
  PAYMENT_ADMIN: "결제 관리자",
};

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

function RefundCard({ refund }: { refund: RefundDetail }) {
  return (
    <Card className="space-y-4 p-6">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-lg font-extrabold text-ink">환불 #{refund.refundId}</h3>
        <p className="text-xl font-extrabold text-ink">{formatAmount(refund.refundAmount)}</p>
      </div>
      <dl className="grid gap-4 sm:grid-cols-3">
        <div>
          <dt className="text-xs font-bold text-muted">환불 사유</dt>
          <dd className="mt-1 text-sm text-ink">{refundReasonLabels[refund.refundReason] ?? refund.refundReason}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">요청 도메인</dt>
          <dd className="mt-1 text-sm text-ink">{requestedByDomainLabels[refund.requestedByDomain] ?? refund.requestedByDomain}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">상태</dt>
          <dd className="mt-1 text-sm text-ink">{refund.status}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">대상 결제 ID</dt>
          <dd className="mt-1 text-sm text-ink">#{refund.paymentId}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">대상 예약 ID</dt>
          <dd className="mt-1 text-sm text-ink">{refund.reservationId !== null ? `#${refund.reservationId}` : "-"}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">요청 시각</dt>
          <dd className="mt-1 text-sm text-ink">{formatDateTime(refund.requestedAt)}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">처리 완료 시각</dt>
          <dd className="mt-1 text-sm text-ink">{formatDateTime(refund.processedAt)}</dd>
        </div>
      </dl>
    </Card>
  );
}

// 결제 한 건에 걸린 환불 이력 조회. 결제당 환불은 최대 1건이라 배열이지만 0개 또는 1개만 온다.
function RefundsByPaymentSection() {
  const [paymentIdInput, setPaymentIdInput] = useState("");
  const [refunds, setRefunds] = useState<RefundDetail[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(paymentIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setError("결제 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await getRefundsByPayment(parsed);
      setRefunds(data);
    } catch (err) {
      setRefunds(null);
      setError(errorMessage(err, "환불 이력을 불러오지 못했어요."));
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="mb-10">
      <SectionHeader title="결제별 환불 이력 조회" description="결제 ID로 그 결제에 걸린 환불 내역을 확인해요." />

      <form onSubmit={handleSubmit} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label htmlFor="rf-payment-id" className="mb-1.5 block text-sm font-bold text-ink">결제 ID</label>
          <Input id="rf-payment-id" className="input-no-spinner" type="number" min={1} value={paymentIdInput} onChange={(event) => setPaymentIdInput(event.target.value)} placeholder="예: test1" />
        </div>
        <Button type="submit" variant="outline" disabled={loading}>
          <Search size={16} />조회
        </Button>
      </form>

      {error && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      {loading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {refunds && !loading && (
        refunds.length === 0
          ? <EmptyState title="이 결제엔 환불 이력이 없어요" description="아직 환불된 적 없는 결제예요." />
          : <div className="space-y-4">{refunds.map((refund) => <RefundCard key={refund.refundId} refund={refund} />)}</div>
      )}
    </section>
  );
}

// 환불 ID로 단건 상세 조회.
function RefundDetailSection() {
  const [refundIdInput, setRefundIdInput] = useState("");
  const [refund, setRefund] = useState<RefundDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(refundIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setError("환불 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await getRefund(parsed);
      setRefund(data);
    } catch (err) {
      setRefund(null);
      setError(errorMessage(err, "환불 정보를 불러오지 못했어요."));
    } finally {
      setLoading(false);
    }
  }

  return (
    <section>
      <SectionHeader title="환불 상세 조회" description="환불 ID로 환불 건 하나를 바로 조회해요." />

      <form onSubmit={handleSubmit} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label htmlFor="rf-refund-id" className="mb-1.5 block text-sm font-bold text-ink">환불 ID</label>
          <Input id="rf-refund-id" className="input-no-spinner" type="number" min={1} value={refundIdInput} onChange={(event) => setRefundIdInput(event.target.value)} placeholder="예: test1" />
        </div>
        <Button type="submit" variant="outline" disabled={loading}>
          <Search size={16} />조회
        </Button>
      </form>

      {error && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      {loading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {refund && !loading && <RefundCard refund={refund} />}
    </section>
  );
}

export function RefundPage() {
  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="결제" title="환불 조회" description="결제별 환불 이력을 확인하거나 환불 ID로 바로 상세를 조회해요." />
      <RefundsByPaymentSection />
      <RefundDetailSection />
    </div>
  );
}
