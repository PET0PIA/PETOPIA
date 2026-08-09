import { AlertCircle, Banknote, CheckCircle2, ShieldCheck, Store } from "lucide-react";
import { useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  confirmPayment,
  createFairOpeningPayment,
  createReservationDepositPayment,
  createVendorFeePayment,
  TEMP_PAYER_USER_ID,
  type PaymentDetail,
} from "../../api/payment";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { formatAmount, paymentTypeLabels, statusLabels, statusTone } from "./paymentDisplay";

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

function ResultCard({ result }: { result: PaymentDetail }) {
  return (
    <Card className="mt-4 flex flex-col gap-2 p-5">
      <div className="flex items-center gap-2">
        <span className="text-sm font-extrabold text-ink">{paymentTypeLabels[result.paymentType]}</span>
        <Badge tone={statusTone[result.status]}>{statusLabels[result.status]}</Badge>
      </div>
      <p className="text-sm text-muted">
        결제 #{result.paymentId} · {formatAmount(result.amount)}
        {result.orderId && <> · orderId {result.orderId}</>}
      </p>
    </Card>
  );
}

// 참가비 결제. 다른 두 결제유형과 달리 즉시 COMPLETED가 아니라 PENDING + orderId로 응답이 오고,
// 실제 완료는 아래 "결제 승인 확정" 섹션(토스 confirm)까지 이어져야 한다.
function VendorFeeSection() {
  const [applicationId, setApplicationId] = useState("");
  const [fairId, setFairId] = useState("");
  const [businessId, setBusinessId] = useState("");
  const [amount, setAmount] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<PaymentDetail | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedApplicationId = Number(applicationId);
    const parsedFairId = Number(fairId);
    const parsedBusinessId = Number(businessId);
    const parsedAmount = Number(amount);
    if (![parsedApplicationId, parsedFairId, parsedBusinessId, parsedAmount].every((value) => Number.isInteger(value) && value > 0)) {
      setError("신청 ID·행사 ID·업체 ID·금액 전부 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const created = await createVendorFeePayment(parsedApplicationId, { fairId: parsedFairId, businessId: parsedBusinessId, amount: parsedAmount });
      setResult(created);
    } catch (err) {
      setResult(null);
      setError(errorMessage(err, "참가비 결제 준비에 실패했어요."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="mb-10">
      <SectionHeader title="참가비 결제 생성" description="신청 상태가 승인(APPROVED)이어야 해요. 성공하면 PENDING 상태로 생성되고, 실제 완료는 아래 승인 확정까지 필요해요." />
      <Card className="p-6">
        <form onSubmit={handleSubmit} className="grid gap-3 sm:grid-cols-4 sm:items-end">
          <div>
            <label htmlFor="vf-application-id" className="mb-1.5 block text-sm font-bold text-ink">신청 ID</label>
            <Input id="vf-application-id" className="input-no-spinner" type="number" min={1} value={applicationId} onChange={(event) => setApplicationId(event.target.value)} placeholder="예: test1" />
          </div>
          <div>
            <label htmlFor="vf-fair-id" className="mb-1.5 block text-sm font-bold text-ink">행사 ID</label>
            <Input id="vf-fair-id" className="input-no-spinner" type="number" min={1} value={fairId} onChange={(event) => setFairId(event.target.value)} placeholder="예: test1" />
          </div>
          <div>
            <label htmlFor="vf-business-id" className="mb-1.5 block text-sm font-bold text-ink">업체 ID</label>
            <Input id="vf-business-id" className="input-no-spinner" type="number" min={1} value={businessId} onChange={(event) => setBusinessId(event.target.value)} placeholder="예: test1" />
          </div>
          <div>
            <label htmlFor="vf-amount" className="mb-1.5 block text-sm font-bold text-ink">금액</label>
            <Input id="vf-amount" type="number" min={1} value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="예: 300000" />
          </div>
          <div className="sm:col-span-4">
            <Button type="submit" disabled={submitting}>
              <Store size={16} />
              {submitting ? "생성 중..." : "참가비 결제 생성"}
            </Button>
          </div>
        </form>
        {error && (
          <p className="mt-3 flex items-start gap-2 text-sm text-primary-strong">
            <AlertCircle size={16} className="mt-0.5 shrink-0" />
            {error}
          </p>
        )}
        {result && <ResultCard result={result} />}
      </Card>
    </section>
  );
}

// 위에서 PENDING으로 만든 참가비 결제를, 토스 결제창 완료 후 받은 paymentKey로 승인 확정한다.
// 관리자 테스트 도구라 실제 위젯 없이 paymentKey를 직접 입력받는다.
function ConfirmSection() {
  const [paymentId, setPaymentId] = useState("");
  const [paymentKey, setPaymentKey] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<PaymentDetail | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedPaymentId = Number(paymentId);
    if (!Number.isInteger(parsedPaymentId) || parsedPaymentId <= 0) {
      setError("결제 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    if (!paymentKey.trim()) {
      setError("paymentKey를 입력해 주세요.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      // 관리자 테스트 도구라 로그인 사용자가 아니라 임시 결제자 ID로 호출한다.
      const confirmed = await confirmPayment(parsedPaymentId, paymentKey.trim(), TEMP_PAYER_USER_ID);
      setResult(confirmed);
    } catch (err) {
      setResult(null);
      setError(errorMessage(err, "결제 승인 확정에 실패했어요."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="mb-10">
      <SectionHeader title="결제 승인 확정" description="토스 결제창 완료 후 받은 paymentKey로 PENDING 결제를 확정해요. 승인거부는 402, 토스 장애는 503, PENDING이 아니면 409로 응답이 와요." />
      <Card className="p-6">
        <form onSubmit={handleSubmit} className="grid gap-3 sm:grid-cols-3 sm:items-end">
          <div>
            <label htmlFor="cf-payment-id" className="mb-1.5 block text-sm font-bold text-ink">결제 ID</label>
            <Input id="cf-payment-id" className="input-no-spinner" type="number" min={1} value={paymentId} onChange={(event) => setPaymentId(event.target.value)} placeholder="예: test1" />
          </div>
          <div className="sm:col-span-2">
            <label htmlFor="cf-payment-key" className="mb-1.5 block text-sm font-bold text-ink">paymentKey</label>
            <Input id="cf-payment-key" value={paymentKey} onChange={(event) => setPaymentKey(event.target.value)} placeholder="토스 결제창에서 받은 값" />
          </div>
          <div className="sm:col-span-3">
            <Button type="submit" disabled={submitting}>
              <ShieldCheck size={16} />
              {submitting ? "확정 중..." : "승인 확정"}
            </Button>
          </div>
        </form>
        {error && (
          <p className="mt-3 flex items-start gap-2 text-sm text-primary-strong">
            <AlertCircle size={16} className="mt-0.5 shrink-0" />
            {error}
          </p>
        )}
        {result && <ResultCard result={result} />}
      </Card>
    </section>
  );
}

// 예약금 결제. 참가비와 마찬가지로 PENDING + orderId로 응답이 오고, 실제 완료는
// 위 "결제 승인 확정" 섹션(토스 confirm)까지 이어져야 한다.
function ReservationDepositSection() {
  const [reservationId, setReservationId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<PaymentDetail | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(reservationId);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setError("예약 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      // 관리자 테스트 도구라 로그인 사용자가 아니라 임시 결제자 ID로 호출한다.
      const created = await createReservationDepositPayment(parsed, TEMP_PAYER_USER_ID);
      setResult(created);
    } catch (err) {
      setResult(null);
      setError(errorMessage(err, "예약금 결제에 실패했어요."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="mb-10">
      <SectionHeader title="예약금 결제 생성" description="예약이 결제대기 상태이고 예약금이 0원보다 커야 해요. 성공하면 PENDING 상태로 생성되고, 실제 완료는 위 승인 확정까지 필요해요." />
      <Card className="p-6">
        <form onSubmit={handleSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label htmlFor="rd-reservation-id" className="mb-1.5 block text-sm font-bold text-ink">예약 ID</label>
            <Input id="rd-reservation-id" className="input-no-spinner" type="number" min={1} value={reservationId} onChange={(event) => setReservationId(event.target.value)} placeholder="예: test1" />
          </div>
          <Button type="submit" disabled={submitting}>
            <Banknote size={16} />
            {submitting ? "생성 중..." : "예약금 결제"}
          </Button>
        </form>
        {error && (
          <p className="mt-3 flex items-start gap-2 text-sm text-primary-strong">
            <AlertCircle size={16} className="mt-0.5 shrink-0" />
            {error}
          </p>
        )}
        {result && <ResultCard result={result} />}
      </Card>
    </section>
  );
}

// 행사개설비 결제. fair 상태 검증 없이 요청 금액을 그대로 신뢰한다(모의결제, 즉시 COMPLETED).
function FairOpeningFeeSection() {
  const [fairId, setFairId] = useState("");
  const [amount, setAmount] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<PaymentDetail | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedFairId = Number(fairId);
    const parsedAmount = Number(amount);
    if (!Number.isInteger(parsedFairId) || parsedFairId <= 0 || !Number.isInteger(parsedAmount) || parsedAmount <= 0) {
      setError("행사 ID와 금액 모두 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const created = await createFairOpeningPayment(parsedFairId, parsedAmount);
      setResult(created);
    } catch (err) {
      setResult(null);
      setError(errorMessage(err, "행사개설비 결제에 실패했어요."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section>
      <SectionHeader title="행사개설비 결제 생성" description="fair 상태는 검증하지 않고 요청 금액을 그대로 써요. 완료 후 행사 상태 전이는 이번 구현 범위 밖이라 별도로 반영되지 않아요." />
      <Card className="p-6">
        <form onSubmit={handleSubmit} className="grid gap-3 sm:grid-cols-3 sm:items-end">
          <div>
            <label htmlFor="of-fair-id" className="mb-1.5 block text-sm font-bold text-ink">행사 ID</label>
            <Input id="of-fair-id" className="input-no-spinner" type="number" min={1} value={fairId} onChange={(event) => setFairId(event.target.value)} placeholder="예: test1" />
          </div>
          <div>
            <label htmlFor="of-amount" className="mb-1.5 block text-sm font-bold text-ink">금액</label>
            <Input id="of-amount" type="number" min={1} value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="예: 500000" />
          </div>
          <div>
            <Button type="submit" disabled={submitting}>
              <CheckCircle2 size={16} />
              {submitting ? "생성 중..." : "개설비 결제"}
            </Button>
          </div>
        </form>
        {error && (
          <p className="mt-3 flex items-start gap-2 text-sm text-primary-strong">
            <AlertCircle size={16} className="mt-0.5 shrink-0" />
            {error}
          </p>
        )}
        {result && <ResultCard result={result} />}
      </Card>
    </section>
  );
}

export function PaymentCreatePage() {
  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="결제" title="결제 생성·확정" description="결제 3종을 직접 생성하고, 참가비 결제는 승인 확정까지 테스트해요." />
      <VendorFeeSection />
      <ConfirmSection />
      <ReservationDepositSection />
      <FairOpeningFeeSection />
    </div>
  );
}
