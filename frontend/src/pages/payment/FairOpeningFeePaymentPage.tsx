import { AlertTriangle, CalendarClock, CreditCard } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getFairOpeningFeeSummary, type FairOpeningFeeSummary } from "../../api/fair";
import { createFairOpeningPayment } from "../../api/payment";
import { useAuth } from "../../contexts/AuthContext";
import { isTossConfigured, requestFairOpeningFeePayment } from "../../payments/toss";

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

  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
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
        orderId: created.orderId ?? `PAYMENT_${created.paymentId}`,
        amount: created.amount,
        orderName: `${summary!.name} 개설비`,
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

      <Card className="mb-4 p-5">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted">개설비</span>
          <span className="text-lg font-extrabold text-ink">
            {summary.openingFeeAmount !== null ? `${summary.openingFeeAmount.toLocaleString()}원` : "미확정"}
          </span>
        </div>
      </Card>

      {summary.paymentDueAt && (
        <Card className="mb-4 flex items-center gap-2 p-4 text-sm">
          <CalendarClock size={16} className="shrink-0 text-muted" />
          <span className="text-muted">
            결제 기한 <b className="text-ink">{formatDueAt(summary.paymentDueAt)}</b>까지
          </span>
        </Card>
      )}

      {nonPayableMessage && (
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
            <p className="rounded-button border border-line bg-page p-4 text-sm leading-6 text-muted">
              <b className="text-ink">결제하기</b>를 누르면 토스페이먼츠 카드 결제창이 열려요.
            </p>
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
          <Button disabled={!tossReady || paying} onClick={handlePay}>
            {paying ? "결제창을 여는 중…" : "결제하기"}
          </Button>
        )}
      </div>
    </div>
  );
}
