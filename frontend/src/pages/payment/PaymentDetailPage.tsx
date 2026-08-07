import { AlertCircle, Search } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getPayment, type PaymentDetail } from "../../api/payment";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { formatAmount, formatDateTime, paymentTypeLabels, statusLabels, statusTone } from "./paymentDisplay";

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold text-muted">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

export function PaymentDetailPage() {
  const [searchParams] = useSearchParams();
  const [paymentIdInput, setPaymentIdInput] = useState("");
  const [detail, setDetail] = useState<PaymentDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  async function loadPayment(paymentId: number) {
    setPaymentIdInput(String(paymentId));
    setLoading(true);
    setLoadError(null);
    try {
      const data = await getPayment(paymentId);
      setDetail(data);
    } catch (error) {
      setDetail(null);
      setLoadError(error instanceof ApiError ? error.message : "결제 정보를 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }

  // 목록 페이지의 "상세" 링크(?id=)로 들어왔을 때 자동으로 채워서 조회한다.
  // loadPayment의 setState 호출이 effect 본문에서 "동기적으로" 실행되는 것으로 잡히지 않도록
  // 마이크로태스크로 한 틱 미룬다(react-hooks/set-state-in-effect).
  useEffect(() => {
    const idParam = Number(searchParams.get("id"));
    if (!Number.isInteger(idParam) || idParam <= 0) return;
    queueMicrotask(() => loadPayment(idParam));
  }, [searchParams]);

  function handleLoad(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(paymentIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setLoadError("결제 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    loadPayment(parsed);
  }

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="결제" title="결제 상세 조회" description="결제 ID로 결제 상세 내역을 확인해요." />

      <form onSubmit={handleLoad} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label htmlFor="payment-id-input" className="mb-1.5 block text-sm font-bold text-ink">조회할 결제 ID</label>
          <Input id="payment-id-input" className="input-no-spinner" type="number" min={1} value={paymentIdInput} onChange={(event) => setPaymentIdInput(event.target.value)} placeholder="예: test1" />
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
        <EmptyState title="결제 ID를 먼저 입력해 주세요." description="조회할 결제의 ID를 입력하고 불러오기를 누르면 상세 내역이 표시돼요." />
      )}

      {loading && <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {detail && !loading && (
        <div className="space-y-6">
          <Card className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="mb-2 flex items-center gap-2">
                <h2 className="text-lg font-extrabold">{paymentTypeLabels[detail.paymentType] ?? detail.paymentType}</h2>
                <Badge tone={statusTone[detail.status] ?? "neutral"}>{statusLabels[detail.status] ?? detail.status}</Badge>
              </div>
              <p className="text-sm text-muted">결제 #{detail.paymentId} · 행사 #{detail.fairId}</p>
            </div>
            <p className="text-2xl font-extrabold text-ink">{formatAmount(detail.amount)}</p>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">결제 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="결제 수단" value={detail.method} />
              <Field label="결제 요청 시각" value={formatDateTime(detail.createdAt)} />
              <Field label="결제 완료 시각" value={formatDateTime(detail.paidAt)} />
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">연관 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="결제자 사용자 ID" value={detail.payerUserId !== null ? `#${detail.payerUserId}` : "-"} />
              <Field label="참가업체(사업자) ID" value={detail.businessId !== null ? `#${detail.businessId}` : "-"} />
              <Field label="예약 ID" value={detail.reservationId !== null ? `#${detail.reservationId}` : "-"} />
              <Field label="참가신청 ID" value={detail.applicationId !== null ? `#${detail.applicationId}` : "-"} />
            </dl>
          </Card>
        </div>
      )}
    </div>
  );
}
