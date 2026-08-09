import { CalendarDays, Clock, CreditCard, MapPin, QrCode } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { QrCanvas } from "../../components/ui/QrCanvas";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import { getFairPublicSummary, type FairPublicSummary } from "../../api/fair";
import { createReservationDepositPayment } from "../../api/payment";
import {
  ADVANCE_TERMS_VERSION,
  createAdvanceReservation,
  createOnsiteReservation,
  getReservationAvailability,
  ONSITE_TERMS_VERSION,
  type ReservationAvailability,
} from "../../api/reservation";
import { useAuth } from "../../contexts/AuthContext";
import { isTossConfigured, requestReservationPayment } from "../../payments/toss";

type ReservationType = "ADVANCE" | "ONSITE";
type Phase = "form" | "payment" | "done";

interface DoneInfo {
  visitDate: string;
  typeLabel: string;
  entryQrToken: string | null;
}
interface PaymentInfo {
  /** 결제 대상 예약. 결제 생성 API가 이 ID로 원장 금액을 조회한다. */
  reservationId: number;
  visitDate: string;
  typeLabel: string;
  /** 화면에 보여준 예약금이 아니라 예약 생성 응답의 서버 계산값. */
  amount: number;
  /** 결제 제한시각(ISO). 지나면 서버 배치가 예약을 만료시킨다. */
  paymentExpiresAt: string | null;
}

function formatTime(time: string) {
  return time.slice(0, 5);
}

/**
 * 결제 제한시각까지 남은 시간을 "m:ss"로 만든다. 제한시각이 없거나 이미 지났으면 null.
 *
 * 백엔드는 LocalDateTime을 오프셋 없이("2026-08-09T12:34:56") 내려주는데, 서버·DB·컨테이너가
 * 전부 Asia/Seoul로 고정돼 있어(Dockerfile / docker-compose의 TZ) 브라우저 로컬 시각으로
 * 파싱해도 어긋나지 않는다.
 */
