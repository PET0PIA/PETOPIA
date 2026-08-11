/**
 * 토스 결제창이 돌아오는 착지 페이지 두 개(success / fail).
 *
 * 리다이렉트 방식이라 여기 진입은 "완전한 페이지 로드"다 — 메모리에만 있던 accessToken이
 * 사라진 상태로 앱이 새로 뜨고, AuthProvider의 토큰 재발급(silent refresh)으로 복구된다.
 * 그래서 승인 확정(confirm)은 반드시 인증 복구가 끝난 뒤에 불러야 한다.
 */
import { AlertCircle, CheckCircle2, Clock3, QrCode } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { confirmPayment, getPayment } from "../../api/payment";
import { getEntryQr } from "../../api/reservation";
import { Card } from "../../components/ui/Card";
import { QrCanvas } from "../../components/ui/QrCanvas";
import { useAuth } from "../../contexts/AuthContext";

/** 백엔드가 PENDING이 아닌 결제를 막을 때 쓰는 코드(409). 새로고침으로 인한 재confirm이 여기 걸린다. */
const NOT_PAYABLE_CODE = "P002";

function parsePositiveInt(value: string | null): number | null {
  if (value === null) return null;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

type Tone = "success" | "error" | "neutral";

const toneStyles: Record<Tone, string> = {
  success: "bg-leaf-soft text-ink",
  error: "bg-primary-soft text-primary-strong",
  neutral: "bg-page text-muted",
};

function ResultShell({
  tone,
  icon,
  title,
  children,
  actions,
}: {
  tone: Tone;
  icon: ReactNode;
  title: string;
  children: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mx-auto max-w-3xl py-2">
      <Card className="grid place-items-center p-8 text-center">
        <div className={`mb-4 grid size-14 place-items-center rounded-full ${toneStyles[tone]}`}>{icon}</div>
        <h2 className="text-lg font-extrabold text-ink">{title}</h2>
        <div className="mt-2 text-sm leading-6 text-muted">{children}</div>
        {actions && <div className="mt-5 flex flex-wrap justify-center gap-2">{actions}</div>}
      </Card>
    </div>
  );
}

function PrimaryLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="inline-flex min-h-11 items-center rounded-button bg-primary-strong px-4 text-sm font-bold text-white hover:opacity-90"
    >
      {children}
    </Link>
  );
}

function SecondaryLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="inline-flex min-h-11 items-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page"
    >
      {children}
    </Link>
  );
}

type ConfirmPhase = "confirming" | "done" | "error";

