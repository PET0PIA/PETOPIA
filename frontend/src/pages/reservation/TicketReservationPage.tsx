import { CalendarDays, CheckCircle2, CreditCard, MapPin, QrCode } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { QrCanvas } from "../../components/ui/QrCanvas";
import { useConfirm } from "../../components/ui/useConfirm";
import { getFairReservationInfo } from "../../mocks/reservationAvailability";

type ReservationType = "ADVANCE" | "ONSITE";
type Phase = "form" | "payment" | "done";

function formatTime(time: string) {
  return time.slice(0, 5);
}

export function TicketReservationPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const info = getFairReservationInfo(fairId);

  const { confirm, confirmDialog } = useConfirm();
  const [type, setType] = useState<ReservationType>("ADVANCE");
  const [selectedDateId, setSelectedDateId] = useState<number | null>(null);
  const [agreed, setAgreed] = useState(false);
  const [phase, setPhase] = useState<Phase>("form");

  // 행사를 못 찾으면(잘못된 주소) 안내 화면을 보여준다.
  if (!info) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <EmptyState
          title="예매할 행사를 찾을 수 없어요."
          description="주소가 올바른지 확인하거나 예매 가능한 행사 목록에서 다시 선택해 주세요."
          actionTo="/tickets"
          actionLabel="예매 가능한 행사 보기"
        />
      </div>
    );
  }

  const onsite = info.onsite;
  const advanceDate = info.dates.find((date) => date.fairDateId === selectedDateId) ?? null;

  // 선택된 유형에 따른 금액·방문일
  const price = type === "ADVANCE" ? info.reservationFee : onsite?.price ?? 0;
  const isPaid = price > 0;
  const visitDate = type === "ADVANCE" ? advanceDate?.visitDate ?? null : onsite?.visitDate ?? null;
  const typeLabel = type === "ONSITE" ? "현장예매" : "사전예약";

  // 예약 대상이 정해졌는지(사전=날짜 선택 / 현장=오늘 접수 중)
  const targetReady = type === "ADVANCE" ? advanceDate !== null : onsite !== null && onsite.available;
  // 유료면 약관 동의까지 필요하다.
  const canProceed = targetReady && (!isPaid || agreed);

  function switchType(next: ReservationType) {
    setType(next);
    setAgreed(false);
  }

  async function handleProceed() {
    const proceed = await confirm({
      title: isPaid ? "결제하고 예약할까요?" : "예약할까요?",
      description: `${info!.fairName} · ${visitDate} 방문으로 ${typeLabel}해요. ${
        isPaid ? `결제 금액은 ${price.toLocaleString()}원이에요.` : "무료 예약이에요."
      }`,
      confirmLabel: isPaid ? "결제하기" : "예약하기",
    });
    if (!proceed) return;
    // 유료는 결제 단계로, 무료는 바로 완료로. (실제 저장·결제는 나중에 연동)
    setPhase(isPaid ? "payment" : "done");
  }

  // 완료 화면 (무료/유료 메시지 분기)
  if (phase === "done") {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <Card className="grid place-items-center p-8 text-center">
          <div className="mb-4 grid size-14 place-items-center rounded-full bg-leaf-soft text-ink">
            {isPaid ? <CheckCircle2 size={28} /> : <QrCode size={28} />}
          </div>
          <h2 className="text-lg font-extrabold">{isPaid ? "결제가 완료됐어요" : "예약이 확정됐어요"}</h2>
          <p className="mt-2 text-sm leading-6 text-muted">
            {info.fairName} · {visitDate} 방문 · {typeLabel}
            <br />
            {isPaid
              ? "결제가 완료되고 예약이 확정됐어요. 아래 입장 QR을 현장 스캐너에 보여주세요."
              : "무료 예약이 바로 확정됐어요. 아래 입장 QR을 현장 스캐너에 보여주세요."}
          </p>
          <div className="my-5 grid place-items-center">
            {/* 목업 토큰으로 QR 생성. 서버 연동 시 실제 입장 QR 토큰을 넘긴다. */}
            <QrCanvas value={`MOCK-QR-${info.fairId}-${visitDate ?? ""}`} size={176} />
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

  // 결제 단계 (유료만) — 실제 결제위젯은 나중에 제공되는 코드로 교체
  if (phase === "payment") {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <PageHeader eyebrow="결제" title="결제하기" description="예약을 확정하려면 결제를 완료해 주세요." />

        <Card className="mb-4 p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-bold text-ink">{info.fairName}</p>
              <p className="mt-1 text-sm text-muted">
                {visitDate} 방문 · {typeLabel}
              </p>
            </div>
            <p className="text-lg font-extrabold text-ink">{price.toLocaleString()}원</p>
          </div>
        </Card>

        <Card className="mb-6 p-6">
          <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
            <CreditCard size={16} />
            결제 수단
          </div>
          {/* TODO: 토스 결제위젯이 들어갈 자리 (나중에 제공되는 코드로 교체) */}
          <div className="grid place-items-center rounded-button border border-dashed border-line bg-page py-10 text-center text-sm text-muted">
            결제위젯 자리 (연동 예정)
          </div>
        </Card>

        <div className="flex justify-between">
          <Button variant="outline" onClick={() => setPhase("form")}>
            뒤로
          </Button>
          <Button onClick={() => setPhase("done")}>{price.toLocaleString()}원 결제하기</Button>
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
        <h2 className="text-lg font-extrabold text-ink">{info.fairName}</h2>
        <div className="mt-2 flex flex-col gap-1.5 text-sm text-muted">
          <span className="flex items-center gap-2">
            <MapPin size={16} className="shrink-0" />
            {info.placeName}
          </span>
          <span className="flex items-center gap-2">
            <CalendarDays size={16} className="shrink-0" />
            {info.period}
          </span>
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
          <div className="mb-6 grid gap-3 sm:grid-cols-3">
            {info.dates.map((date) => {
              const soldOut = !date.available || date.remainingCapacity === 0;
              const selected = date.fairDateId === selectedDateId;
              return (
                <button
                  key={date.fairDateId}
                  type="button"
                  disabled={soldOut}
                  onClick={() => setSelectedDateId(date.fairDateId)}
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
        </>
      ) : (
        <div className="mb-6">
          <h3 className="mb-3 text-sm font-bold text-ink">현장예매</h3>
          {onsite && onsite.available ? (
            <Card className="p-4">
              <p className="font-bold text-ink">오늘 방문 · {onsite.visitDate}</p>
              <p className="mt-1 text-xs text-muted">
                {formatTime(onsite.entryStartTime)} ~ {formatTime(onsite.entryEndTime)}
              </p>
              <p className="mt-2 text-xs text-muted">
                현장예매는 오늘 방문만 가능하고, 결제 후 바로 입장할 수 있어요.
              </p>
            </Card>
          ) : (
            <Card className="p-4 text-sm text-muted">현재 이 행사는 현장예매를 접수하지 않아요.</Card>
          )}
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

      <div className="flex justify-end">
        <Button disabled={!canProceed} onClick={handleProceed}>
          {isPaid ? "결제하고 예약하기" : "예약 완료하기"}
        </Button>
      </div>

      {confirmDialog}
    </div>
  );
}
