import { AlertTriangle, CalendarClock, CreditCard } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getFairOpeningFeeSummary, type FairOpeningFeeSummary } from "../../api/fair";
import { createFairOpeningPayment, getPayments, type PaymentDetail } from "../../api/payment";
import { useAuth } from "../../contexts/AuthContext";
import {
  ALL_PAYMENT_METHODS,
  isTossConfigured,
  requestFairOpeningFeePayment,
  type PaymentMethodOption,
} from "../../payments/toss";
import { PaymentMethodPicker } from "../../components/payment/PaymentMethodPicker";
import {
  formatAmount,
  formatDateTime,
  paymentStatusLabel,
  paymentStatusTone,
  paymentTypeLabels,
  refundReasonLabels,
  refundRequestedByDomainLabels,
  refundStatusLabels,
} from "./paymentDisplay";

// PAYMENT_PENDING이면서 결제 가능(payable)한 상태가 아닐 때 보여줄 안내. PAYMENT_PENDING인데
// 기한이 지난 경우는 별도로 판단해서 이 맵 밖에서 처리한다(배치가 아직 EXPIRED로 안 돌렸을 수 있어서).
const STATUS_MESSAGE: Partial<Record<FairOpeningFeeSummary["status"], string>> = {
  RECEIVED: "아직 승인 전이에요. 승인되면 개설비 금액과 결제 기한이 안내돼요.",
  REJECTED: "반려된 신청이라 개설비를 결제할 수 없어요.",
  EXPIRED: "결제 기한이 지나 신청이 만료됐어요.",
  PREPARING: "이미 개설비 결제가 완료된 행사예요.",
  IN_PROGRESS: "이미 개설비 결제가 완료된 행사예요.",
  ENDED: "이미 종료된 행사예요.",
};

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold text-muted">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

// 개설비 결제가 완료된 후엔 /admin/payments?id= 결제 상세 화면과 같은 구성(결제정보·환불정보·
// 연관정보)으로 보여준다(2026-08-23) - 완료된 결제는 "결제하기" 흐름이 아니라 "무엇을 얼마에
// 언제 냈는지 확인하는" 화면이라 관리자 결제상세 페이지와 같은 정보가 필요하다고 판단해서다.
function PaidOpeningFeeDetail({ detail }: { detail: PaymentDetail }) {
  return (
    <div className="space-y-4">
      <Card className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-extrabold">{paymentTypeLabels[detail.paymentType] ?? detail.paymentType}</h2>
            <Badge tone={paymentStatusTone(detail.status, detail.refundStatus)}>{paymentStatusLabel(detail.status, detail.refundStatus)}</Badge>
          </div>
        </div>
        <p className="text-2xl font-extrabold text-ink">{formatAmount(detail.amount)}</p>
      </Card>

      <Card className="space-y-4 p-6">
        <h3 className="text-sm font-extrabold text-muted">결제 정보</h3>
        <dl className="grid gap-4 sm:grid-cols-3">
          <Field label="결제 ID" value={`#${detail.paymentId}`} />
          <Field label="결제 금액" value={formatAmount(detail.amount)} />
          <Field label="결제 수단" value={detail.method} />
          <Field label="결제 요청 시각" value={formatDateTime(detail.createdAt)} />
          <Field label="결제 완료 시각" value={formatDateTime(detail.paidAt)} />
        </dl>

        {detail.refundId !== null && (
          <>
            <h3 className="pt-2 text-sm font-extrabold text-muted">환불 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="환불 ID" value={`#${detail.refundId}`} />
              <Field label="환불 상태" value={refundStatusLabels[detail.refundStatus ?? ""] ?? detail.refundStatus ?? "-"} />
              <Field label="환불 금액" value={detail.refundAmount !== null ? formatAmount(detail.refundAmount) : "-"} />
              <Field label="환불 사유" value={refundReasonLabels[detail.refundReason ?? ""] ?? detail.refundReason ?? "-"} />
              <Field label="요청 도메인" value={refundRequestedByDomainLabels[detail.refundRequestedByDomain ?? ""] ?? detail.refundRequestedByDomain ?? "-"} />
              <Field label="환불 요청 시각" value={formatDateTime(detail.refundRequestedAt)} />
              <Field label="환불 처리 완료 시각" value={formatDateTime(detail.refundProcessedAt)} />
            </dl>
          </>
        )}
      </Card>

      <Card className="space-y-4 p-6">
        <h3 className="text-sm font-extrabold text-muted">연관 정보</h3>
        <dl className="grid gap-4 sm:grid-cols-3">
          <Field label="행사ID · 행사명" value={`#${detail.fairId} · ${detail.fairName ?? "-"}`} />
          <Field label="행사 담당자" value={detail.fairManagerName ?? "-"} />
          <Field label="행사 담당자 연락처" value={detail.fairManagerPhone ?? "-"} />
          <Field label="행사 담당자 이메일" value={detail.fairManagerEmail ?? "-"} />
        </dl>
      </Card>
    </div>
  );
}