export function PaymentSuccessPage() {
  const [searchParams] = useSearchParams();
  const { user, status } = useAuth();

  // 토스가 붙여주는 값: paymentKey·orderId·amount. paymentId·reservationId는 결제창을
  // 띄울 때 successUrl에 우리가 직접 실어보낸 값이다.
  const paymentId = parsePositiveInt(searchParams.get("paymentId"));
  const reservationId = parsePositiveInt(searchParams.get("reservationId"));
  const paymentKey = searchParams.get("paymentKey");
  const orderId = searchParams.get("orderId");

  // 파라미터 검증은 렌더 입력만으로 결정되는 순수 계산이라 이펙트에 둘 이유가 없다.
  let blockedReason: string | null = null;
  if (paymentId === null || reservationId === null || !paymentKey) {
    blockedReason = "결제 결과 주소에 필요한 정보가 없어요. 내 예약 목록에서 결제 상태를 확인해 주세요.";
  } else if (orderId !== null && orderId !== `PAYMENT_${paymentId}`) {
    // 백엔드가 "PAYMENT_" + paymentId로 orderId를 만든다. 어긋나면 어차피 토스가 거절한다.
    blockedReason = "결제 주문번호가 이 결제와 맞지 않아요. 내 예약 목록에서 결제 상태를 확인해 주세요.";
  }
  const blocked = blockedReason !== null;

  const [phase, setPhase] = useState<ConfirmPhase>("confirming");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [entryQrToken, setEntryQrToken] = useState<string | null>(null);
  // 결제당 딱 한 번만 confirm한다. 백엔드는 PENDING이 아닌 결제를 409로 막으므로, 가드가
  // 없으면 StrictMode의 이펙트 2회 실행이 그대로 중복 호출이 된다.
  const confirmStarted = useRef(false);

  useEffect(() => {
    // 토큰 재발급이 끝날 때까지 기다린다. status가 확정되기 전에 부르면 401로 실패한다.
    if (status !== "authenticated" || !user) return;
    if (blocked || paymentId === null || reservationId === null || paymentKey === null) return;
    if (confirmStarted.current) return;
    confirmStarted.current = true;

    const userId = user.userId;
    void (async () => {
      try {
        await confirmPayment(paymentId, paymentKey, userId);
      } catch (err) {
        // 새로고침 등으로 이미 확정된 결제를 다시 확정하려 하면 409(P002)가 온다.
        // 실제 상태를 되물어서, 이미 성공한 결제를 실패 화면으로 보여주지 않는다.
        if (err instanceof ApiError && err.code === NOT_PAYABLE_CODE) {
          try {
            const payment = await getPayment(paymentId, userId);
            if (payment.status !== "COMPLETED") {
              setErrorMessage(err.message);
              setPhase("error");
              return;
            }
          } catch {
            setErrorMessage(err.message);
            setPhase("error");
            return;
          }
        } else {
          setErrorMessage(err instanceof ApiError ? err.message : "결제 승인을 확정하지 못했어요.");
          setPhase("error");
          return;
        }
      }

      // confirm 응답에는 QR 토큰이 없다. 승인이 확정되면 결제 도메인이 예약 도메인에
      // 통지하고 그 시점에 QR이 발급되므로 따로 조회한다.
      try {
        const qr = await getEntryQr(reservationId);
        setEntryQrToken(qr.qrToken);
      } catch {
        // 결제는 이미 성공 - QR 조회 실패를 결제 실패로 보여주지 않는다.
      }
      setPhase("done");
    })();
  }, [status, user, blocked, paymentId, reservationId, paymentKey]);

  if (blockedReason) {
    return (
      <ResultShell
        tone="error"
        icon={<AlertCircle size={28} />}
        title="결제 결과를 확인할 수 없어요"
        actions={<PrimaryLink to="/reservations/me">내 예약 목록으로</PrimaryLink>}
      >
        {blockedReason}
      </ResultShell>
    );
  }

  if (status === "loading") {
    return (
      <ResultShell tone="neutral" icon={<Clock3 size={28} />} title="로그인 정보를 확인하는 중이에요">
        잠시만 기다려 주세요. 확인이 끝나면 결제를 확정해요.
      </ResultShell>
    );
  }

  if (status !== "authenticated" || !user) {
    return (
      <ResultShell
        tone="error"
        icon={<AlertCircle size={28} />}
        title="로그인이 풀렸어요"
        actions={
          <>
            <PrimaryLink to="/login">다시 로그인하기</PrimaryLink>
            <SecondaryLink to="/reservations/me">내 예약 목록으로</SecondaryLink>
          </>
        }
      >
        결제는 승인됐을 수 있지만 예약 확정을 마치지 못했어요.
        <br />
        다시 로그인한 뒤 내 예약 목록에서 예약 상태를 꼭 확인해 주세요.
      </ResultShell>
    );
  }

  if (phase === "confirming") {
    return (
      <ResultShell tone="neutral" icon={<Clock3 size={28} />} title="결제를 확정하는 중이에요">
        창을 닫거나 새로고침하지 말고 잠시만 기다려 주세요.
      </ResultShell>
    );
  }

  if (phase === "error") {
    return (
      <ResultShell
        tone="error"
        icon={<AlertCircle size={28} />}
        title="결제 확정에 실패했어요"
        actions={<PrimaryLink to="/reservations/me">내 예약 목록으로</PrimaryLink>}
      >
        {errorMessage ?? "결제 승인을 확정하지 못했어요."}
        <br />
        결제가 이미 승인된 상태일 수 있어요. 내 예약 목록에서 상태를 확인해 주세요.
      </ResultShell>
    );
  }

  return (
    <ResultShell
      tone="success"
      icon={entryQrToken ? <QrCode size={28} /> : <CheckCircle2 size={28} />}
      title="결제가 완료됐어요"
      actions={
        <>
          <PrimaryLink to="/reservations/me">내 예약 목록으로</PrimaryLink>
          <SecondaryLink to="/tickets">다른 행사 보기</SecondaryLink>
        </>
      }
    >
      예약이 확정됐어요.
      {entryQrToken ? (
        <>
          <br />
          아래 입장 QR을 현장 스캐너에 보여주세요.
          <div className="my-5 grid place-items-center">
            <QrCanvas value={entryQrToken} size={176} />
          </div>
        </>
      ) : (
        <>
          <br />
          입장 QR은 내 예약 목록에서 확인할 수 있어요.
        </>
      )}
    </ResultShell>
  );
}

/**
 * 결제 실패·이탈 착지. 예약은 PENDING_PAYMENT로 남아있으므로 별도 API 호출은 하지 않고
 * 상태와 재시도 경로만 안내한다.
 */
export function PaymentFailPage() {
  const [searchParams] = useSearchParams();
  // 토스가 실패 리다이렉트에 붙여주는 값.
  const code = searchParams.get("code");
  const message = searchParams.get("message");

  return (
    <ResultShell
      tone="error"
      icon={<AlertCircle size={28} />}
      title="결제가 완료되지 않았어요"
      actions={
        <>
          <PrimaryLink to="/reservations/me">내 예약 목록으로</PrimaryLink>
          <SecondaryLink to="/tickets">예매 가능한 행사 보기</SecondaryLink>
        </>
      }
    >
      {message ?? "결제가 취소되었거나 승인되지 않았어요."}
      {code && <span className="ml-1 text-xs text-muted">({code})</span>}
      <br />
      예약은 결제 대기 상태로 남아 있어요. 제한시각 전까지 내 예약 목록에서 다시 결제할 수 있고,
      그 뒤에는 자동으로 만료돼요.
    </ResultShell>
  );
}
