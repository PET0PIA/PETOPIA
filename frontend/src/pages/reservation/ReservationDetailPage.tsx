import { ChevronLeft, CreditCard, QrCode } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { DropdownMenu } from "../../components/ui/DropdownMenu";
import { QrCanvas } from "../../components/ui/QrCanvas";
import { useConfirm } from "../../components/ui/useConfirm";
import { PaymentMethodPicker } from "../../components/payment/PaymentMethodPicker";
import { ApiError } from "../../api/client";
import { createReservationDepositPayment } from "../../api/payment";
import {
  cancelReservation,
  changeVisitDate,
  getEntryQr,
  getReservationAvailability,
  getReservationDetail,
  type ReservationAvailabilityDate,
  type ReservationDetail,
} from "../../api/reservation";
import { useAuth } from "../../contexts/AuthContext";
import { isTossConfigured, requestReservationPayment, type PaymentMethodOption } from "../../payments/toss";
import {
  formatEntryTime,
  formatVisitDateDow,
  reservationStatusLabels,
  reservationStatusTones,
  reservationTypeLabels,
} from "./reservationDisplay";

// ISO 일시(2026-08-01T14:00:00)를 "2026-08-01 14:00"으로 다듬는다.
function formatDateTime(value: string | null) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

/**
 * 취소 실패 응답을 사용자 문구로 바꾼다.
 *
 * R021(결제 진행 중)은 잠시 후 재시도하면 풀리는 일시적 상태라 재시도 안내를 붙인다 —
 * 서버 메시지 그대로 두면 영구 실패처럼 읽힌다. R020은 원장이 안 맞는 상황이라 사용자가
 * 혼자 해결할 수 없어 문의로 안내한다. 나머지(R019 마감, R013 상태)는 서버 메시지가 이미
 * 구체적이라 그대로 보여준다.
 */
function cancelErrorMessage(err: unknown) {
  if (!(err instanceof ApiError)) return "예약 취소에 실패했어요.";
  switch (err.code) {
    case "R021":
      return "결제가 진행 중이라 지금은 취소할 수 없어요. 잠시 후 다시 시도해 주세요.";
    case "R020":
      return "환불할 결제 내역을 찾을 수 없어 취소를 진행하지 못했어요. 고객센터에 문의해 주세요.";
    default:
      return err.message;
  }
}

