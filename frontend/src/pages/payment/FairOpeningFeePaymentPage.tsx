import { AlertTriangle, CalendarClock, CreditCard } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
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
  const dueAtMs = summary.paymentDueAt ? new Date(summary.paymentDueAt).getTime() : null;
  const dueExpired = dueAtMs !== null && !Number.isNaN(dueAtMs) && dueAtMs <= now;
  const payable = summary.status === "PAYMENT_PENDING" && summary.openingFeeAmount !== null && summary.paymentDueAt !== null && !dueExpired;

  const nonPayableMessage = payable
    ? null
    : summary.status === "PAYMENT_PENDING" && dueExpired
      ? "결제 기한이 지났어요. 곧 신청이 만료될 예정이니 관리자에게 문의해 주세요."
      : (STATUS_MESSAGE[summary.status] ?? "지금은 개설비를 결제할 수 없는 상태예요.");
  // 개설비 결제가 이미 끝난 행사(PREPARING/IN_PROGRESS)는 결제 기한이 더 이상 의미가
  // 없으니 카드 자체를 뺀다(2026-08-22).
  const paidCompleted = summary.status === "PREPARING" || summary.status === "IN_PROGRESS";
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
        eyebrow="개설비 결제"
        title={summary.name}
        description="행사를 개설하려면 개설비 결제를 마쳐야 해요."
      />

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

      {/* 결제 기한 + 결제ID를 한 카드에 같이 보여준다(2026-08-22) - 결제 상세 표는 굳이
          필요 없다고 판단해서 뺐고, 결제ID만 이 카드 오른쪽에 짧게 붙여둔다. payments[0]이
          최신 시도(getPayments가 created_at DESC로 내려줌). 결제 완료 후엔 기한 표시가
          없어지니(paidCompleted) 그땐 오른쪽 결제ID만 남는다. */}
      {((summary.paymentDueAt && !paidCompleted) || payments.length > 0) && (
        <Card className="mb-4 flex items-center justify-between gap-2 p-4 text-sm">
          <span className="flex items-center gap-2 text-muted">
            {summary.paymentDueAt && !paidCompleted && (
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
