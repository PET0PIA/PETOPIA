import { CalendarDays, CreditCard, MapPin, QrCode } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { QrCanvas } from "../../components/ui/QrCanvas";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import { getFairApplication, type FairApplicationDetail } from "../../api/fair";
import {
  createAdvanceReservation,
  createOnsiteReservation,
  getReservationAvailability,
  ONSITE_TERMS_VERSION,
  type ReservationAvailability,
} from "../../api/reservation";

type ReservationType = "ADVANCE" | "ONSITE";
type Phase = "form" | "payment" | "done";

interface DoneInfo {
  visitDate: string;
  typeLabel: string;
  entryQrToken: string | null;
}
interface PaymentInfo {
  visitDate: string;
  typeLabel: string;
  amount: number;
}

function formatTime(time: string) {
  return time.slice(0, 5);
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

  const [availability, setAvailability] = useState<ReservationAvailability | null>(null);
  const [fair, setFair] = useState<FairApplicationDetail | null>(null);
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

  useEffect(() => {
    if (!idValid) return; // 잘못된 경로 파라미터면 요청하지 않는다
    let alive = true;
    // 행사 이름·장소는 fair 도메인, 예약금·날짜는 예약 도메인에서 각각 가져온다.
    // 행사 정보 조회는 실패해도 예매는 계속할 수 있게 막지 않는다(이름만 못 보여줄 뿐).
    getFairApplication(id)
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
          ? `결제 금액은 ${price.toLocaleString()}원이에요. 결제 기능은 준비 중이라 지금은 예약을 완료할 수 없어요.`
          : "무료 예약이에요."
      }`,
      confirmLabel: isPaid ? "다음" : "예약하기",
    });
    if (!proceed) return;

    // 유료는 결제 단계로(결제위젯은 결제 도메인 연동 예정 — 아직 생성하지 않는다).
    if (isPaid) {
      setPaymentInfo({ visitDate: advanceVisitDate, typeLabel: "사전예약", amount: price });
      setPhase("payment");
      return;
    }

    // 무료: 서버가 한 트랜잭션에서 CONFIRMED + QR까지 끝낸다.
    setSubmitError(null);
    setSubmitting(true);
    try {
      const created = await createAdvanceReservation(id, { visitDate: advanceVisitDate });
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
      if (created.reservationStatus === "PENDING_PAYMENT") {
        setPaymentInfo({ visitDate: created.visitDate, typeLabel: "현장예매", amount: created.amount });
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

  // 결제 단계 (유료만) — 실제 결제위젯은 결제 도메인 연동 예정
  if (phase === "payment" && paymentInfo) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <PageHeader
          eyebrow="결제"
          title="결제 준비 중"
          description="유료 예약의 결제 기능은 아직 준비 중이에요. 준비되면 이 화면에서 결제를 완료할 수 있어요."
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

        <Card className="mb-6 p-6">
          <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
            <CreditCard size={16} />
            결제 수단
          </div>
          {/* 결제위젯 자리 — 결제 도메인 연동 예정. 유료 예약 생성·결제는 그때 함께 붙인다. */}
          <div className="grid place-items-center gap-1 rounded-button border border-dashed border-line bg-page py-10 text-center text-sm text-muted">
            <p className="font-bold text-ink">결제 기능 준비 중</p>
            <p>지금은 결제를 완료할 수 없어요. 곧 지원할 예정이에요.</p>
          </div>
        </Card>

        <div className="flex justify-between">
          <Button variant="outline" onClick={() => setPhase("form")}>
            뒤로
          </Button>
        </div>
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
              ※ 유료 예약 결제는 준비 중이라 지금은 예약을 완료할 수 없어요. 무료 예약만 바로 확정돼요.
            </p>
          )}

          {submitError && <p className="mb-4 text-sm font-bold text-primary-strong">{submitError}</p>}

          <div className="flex justify-end">
            <Button disabled={!canProceedAdvance} onClick={handleAdvance}>
              {submitting ? "예약 중…" : isPaid ? "다음" : "예약 완료하기"}
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