function BackLink() {
  return (
    <Link
      to="/reservations/me"
      className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink"
    >
      <ChevronLeft size={16} />
      내 예약 목록
    </Link>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold text-muted">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

export function ReservationDetailPage() {
  const { reservationId } = useParams<{ reservationId: string }>();
  const { confirm, confirmDialog } = useConfirm();
  // 결제 이어가기: 결제 생성 API가 예약 소유자 대조에 쓰는 userId를 여기서 넘긴다.
  const { user } = useAuth();

  const [reservation, setReservation] = useState<ReservationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 결제 대기 예약을 이어서 결제하는 흐름(예매 화면의 결제 단계와 동일).
  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethodOption>("CARD");

  // 입장 QR은 별도 API로 실제 토큰을 받아 그린다.
  const [qrToken, setQrToken] = useState<string | null>(null);
  const [qrError, setQrError] = useState<string | null>(null);

  // 취소 실패(R019/R013/R020/R021 등) 메시지.
  const [actionError, setActionError] = useState<string | null>(null);
  // 취소 성공 안내. 유료 예약이면 환불 금액까지 알려준다.
  const [cancelNotice, setCancelNotice] = useState<string | null>(null);

  // 방문일 변경 다이얼로그.
  const [dateDialogOpen, setDateDialogOpen] = useState(false);
  const [availDates, setAvailDates] = useState<ReservationAvailabilityDate[]>([]);
  const [availLoading, setAvailLoading] = useState(false);
  const [availError, setAvailError] = useState<string | null>(null);
  const [selectedNewDate, setSelectedNewDate] = useState<string | null>(null);
  const [changeSubmitting, setChangeSubmitting] = useState(false);
  const [changeError, setChangeError] = useState<string | null>(null);

  const id = Number(reservationId);
  // 경로 파라미터가 양의 정수가 아니면(예: /reservations/me/abc) NaN을 API URL에 싣지 않는다.
  const idValid = Number.isInteger(id) && id > 0;

  useEffect(() => {
    if (!idValid) return; // 잘못된 경로 파라미터면 요청하지 않는다
    let alive = true;
    getReservationDetail(id)
      .then((res) => {
        if (alive) setReservation(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "예약을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [id, idValid]);

  // 상세가 로드되고 QR 표시 가능 상태면 실제 토큰을 받아온다.
  const qrAvailable = reservation?.qrAvailable ?? false;
  useEffect(() => {
    if (!qrAvailable) return;
    let alive = true;
    getEntryQr(id)
      .then((res) => {
        if (alive) setQrToken(res.qrToken);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        // R016: 입장 종료 등으로 QR을 쓸 수 없는 경우
        setQrError(err instanceof ApiError ? err.message : "입장 QR을 불러오지 못했어요.");
      });
    return () => {
      alive = false;
    };
  }, [id, qrAvailable]);

  if (!idValid) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="잘못된 예약 주소예요."
            description="예약 주소가 올바르지 않아요. 목록에서 다시 선택해 주세요."
            actionTo="/reservations/me"
            actionLabel="내 예약 목록으로"
          />
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <p className="py-16 text-center text-sm text-muted">예약 정보를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (error || !reservation) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="예약을 찾을 수 없어요."
            description={error ?? "목록에서 예약을 다시 선택해 주세요."}
            actionTo="/reservations/me"
            actionLabel="내 예약 목록으로"
          />
        </div>
      </div>
    );
  }

  // 케밥 노출은 서버가 계산한 플래그를 그대로 쓴다(유형·상태 규칙이 여기 반영돼 있음).
  // 유료 확정 예약도 취소 가능하다 — 서버가 예약금을 전액 환불하고 CANCELED로 넘긴다.
  // 12시간 마감은 플래그에 없어서, 실제 변경·취소 호출 시 R018/R019로 최종 검증된다.
  const target = reservation; // 아래 콜백에서 non-null로 쓰기 위한 지역 별칭

  /**
   * 결제 대기 예약을 이어서 결제한다. 예매 화면(TicketReservationPage)의 결제 단계와 같은 흐름 —
   * 결제를 생성한 뒤 토스 결제창을 띄운다. 정상 흐름이면 /payments/success로 리다이렉트되므로
   * 이 함수가 끝까지 진행되면 페이지는 곧 사라진다(그래서 성공 시 paying을 되돌리지 않는다).
   */
  const handleResumePayment = async () => {
    if (paying) return;
    if (!user) {
      setPayError("로그인이 풀렸어요. 다시 로그인한 뒤 결제를 이어가 주세요.");
      return;
    }
    setPayError(null);
    setPaying(true);
    try {
      const created = await createReservationDepositPayment(id, user.userId);
      await requestReservationPayment({
        paymentId: created.paymentId,
        reservationId: id,
        // 서버가 만든 orderId·amount를 가공 없이 넘긴다 - 다르면 토스가 승인을 거절한다.
        orderId: created.orderId ?? `PAYMENT_${created.paymentId}`,
        amount: created.amount,
        orderName: `${target.fairName} 예약금`,
        method: paymentMethod,
      });
    } catch (err) {
      // 결제창을 사용자가 닫은 경우도 여기로 온다.
      setPayError(err instanceof ApiError ? err.message : "결제를 시작하지 못했어요. 잠시 후 다시 시도해 주세요.");
      setPaying(false);
    }
  };

  const openChangeDialog = () => {
    setSelectedNewDate(target.visitDate);
    setChangeError(null);
    setAvailError(null);
    setDateDialogOpen(true);
    // 예매 화면과 같은 방식으로 이 행사의 예약 가능 운영일·잔여석을 불러온다.
    setAvailLoading(true);
    getReservationAvailability(target.fairId)
      .then((res) => setAvailDates(res.dates))
      .catch((err: unknown) =>
        setAvailError(err instanceof ApiError ? err.message : "예약 가능한 날짜를 불러오지 못했어요."),
      )
      .finally(() => setAvailLoading(false));
  };

  const handleChangeDate = async () => {
    if (!selectedNewDate) return;
    setChangeSubmitting(true);
    setChangeError(null);
    try {
      const res = await changeVisitDate(id, selectedNewDate);
      setReservation((previous) =>
        previous
          ? {
              ...previous,
              visitDate: res.visitDate,
              entryStartTime: res.entryStartTime,
              entryEndTime: res.entryEndTime,
              reservationStatus: res.reservationStatus,
            }
          : previous,
      );
      setDateDialogOpen(false);
    } catch (err) {
      // R018 변경 마감 / R002 날짜 불가 / R004 마감 / R013 상태 불가
      setChangeError(err instanceof ApiError ? err.message : "방문일 변경에 실패했어요.");
    } finally {
      setChangeSubmitting(false);
    }
  };

  const handleCancel = async () => {
    // 돈이 걸린 취소는 환불 금액을 확인 창에서 먼저 알려준다. 결제 전(PENDING_PAYMENT) 예약은
    // 아직 받은 돈이 없어 환불이 아니라 결제 취소라, 유료여도 환불 문구를 넣지 않는다.
    const refundExpected = target.amount > 0 && target.reservationStatus === "CONFIRMED";
    const proceed = await confirm({
      title: "예약을 취소할까요?",
      description: refundExpected
        ? `${target.fairName} (${target.visitDate}) 예약을 취소해요.\n`
          + `예약금 ${target.amount.toLocaleString()}원은 전액 환불돼요. 취소하면 되돌릴 수 없어요.`
        : `${target.fairName} (${target.visitDate}) 예약을 취소해요. 취소하면 되돌릴 수 없어요.`,
      confirmLabel: "예약 취소",
    });
    if (!proceed) return;
    setActionError(null);
    setCancelNotice(null);
    try {
      const res = await cancelReservation(id);
      setReservation((previous) =>
        previous
          ? {
              ...previous,
              reservationStatus: res.reservationStatus,
              qrAvailable: false,
              canCancel: false,
              canChangeVisitDate: false,
            }
          : previous,
      );
      setQrToken(null);
      setCancelNotice(
        res.refunded && res.refundAmount !== null
          ? `예약이 취소되고 예약금 ${res.refundAmount.toLocaleString()}원의 환불이 접수됐어요. `
            + "카드사에 따라 영업일 기준 3~5일 이내 반영돼요."
          : "예약이 취소됐어요.",
      );
    } catch (err) {
      setActionError(cancelErrorMessage(err));
    }
  };

  const menuItems: { label: string; onSelect: () => void }[] = [];
  if (reservation.canChangeVisitDate) {
    menuItems.push({ label: "방문일 변경", onSelect: openChangeDialog });
  }
  if (reservation.canCancel) {
    menuItems.push({ label: "예약 취소", onSelect: handleCancel });
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <BackLink />

      <div className="mt-4 mb-6 flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Badge tone={reservationStatusTones[reservation.reservationStatus]}>
              {reservationStatusLabels[reservation.reservationStatus]}
            </Badge>
            <span className="text-xs font-bold text-muted">{reservationTypeLabels[reservation.reservationType]}</span>
          </div>
          <h1 className="mt-2 text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">
            {reservation.fairName}
          </h1>
        </div>
        {menuItems.length > 0 && <DropdownMenu label="관리" items={menuItems} />}
      </div>

      {actionError && (
        <p role="alert" className="mb-4 text-sm font-bold text-primary-strong">
          {actionError}
        </p>
      )}

      {cancelNotice && (
        <p role="status" className="mb-4 rounded-card bg-leaf-soft px-4 py-3 text-sm font-bold text-ink">
          {cancelNotice}
        </p>
      )}

      {/* 결제 이어가기: 결제 대기 예약에서만(paymentAvailable) 뜬다. 결제를 마쳐야 예약이 확정된다. */}
      {reservation.paymentAvailable && (
        <Card className="mb-4 p-6">
          <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
            <CreditCard size={16} />
            예약금 결제
          </div>
          <div className="flex items-center justify-between gap-4">
            <p className="text-sm text-muted">결제를 마쳐야 예약이 확정돼요.</p>
            <p className="shrink-0 text-lg font-extrabold text-ink">{reservation.amount.toLocaleString()}원</p>
          </div>
          {isTossConfigured() ? (
            <div className="mt-4">
              <PaymentMethodPicker value={paymentMethod} onChange={setPaymentMethod} disabled={paying} />
              <div className="mt-4 flex justify-end">
                <Button disabled={paying} onClick={handleResumePayment}>
                  {paying ? "결제창을 여는 중…" : "결제 계속하기"}
                </Button>
              </div>
            </div>
          ) : (
            <div className="mt-4 grid place-items-center gap-1 rounded-button border border-dashed border-line bg-page py-8 text-center text-sm text-muted">
              <p className="font-bold text-ink">결제 설정이 없어요</p>
              <p>결제 클라이언트 키가 주입되지 않았어요. 관리자에게 문의해 주세요.</p>
            </div>
          )}
          {payError && <p className="mt-3 text-sm font-bold text-primary-strong">{payError}</p>}
          <p className="mt-3 text-xs leading-5 text-muted">
            제한시각까지 결제하지 않으면 예약이 자동으로 만료돼요.
          </p>
        </Card>
      )}

      {/* 입장 QR */}
      <Card className="mb-4 p-6">
        <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
          <QrCode size={16} />
          입장 QR
        </div>
        {reservation.qrAvailable && qrToken ? (
          <div className="grid place-items-center gap-3 py-4">
            <QrCanvas value={qrToken} size={176} />
            <p className="text-xs text-muted">
              현장 입장 스캐너에 보여주세요. 화면 밝기를 높이면 인식이 잘 돼요.
            </p>
          </div>
        ) : reservation.qrAvailable && qrError ? (
          <p className="text-sm text-muted">{qrError}</p>
        ) : reservation.qrAvailable ? (
          <p className="text-sm text-muted">입장 QR을 불러오는 중이에요…</p>
        ) : (
          <p className="text-sm text-muted">
            아직 입장 QR을 표시할 수 없는 예약이에요. (예약이 확정되고 입장 가능 시간일 때 표시돼요.)
          </p>
        )}
      </Card>

      {/* 예약 상세 정보 */}
      <Card className="p-6">
        <dl className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2">
          <DetailRow label="예약번호" value={reservation.reservationNo} />
          <DetailRow label="예약 유형" value={reservationTypeLabels[reservation.reservationType]} />
          <DetailRow label="방문일" value={formatVisitDateDow(reservation.visitDate)} />
          <DetailRow
            label="입장 시간"
            value={`${formatEntryTime(reservation.entryStartTime)} ~ ${formatEntryTime(reservation.entryEndTime)}`}
          />
          <DetailRow
            label="결제 금액"
            value={reservation.amount === 0 ? "무료" : `${reservation.amount.toLocaleString()}원`}
          />
          <DetailRow label="예약 확정시각" value={formatDateTime(reservation.reservedAt)} />
          <DetailRow label="최초 입장시각" value={formatDateTime(reservation.checkedInAt)} />
        </dl>
      </Card>

      <Dialog open={dateDialogOpen} onClose={() => setDateDialogOpen(false)} title="방문일 변경">
        <div className="space-y-4">
          <p className="text-sm text-muted">바꿀 방문일을 선택해 주세요.</p>

          {availLoading ? (
            <p className="py-6 text-center text-sm text-muted">예약 가능한 날짜를 불러오는 중이에요…</p>
          ) : availError ? (
            <p className="text-sm text-muted">{availError}</p>
          ) : availDates.length === 0 ? (
            <p className="text-sm text-muted">지금 선택할 수 있는 방문일이 없어요.</p>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2">
              {availDates.map((date) => {
                const soldOut = !date.available || date.remainingCapacity === 0;
                const selected = date.visitDate === selectedNewDate;
                return (
                  <button
                    key={date.visitDate}
                    type="button"
                    disabled={soldOut}
                    onClick={() => setSelectedNewDate(date.visitDate)}
                    className={`rounded-card border p-3 text-left transition ${
                      selected ? "border-primary-strong bg-primary-soft" : "border-line bg-card hover:bg-page"
                    } ${soldOut ? "cursor-not-allowed opacity-50 hover:bg-card" : ""}`}
                  >
                    <p className="font-bold text-ink">{formatVisitDateDow(date.visitDate)}</p>
                    <p className="mt-1 text-xs text-muted">
                      {formatEntryTime(date.entryStartTime)} ~ {formatEntryTime(date.entryEndTime)}
                    </p>
                    <p className="mt-1 text-xs font-bold">
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

          {changeError && <p className="text-sm font-bold text-primary-strong">{changeError}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setDateDialogOpen(false)}>
              닫기
            </Button>
            <Button onClick={handleChangeDate} disabled={!selectedNewDate || changeSubmitting}>
              {changeSubmitting ? "변경 중…" : "변경"}
            </Button>
          </div>
        </div>
      </Dialog>

      {confirmDialog}
    </div>
  );
}