function formatDueAt(dueAt: string) {
  // 백엔드가 LocalDateTime을 오프셋 없이 내려주는데, 서버·DB·컨테이너가 전부 Asia/Seoul로
  // 고정돼 있어(Dockerfile / docker-compose의 TZ) 브라우저 로컬 시각으로 파싱해도 어긋나지 않는다.
  const date = new Date(dueAt);
  if (Number.isNaN(date.getTime())) return dueAt;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function FairOpeningFeePaymentPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const id = Number(fairId);
  const idValid = Number.isInteger(id) && id > 0;
  const { user } = useAuth();

  const [summary, setSummary] = useState<FairOpeningFeeSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 이 행사의 개설비(FAIR_OPENING_FEE) 결제 이력 - 결제ID만 짧게 보여주는 데 쓴다(2026-08-22,
  // 결제 상세를 표로 다 보여주는 건 굳이 필요 없다고 판단해서 뺌). 못 불러와도 페이지 나머지는
  // 그대로 쓸 수 있어야 하니 실패해도 loadError로 전체를 막지 않고 조용히 빈 배열로 둔다.
  const [payments, setPayments] = useState<PaymentDetail[]>([]);

  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethodOption>("CARD");
  // Date.now()를 렌더 중에 직접 부르면 impure(react-hooks/purity)라 state 초기화로 한 번만 잡아둔다.
  // 이 페이지는 라이브 카운트다운이 필요 없어(기한이 보통 며칠 단위) 타이머로 갱신하지 않는다.
  const [now] = useState(() => Date.now());

  useEffect(() => {
    if (!idValid) return;
    let alive = true;
    getFairOpeningFeeSummary(id)
      .then((res) => {
        if (alive) setSummary(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "개설비 정보를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    // fairId가 바뀌는 사이(또는 재조회 실패 시) 이전 행사의 결제 이력이 화면에 남아있지
    // 않도록 먼저 비운다(2026-08-22 코드레빗 리뷰 반영, PR #230).
    setPayments([]);
    getPayments({ fairId: id, paymentType: "FAIR_OPENING_FEE", size: 50 })
      .then((res) => {
        if (alive) setPayments(res.content);
      })
      .catch(() => {
        // 결제 이력은 부가 정보라 실패해도 조용히 넘어간다 - 페이지 본 목적(결제/안내)은
        // summary 로딩 성공 여부로만 판단한다.
      });
    return () => {
      alive = false;
    };
  }, [id, idValid]);

  if (!idValid) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <EmptyState
          title="잘못된 주소예요."
          description="행사 주소가 올바르지 않아요."
          actionTo="/"
          actionLabel="홈으로"
        />
      </div>
    );
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <p className="py-16 text-center text-sm text-muted">개설비 정보를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (loadError || !summary) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <EmptyState
          title="개설비 정보를 볼 수 없어요."
          description={loadError ?? "개설비 정보를 불러오지 못했어요."}
          actionTo="/"
          actionLabel="홈으로"
        />
      </div>
    );
  }

  const tossReady = isTossConfigured();
  // 취소 승인(FairCancelRequestService#review)은 status를 안 바꾸고 canceledAt만 채운다 -
  // status만 보면 취소된 행사도 여전히 PAYMENT_PENDING 등으로 보여서 결제 가능한 것처럼
  // 나온다(2026-08-23, MyFairApplicationDetailPage의 resolveDisplayStatus와 같은 이유).
  const canceled = summary.canceledAt !== null;
  const dueAtMs = summary.paymentDueAt ? new Date(summary.paymentDueAt).getTime() : null;
  const dueExpired = dueAtMs !== null && !Number.isNaN(dueAtMs) && dueAtMs <= now;
  const payable = !canceled && summary.status === "PAYMENT_PENDING" && summary.openingFeeAmount !== null && summary.paymentDueAt !== null && !dueExpired;

  const nonPayableMessage = payable
    ? null
    : canceled
      ? null
      : summary.status === "PAYMENT_PENDING" && dueExpired
        ? "결제 기한이 지났어요. 곧 신청이 만료될 예정이니 관리자에게 문의해 주세요."
        : (STATUS_MESSAGE[summary.status] ?? "지금은 개설비를 결제할 수 없는 상태예요.");
  // 개설비 결제가 이미 끝난 행사(PREPARING/IN_PROGRESS)이거나 취소된 행사는 결제 기한이 더
  // 이상 의미가 없으니 카드 자체를 뺀다(2026-08-22, 취소는 2026-08-23 추가) - 취소된 행사는
  // 결제 완료 여부와 무관하게 관리자 결제상세와 같은 "조회" 화면으로만 보여준다.
  const paidCompleted = summary.status === "PREPARING" || summary.status === "IN_PROGRESS" || canceled;
  // 결제가 실제로 완료된 경우에만 안내 문구를 숨긴다 - 실패/만료 등 미완료 결제 시도가
  // 있어도 결제 불가 안내는 그대로 보여줘야 한다(2026-08-22 코드레빗 리뷰 반영, PR #230).
  const showNonPayableMessage = nonPayableMessage !== null && !paidCompleted;

  async function handlePay() {
    if (!payable || paying) return;
    if (!user) {
      setPayError("로그인이 풀렸어요. 다시 로그인한 뒤 이어가 주세요.");
      return;
    }

    setPayError(null);
    setPaying(true);
    try {
      const created = await createFairOpeningPayment(id, user.userId);
      await requestFairOpeningFeePayment({
        paymentId: created.paymentId,
        fairId: id,
        orderId: created.orderId,
        amount: created.amount,
        orderName: `${summary!.name} 개설비`,
        method: paymentMethod,
      });
      // 리다이렉트가 시작됐으므로 paying을 되돌리지 않는다(버튼이 다시 눌리면 안 된다).
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "결제를 시작하지 못했어요. 잠시 후 다시 시도해 주세요.");
      setPaying(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader
        eyebrow={paidCompleted ? "개설비 결제 상세" : "개설비 결제"}
        title={summary.name}
        description={paidCompleted ? undefined : "행사를 개설하려면 개설비 결제를 마쳐야 해요."}
      />

      {canceled && (
        <Card className="mb-4 flex items-start gap-2 p-4 text-sm">
          <AlertTriangle size={16} className="mt-0.5 shrink-0 text-muted" />
          <span className="text-muted">이 행사는 {formatDueAt(summary.canceledAt!)}에 취소가 확정됐어요.</span>
        </Card>
      )}

      {/* 결제 완료된 행사는 "결제하기" 흐름이 필요 없으니, 관리자 결제상세(/admin/payments?id=)와
          같은 구성으로 무엇을 얼마에 냈는지 보여준다(2026-08-23). payments[0]이 최신 결제
          시도(getPayments가 created_at DESC로 내려줌) - FAIR_OPENING_FEE는 재시도가 거의
          없어 사실상 그 행사의 결제 그 자체다. 취소된 행사인데 결제 시도 이력조차 없으면
          (RECEIVED/PAYMENT_PENDING 상태에서 그대로 취소된 경우) 보여줄 결제 정보가 없다. */}
      {paidCompleted && payments.length > 0 && <PaidOpeningFeeDetail detail={payments[0]} />}
      {paidCompleted && payments.length === 0 && canceled && (
        <EmptyState title="결제 이력이 없어요." description="개설비를 결제하기 전에 취소된 행사예요." />
      )}

      {!paidCompleted && (
        <Card className="mb-4 p-5">
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted">개설비</span>
            <span className="text-lg font-extrabold text-ink">
              {summary.openingFeeAmount !== null ? `${summary.openingFeeAmount.toLocaleString()}원` : "미확정"}
            </span>
          </div>
        </Card>
      )}

      {!paidCompleted && (summary.paymentDueAt || payments.length > 0) && (
        <Card className="mb-4 flex items-center justify-between gap-2 p-4 text-sm">
          <span className="flex items-center gap-2 text-muted">
            {summary.paymentDueAt && (
              <>
                <CalendarClock size={16} className="shrink-0" />
                결제 기한 <b className="text-ink">{formatDueAt(summary.paymentDueAt)}</b>까지
              </>
            )}
          </span>
          {payments.length > 0 && (
            <span className="shrink-0 text-muted">
              결제 ID <span className="font-bold text-ink">#{payments[0].paymentId}</span>
            </span>
          )}
        </Card>
      )}

      {showNonPayableMessage && (
        <Card className="mb-6 flex items-start gap-2 p-4 text-sm">
          <AlertTriangle size={16} className="mt-0.5 shrink-0 text-muted" />
          <span className="text-muted">{nonPayableMessage}</span>
        </Card>
      )}

      {payable && (
        <Card className="mb-6 p-6">
          <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
            <CreditCard size={16} />
            결제 수단
          </div>
          {tossReady ? (
            <>
              <PaymentMethodPicker
                value={paymentMethod}
                onChange={setPaymentMethod}
                options={ALL_PAYMENT_METHODS}
                disabled={paying}
              />
              <p className="mt-3 text-xs leading-5 text-muted">
                <b className="text-ink">결제하기</b>를 누르면 토스페이먼츠 결제창이 열려요.
              </p>
            </>
          ) : (
            <div className="grid place-items-center gap-1 rounded-button border border-dashed border-line bg-page py-10 text-center text-sm text-muted">
              <p className="font-bold text-ink">결제 설정이 없어요</p>
              <p>결제 클라이언트 키가 주입되지 않았어요. 관리자에게 문의해 주세요.</p>
            </div>
          )}
        </Card>
      )}

      {payError && <p className="mb-4 text-sm font-bold text-primary-strong">{payError}</p>}

      <div className="flex items-center justify-end gap-3">
        <Link
          to="/"
          className="inline-flex min-h-11 items-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page"
        >
          홈으로
        </Link>
        {payable && (
          // paying 중엔 버튼이 disabled라 기본 커서(cursor-not-allowed, "금지" 아이콘)가
          // 뜨는데, 방금 누른 버튼 위에 계속 그게 보이면 거슬리니 이 순간만 대기 커서로 바꾼다.
          <Button disabled={!tossReady || paying} onClick={handlePay} className={paying ? "!cursor-wait" : undefined}>
            {paying ? "결제창을 여는 중…" : "결제하기"}
          </Button>
        )}
      </div>
    </div>
  );
}