function formatRemaining(expiresAt: string | null, now: number): string | null {
  if (!expiresAt) return null;
  const diff = new Date(expiresAt).getTime() - now;
  if (Number.isNaN(diff) || diff <= 0) return null;
  const totalSeconds = Math.floor(diff / 1000);
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, "0")}`;
}

// 운영 시작~종료일을 "2026-09-18 ~ 09-20" 형태로 다듬는다.
// 단, 해가 바뀌는 기간(2026-12-30 ~ 2027-01-02)은 종료 연도를 남긴다.
function formatPeriod(start: string | null, end: string | null) {
  if (!start) return "";
  if (!end || end === start) return start;
  const sameYear = start.slice(0, 4) === end.slice(0, 4);
  return `${start} ~ ${sameYear ? end.slice(5) : end}`;
}

export function TicketReservationPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const id = Number(fairId);
  // 경로 파라미터가 양의 정수가 아니면(예: /tickets/abc) NaN을 API URL에 싣지 않는다.
  const idValid = Number.isInteger(id) && id > 0;
  const { confirm, confirmDialog } = useConfirm();
  // 결제 API는 아직 JWT가 아니라 X-User-Id 헤더로 결제자를 식별한다. JWT의 sub를 디코딩한
  // 값이라 결제 API가 JWT로 넘어가기 전에도 정확하다.
  const { user } = useAuth();

  const [availability, setAvailability] = useState<ReservationAvailability | null>(null);
  const [fair, setFair] = useState<FairPublicSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [type, setType] = useState<ReservationType>("ADVANCE");
  const [selectedVisitDate, setSelectedVisitDate] = useState<string | null>(null);
  const [agreed, setAgreed] = useState(false); // 사전예약 유료 약관
  const [onsiteAgreed, setOnsiteAgreed] = useState(false); // 현장예매 환불불가 약관
  const [phase, setPhase] = useState<Phase>("form");

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [doneInfo, setDoneInfo] = useState<DoneInfo | null>(null);
  const [paymentInfo, setPaymentInfo] = useState<PaymentInfo | null>(null);

  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());

  // 카운트다운은 결제 단계에서만 돌린다 - 폼/완료 화면에서 1초마다 리렌더할 이유가 없다.
  useEffect(() => {
    if (phase !== "payment") return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [phase]);

  useEffect(() => {
    if (!idValid) return; // 잘못된 경로 파라미터면 요청하지 않는다
    let alive = true;
    // 행사 이름·장소는 fair 도메인, 예약금·날짜는 예약 도메인에서 각각 가져온다.
    // 행사 정보 조회는 실패해도 예매는 계속할 수 있게 막지 않는다(이름만 못 보여줄 뿐).
    getFairPublicSummary(id)
      .then((res) => {
        if (alive) setFair(res);
      })
      .catch(() => {
        /* 헤더 표시용이라 실패를 치명적으로 다루지 않는다. */
      });
    getReservationAvailability(id)
      .then((res) => {
        if (alive) setAvailability(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "예매 정보를 불러오지 못했어요.");
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
          title="잘못된 예매 주소예요."
          description="행사 주소가 올바르지 않아요. 예매 가능한 행사에서 다시 선택해 주세요."
          actionTo="/tickets"
          actionLabel="예매 가능한 행사 보기"
        />
      </div>
    );
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <p className="py-16 text-center text-sm text-muted">예매 정보를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (loadError || !availability) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <EmptyState
          title="지금은 예매할 수 없어요."
          description={loadError ?? "예매 정보를 불러오지 못했어요."}
          actionTo="/tickets"
          actionLabel="예매 가능한 행사 보기"
        />
      </div>
    );
  }

  const fairName = fair?.name ?? `행사 #${id}`;
  const period = formatPeriod(fair?.operationStartDate ?? null, fair?.operationEndDate ?? null);

  const selectedDate = availability.dates.find((date) => date.visitDate === selectedVisitDate) ?? null;

  const price = availability.reservationFee; // 사전예약금
  const isPaid = price > 0;
  const advanceVisitDate = selectedDate?.visitDate ?? null;

  const canProceedAdvance = selectedDate !== null && (!isPaid || agreed) && !submitting;
  const canProceedOnsite = onsiteAgreed && !submitting;

  // 클라이언트 키가 안 들어와 있으면 결제 버튼 대신 안내를 띄운다 - 키 누락을 런타임 에러가
  // 아니라 UI로 드러내려는 목적이다.
  const tossReady = isTossConfigured();
  const expiresAt = paymentInfo?.paymentExpiresAt ?? null;
  const remaining = formatRemaining(expiresAt, now);
  // 현재 백엔드 confirmPayment는 만료를 검증하지 않는다. 만료 후 결제하면 결제는 승인되고
  // 예약 통지만 거절당해 돈만 나가므로, 이 가드가 지금은 유일한 방어선이다.
  const expired = expiresAt !== null && remaining === null;

  function switchType(next: ReservationType) {
    setType(next);
    setAgreed(false);
    setOnsiteAgreed(false);
    setSubmitError(null);
  }

  async function handleAdvance() {
    if (!advanceVisitDate) return;
    const proceed = await confirm({
      title: isPaid ? "유료 예약을 진행할까요?" : "예약할까요?",
      description: `${fairName} · ${advanceVisitDate} 방문으로 사전예약해요. ${
        isPaid
          ? `결제 금액은 ${price.toLocaleString()}원이에요. 예약을 잡아둔 뒤 10분 안에 결제를 마쳐야 확정돼요.`
          : "무료 예약이에요."
      }`,
      confirmLabel: isPaid ? "다음" : "예약하기",
    });
    if (!proceed) return;

    setSubmitError(null);
    setSubmitting(true);
    try {
      // 유료도 예약을 먼저 만든다 - 결제 생성 API가 reservationId로 원장 금액을 조회하므로
      // 예약이 없으면 결제를 시작할 수 없다. 유료는 약관 동의가 필수다(R006).
      const created = await createAdvanceReservation(id, {
        visitDate: advanceVisitDate,
        ...(isPaid ? { reservationTermsAgreed: true, reservationTermsVersion: ADVANCE_TERMS_VERSION } : {}),
      });

      if (created.paymentRequired) {
        setPaymentInfo({
          reservationId: created.reservationId,
          visitDate: advanceVisitDate,
          typeLabel: "사전예약",
          amount: created.amount,
          paymentExpiresAt: created.paymentExpiresAt,
        });
        setPayError(null);
        setNow(Date.now()); // 카운트다운 첫 프레임이 옛날 값으로 그려지지 않게 맞춰둔다
        setPhase("payment");
        return;
      }

      // 무료: 서버가 한 트랜잭션에서 CONFIRMED + QR까지 끝낸다.
      setDoneInfo({ visitDate: advanceVisitDate, typeLabel: "사전예약", entryQrToken: created.entryQrToken });
      setPhase("done");
    } catch (err) {
      // R001 행사없음 / R002 날짜불가 / R003 접수아님 / R004 마감 / R005 중복 / R006 약관
      setSubmitError(err instanceof ApiError ? err.message : "예약에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleOnsite() {
    const proceed = await confirm({
      title: "현장예매 할까요?",
      description: `${fairName} · 오늘 방문으로 현장예매해요. 현장예매는 취소·환불이 불가능해요.`,
      confirmLabel: "현장예매 하기",
    });
    if (!proceed) return;

    setSubmitError(null);
    setSubmitting(true);
    try {
      const created = await createOnsiteReservation(id, {
        reservationTermsAgreed: true,
        reservationTermsVersion: ONSITE_TERMS_VERSION,
      });
      if (created.paymentRequired) {
        // 현장예매는 이미 예약을 만들고 있었으므로 결제 단계로 넘길 값만 채운다.
        setPaymentInfo({
          reservationId: created.reservationId,
          visitDate: created.visitDate,
          typeLabel: "현장예매",
          amount: created.amount,
          paymentExpiresAt: created.paymentExpiresAt,
        });
        setPayError(null);
        setNow(Date.now());
        setPhase("payment");
      } else {
        setDoneInfo({ visitDate: created.visitDate, typeLabel: "현장예매", entryQrToken: created.entryQrToken });
        setPhase("done");
      }
    } catch (err) {
      // R007 접수아님/시간지남 / R008 일시중지 / R005 활성 예약 중복
      setSubmitError(err instanceof ApiError ? err.message : "현장예매에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  /**
   * 결제 생성 → 토스 결제창. 정상 흐름이면 결제창이 뜨고 그 뒤 /payments/success 로
   * 리다이렉트되므로, 이 함수가 끝까지 진행되면 페이지는 곧 사라진다.
   */
  async function handlePay() {
    if (!paymentInfo || paying || expired) return;
    if (!user) {
      setPayError("로그인이 풀렸어요. 다시 로그인한 뒤 내 예약 목록에서 결제를 이어가 주세요.");
      return;
    }

    setPayError(null);
    setPaying(true);
    try {
      const created = await createReservationDepositPayment(paymentInfo.reservationId, user.userId);
      await requestReservationPayment({
        paymentId: created.paymentId,
        reservationId: paymentInfo.reservationId,
        // 서버가 만든 orderId·amount를 가공 없이 넘긴다 - 다르면 토스가 승인을 거절한다.
        orderId: created.orderId ?? `PAYMENT_${created.paymentId}`,
        amount: created.amount,
        orderName: `${fairName} 예약금`,
      });
      // 리다이렉트가 시작됐으므로 paying을 되돌리지 않는다(버튼이 다시 눌리면 안 된다).
    } catch (err) {
      // 결제창을 사용자가 닫은 경우도 여기로 온다.
      setPayError(err instanceof ApiError ? err.message : "결제를 시작하지 못했어요. 잠시 후 다시 시도해 주세요.");
      setPaying(false);
    }
  }

  // 완료 화면
  if (phase === "done" && doneInfo) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <Card className="grid place-items-center p-8 text-center">
          <div className="mb-4 grid size-14 place-items-center rounded-full bg-leaf-soft text-ink">
            <QrCode size={28} />
          </div>
          <h2 className="text-lg font-extrabold">예약이 확정됐어요</h2>
          <p className="mt-2 text-sm leading-6 text-muted">
            {fairName} · {doneInfo.visitDate} 방문 · {doneInfo.typeLabel}
            <br />
            예약이 바로 확정됐어요. 아래 입장 QR을 현장 스캐너에 보여주세요.
          </p>
          <div className="my-5 grid place-items-center">
            {doneInfo.entryQrToken ? (
              <QrCanvas value={doneInfo.entryQrToken} size={176} />
            ) : (
              <p className="text-sm text-muted">입장 QR은 내 예약 목록에서 확인할 수 있어요.</p>
            )}
          </div>
          <div className="flex gap-2">
            <Link
              to="/reservations/me"
              className="inline-flex min-h-11 items-center rounded-button bg-primary-strong px-4 text-sm font-bold text-white hover:opacity-90"
            >
              내 예약 목록으로
            </Link>
            <Link
              to="/tickets"
              className="inline-flex min-h-11 items-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page"
            >
              다른 행사 보기
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  // 결제 단계 (유료만). 여기 도달한 시점에 예약은 이미 PENDING_PAYMENT로 만들어져 있다.
  if (phase === "payment" && paymentInfo) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <PageHeader
          eyebrow="결제"
          title="예약금 결제"
          description="결제를 마쳐야 예약이 확정돼요. 제한시각까지 결제하지 않으면 예약이 자동으로 만료돼요."
        />

        <Card className="mb-4 p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-bold text-ink">{fairName}</p>
              <p className="mt-1 text-sm text-muted">
                {paymentInfo.visitDate} 방문 · {paymentInfo.typeLabel}
              </p>
            </div>
            <p className="text-lg font-extrabold text-ink">{paymentInfo.amount.toLocaleString()}원</p>
          </div>
        </Card>

        {expiresAt && (
          <Card className="mb-4 flex items-center gap-2 p-4 text-sm">
            <Clock size={16} className="shrink-0 text-muted" />
            {expired ? (
              <span className="font-bold text-primary-strong">
                결제 제한시각이 지났어요. 이 예약은 곧 만료되니 다시 예매해 주세요.
              </span>
            ) : (
              <span className="text-muted">
                결제 제한시각까지 <b className="text-ink">{remaining}</b> 남았어요.
              </span>
            )}
          </Card>
        )}

        <Card className="mb-6 p-6">
          <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
            <CreditCard size={16} />
            결제 수단
          </div>
          {tossReady ? (
            <p className="rounded-button border border-line bg-page p-4 text-sm leading-6 text-muted">
              <b className="text-ink">결제하기</b>를 누르면 토스페이먼츠 결제창이 열려요. 카드·간편결제 중에서
              고를 수 있고, 결제를 마치면 이 사이트로 돌아와 예약이 확정돼요.
            </p>
          ) : (
            <div className="grid place-items-center gap-1 rounded-button border border-dashed border-line bg-page py-10 text-center text-sm text-muted">
              <p className="font-bold text-ink">결제 설정이 없어요</p>
              <p>결제 클라이언트 키가 주입되지 않았어요. 관리자에게 문의해 주세요.</p>
            </div>
          )}
        </Card>

        {payError && <p className="mb-4 text-sm font-bold text-primary-strong">{payError}</p>}

        <div className="flex items-center justify-between gap-3">
          <Link
            to="/reservations/me"
            className="inline-flex min-h-11 items-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page"
          >
            나중에 결제하기
          </Link>
          <Button disabled={!tossReady || expired || paying} onClick={handlePay}>
            {paying ? "결제창을 여는 중…" : "결제하기"}
          </Button>
        </div>
        {/* 결제 실패·이탈 시 예약은 건드리지 않는다 - 제한시각까지는 그대로 두고 안내만 한다. */}
        <p className="mt-3 text-xs leading-5 text-muted">
          결제하지 않고 나가도 예약은 제한시각까지 결제 대기 상태로 남아 있어요. 내 예약 목록에서 확인할 수 있어요.
        </p>
      </div>
    );
  }

  // 폼 (기본)
  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader eyebrow="티켓 예매" title="예매하기" description="예약 유형과 방문일을 고르고 예약을 진행해요." />

      {/* 행사 정보 */}
      <Card className="mb-6 p-5">
        <h2 className="text-lg font-extrabold text-ink">{fairName}</h2>
        <div className="mt-2 flex flex-col gap-1.5 text-sm text-muted">
          {fair?.placeName && (
            <span className="flex items-center gap-2">
              <MapPin size={16} className="shrink-0" />
              {fair.placeName}
            </span>
          )}
          {period && (
            <span className="flex items-center gap-2">
              <CalendarDays size={16} className="shrink-0" />
              {period}
            </span>
          )}
        </div>
      </Card>

      {/* 예약 유형 선택 */}
      <h3 className="mb-3 text-sm font-bold text-ink">예약 유형</h3>
      <div className="mb-6 grid grid-cols-2 gap-3">
        <button
          type="button"
          onClick={() => switchType("ADVANCE")}
          className={`rounded-card border p-4 text-left transition ${
            type === "ADVANCE" ? "border-primary-strong bg-primary-soft" : "border-line bg-card hover:bg-page"
          }`}
        >
          <p className="font-bold text-ink">사전예약</p>
          <p className="mt-1 text-xs text-muted">방문일을 골라 미리 예약</p>
        </button>
        <button
          type="button"
          onClick={() => switchType("ONSITE")}
          className={`rounded-card border p-4 text-left transition ${
            type === "ONSITE" ? "border-primary-strong bg-primary-soft" : "border-line bg-card hover:bg-page"
          }`}
        >
          <p className="font-bold text-ink">현장예매</p>
          <p className="mt-1 text-xs text-muted">오늘 방문, 현장에서 바로 입장</p>
        </button>
      </div>

      {/* 유형별 내용 */}
      {type === "ADVANCE" ? (
        <>
          <h3 className="mb-3 text-sm font-bold text-ink">
            방문일 선택<span className="ml-1 text-primary-strong">*</span>
          </h3>
          {availability.dates.length === 0 ? (
            <Card className="mb-6 p-4 text-sm text-muted">지금 예매할 수 있는 방문일이 없어요.</Card>
          ) : (
            <div className="mb-6 grid gap-3 sm:grid-cols-3">
              {availability.dates.map((date) => {
                const soldOut = !date.available || date.remainingCapacity === 0;
                const selected = date.visitDate === selectedVisitDate;
                return (
                  <button
                    key={date.visitDate}
                    type="button"
                    disabled={soldOut}
                    onClick={() => setSelectedVisitDate(date.visitDate)}
                    className={`rounded-card border p-4 text-left transition ${
                      selected ? "border-primary-strong bg-primary-soft" : "border-line bg-card hover:bg-page"
                    } ${soldOut ? "cursor-not-allowed opacity-50 hover:bg-card" : ""}`}
                  >
                    <p className="font-bold text-ink">{date.visitDate}</p>
                    <p className="mt-1 text-xs text-muted">
                      {formatTime(date.entryStartTime)} ~ {formatTime(date.entryEndTime)}
                    </p>
                    <p className="mt-2 text-xs font-bold">
                      {soldOut ? (
                        <span className="text-muted">마감</span>
                      ) : (
                        <span className="text-ink">잔여 {date.remainingCapacity}석</span>
                      )}
                    </p>
                  </button>
                );
              })}
            </div>
          )}

          {/* 금액 */}
          <Card className="mb-6 p-5">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted">결제 금액</span>
              <span className="text-lg font-extrabold text-ink">
                {isPaid ? `${price.toLocaleString()}원` : "무료"}
              </span>
            </div>
          </Card>

          {/* 약관 동의: 유료일 때만 */}
          {isPaid && (
            <label className="mb-6 flex cursor-pointer items-start gap-2.5 text-sm text-ink">
              <input
                type="checkbox"
                checked={agreed}
                onChange={(event) => setAgreed(event.target.checked)}
                className="mt-0.5 size-4 accent-primary-strong"
              />
              <span>예약 및 취소·환불 규정을 확인했고 이에 동의해요.</span>
            </label>
          )}

          {isPaid && (
            <p className="mb-4 text-sm text-muted">
              ※ 다음 화면에서 결제를 마쳐야 예약이 확정돼요. 예약을 잡아둔 뒤 10분 안에 결제해 주세요.
            </p>
          )}

          {submitError && <p className="mb-4 text-sm font-bold text-primary-strong">{submitError}</p>}

          <div className="flex justify-end">
            <Button disabled={!canProceedAdvance} onClick={handleAdvance}>
              {submitting ? "예약 중…" : isPaid ? "결제하러 가기" : "예약 완료하기"}
            </Button>
          </div>
        </>
      ) : (
        <>
          <h3 className="mb-3 text-sm font-bold text-ink">현장예매</h3>
          <Card className="mb-6 p-5 text-sm leading-6 text-muted">
            <p className="font-bold text-ink">오늘 방문 · 현장에서 바로 입장</p>
            <p className="mt-1">
              현장예매는 오늘 방문만 가능하고, 예매 후 바로 입장할 수 있어요. 판매 상태·가격은 행사마다
              달라서 예매를 시도하면 확인돼요.
            </p>
            <p className="mt-1 font-bold text-primary-strong">현장예매는 취소·환불이 불가능해요.</p>
          </Card>

          <label className="mb-6 flex cursor-pointer items-start gap-2.5 text-sm text-ink">
            <input
              type="checkbox"
              checked={onsiteAgreed}
              onChange={(event) => setOnsiteAgreed(event.target.checked)}
              className="mt-0.5 size-4 accent-primary-strong"
            />
            <span>현장예매 취소·환불 불가 규정을 확인했고 이에 동의해요.</span>
          </label>

          {submitError && <p className="mb-4 text-sm font-bold text-primary-strong">{submitError}</p>}

          <div className="flex justify-end">
            <Button disabled={!canProceedOnsite} onClick={handleOnsite}>
              {submitting ? "처리 중…" : "현장예매 하기"}
            </Button>
          </div>
        </>
      )}

      {confirmDialog}
    </div>
  );
}
