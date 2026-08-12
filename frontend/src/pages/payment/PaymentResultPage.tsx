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

  // 토스가 붙여주는 값: paymentKey·orderId·amount. paymentId·reservationId(예약금 결제)·
  // fairId(개설비 결제) 또는 applicationId(참가비 결제)는 결제창을 띄울 때 successUrl에
  // 우리가 직접 실어보낸 값이다. 세 흐름은 toss.ts에서 서로 다른 쿼리 파라미터를 붙이므로
  // 한쪽만 채워진다.
  const paymentId = parsePositiveInt(searchParams.get("paymentId"));
  const reservationId = parsePositiveInt(searchParams.get("reservationId"));
  const fairId = parsePositiveInt(searchParams.get("fairId"));
  const applicationId = parsePositiveInt(searchParams.get("applicationId"));
  const paymentKey = searchParams.get("paymentKey");
  const orderId = searchParams.get("orderId");
  const isFairOpeningFee = reservationId === null && fairId !== null;
  const isVendorFee = reservationId === null && fairId === null && applicationId !== null;
  // 확정 실패·이탈 시 되돌아갈 곳. 예약금은 "내 예약 목록", 참가비는 "참가 신청 현황",
  // 개설비는 마땅한 전용 목록이 없어(fairId 단위 페이지라) 홈으로 보낸다.
  const backLink = isFairOpeningFee
    ? { to: "/", label: "홈으로" }
    : isVendorFee
      ? { to: "/participations/me", label: "참가 신청 현황으로" }
      : { to: "/reservations/me", label: "내 예약 목록으로" };

  // 파라미터 검증은 렌더 입력만으로 결정되는 순수 계산이라 이펙트에 둘 이유가 없다.
  let blockedReason: string | null = null;
  if (paymentId === null || (reservationId === null && fairId === null && applicationId === null) || !paymentKey) {
    blockedReason = isFairOpeningFee
      ? "결제 결과 주소에 필요한 정보가 없어요. 결제 상태를 확인해 주세요."
      : isVendorFee
        ? "결제 결과 주소에 필요한 정보가 없어요. 참가 신청 현황에서 결제 상태를 확인해 주세요."
        : "결제 결과 주소에 필요한 정보가 없어요. 내 예약 목록에서 결제 상태를 확인해 주세요.";
  } else if (orderId !== null && orderId !== `PAYMENT_${paymentId}`) {
    // 백엔드가 "PAYMENT_" + paymentId로 orderId를 만든다. 어긋나면 어차피 토스가 거절한다.
    blockedReason = isFairOpeningFee
      ? "결제 주문번호가 이 결제와 맞지 않아요. 결제 상태를 확인해 주세요."
      : isVendorFee
        ? "결제 주문번호가 이 결제와 맞지 않아요. 참가 신청 현황에서 결제 상태를 확인해 주세요."
        : "결제 주문번호가 이 결제와 맞지 않아요. 내 예약 목록에서 결제 상태를 확인해 주세요.";
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
    if (blocked || paymentId === null || paymentKey === null) return;
    if (reservationId === null && fairId === null && applicationId === null) return;
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
      // 통지하고 그 시점에 QR이 발급되므로 따로 조회한다. 개설비 결제는 QR이 없는
      // 흐름이라(예약이 아니다) 이 조회 자체를 건너뛴다.
      if (reservationId !== null) {
        try {
          const qr = await getEntryQr(reservationId);
          setEntryQrToken(qr.qrToken);
        } catch {
          // 결제는 이미 성공 - QR 조회 실패를 결제 실패로 보여주지 않는다.
        }
      }
      setPhase("done");
    })();
  }, [status, user, blocked, paymentId, reservationId, fairId, applicationId, paymentKey]);

  if (blockedReason) {
    return (
      <ResultShell
        tone="error"
        icon={<AlertCircle size={28} />}
        title="결제 결과를 확인할 수 없어요"
        actions={<PrimaryLink to={backLink.to}>{backLink.label}</PrimaryLink>}
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
            <SecondaryLink to={backLink.to}>{backLink.label}</SecondaryLink>
          </>
        }
      >
        {isFairOpeningFee ? (
          <>
            결제는 승인됐을 수 있지만 개설비 결제 확정을 마치지 못했어요.
            <br />
            다시 로그인한 뒤 결제 상태를 꼭 확인해 주세요.
          </>
        ) : isVendorFee ? (
          <>
            결제는 승인됐을 수 있지만 참가 신청 확정을 마치지 못했어요.
            <br />
            다시 로그인한 뒤 참가 신청 현황에서 상태를 꼭 확인해 주세요.
          </>
        ) : (
          <>
            결제는 승인됐을 수 있지만 예약 확정을 마치지 못했어요.
            <br />
            다시 로그인한 뒤 내 예약 목록에서 예약 상태를 꼭 확인해 주세요.
          </>
        )}
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
        actions={<PrimaryLink to={backLink.to}>{backLink.label}</PrimaryLink>}
      >
        {errorMessage ?? "결제 승인을 확정하지 못했어요."}
        <br />
        {isFairOpeningFee
          ? "결제가 이미 승인된 상태일 수 있어요. 관리자에게 문의해 상태를 확인해 주세요."
          : isVendorFee
            ? "결제가 이미 승인된 상태일 수 있어요. 참가 신청 현황에서 상태를 확인해 주세요."
            : "결제가 이미 승인된 상태일 수 있어요. 내 예약 목록에서 상태를 확인해 주세요."}
      </ResultShell>
    );
  }

  // 개설비 결제는 예약이 아니라 QR·입장 절차가 없다 - 승인 확정만으로 끝나므로
  // 기존 예약금 성공 화면(QR 표시)과 별도로 짧게 처리한다.
  if (isFairOpeningFee) {
    return (
      <ResultShell
        tone="success"
        icon={<CheckCircle2 size={28} />}
        title="개설비 결제가 완료됐어요"
        actions={<PrimaryLink to="/">홈으로</PrimaryLink>}
      >
        행사 개설비 결제가 정상적으로 승인·확정됐어요.
      </ResultShell>
    );
  }

  // 참가비 결제도 개설비와 마찬가지로 QR이 없다 - 신청 확정으로 끝난다.
  if (isVendorFee) {
    return (
      <ResultShell
        tone="success"
        icon={<CheckCircle2 size={28} />}
        title="참가비 결제가 완료됐어요"
        actions={<PrimaryLink to="/participations/me">참가 신청 현황으로</PrimaryLink>}
      >
        참가 신청이 확정됐어요.
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
  // 성공 착지와 마찬가지로 개설비 결제는 fairId, 참가비 결제는 applicationId가 실려온다(toss.ts 참고).
  const fairId = parsePositiveInt(searchParams.get("fairId"));
  const applicationId = parsePositiveInt(searchParams.get("applicationId"));

  if (fairId !== null) {
    return (
      <ResultShell
        tone="error"
        icon={<AlertCircle size={28} />}
        title="결제가 완료되지 않았어요"
        actions={
          <>
            <PrimaryLink to={`/payments/fair-opening-fee/${fairId}`}>다시 결제하기</PrimaryLink>
            <SecondaryLink to="/">홈으로</SecondaryLink>
          </>
        }
      >
        {message ?? "결제가 취소되었거나 승인되지 않았어요."}
        {code && <span className="ml-1 text-xs text-muted">({code})</span>}
        <br />
        개설비 결제가 아직 완료되지 않았어요. 결제 기한 전까지 다시 시도할 수 있어요.
      </ResultShell>
    );
  }

  if (applicationId !== null) {
    return (
      <ResultShell
        tone="error"
        icon={<AlertCircle size={28} />}
        title="결제가 완료되지 않았어요"
        actions={
          <>
            <PrimaryLink to="/participations/me">참가 신청 현황으로</PrimaryLink>
            <SecondaryLink to="/fairs/upcoming">다른 행사 모집 보기</SecondaryLink>
          </>
        }
      >
        {message ?? "결제가 취소되었거나 승인되지 않았어요."}
        {code && <span className="ml-1 text-xs text-muted">({code})</span>}
        <br />
        신청은 결제 대기 상태로 남아 있어요. 참가 신청 현황에서 다시 결제할 수 있어요.
      </ResultShell>
    );
  }

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
