import { ChevronRight, Ticket } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { ApiError } from "../../api/client";
import { getMyReservations, type ReservationListItem } from "../../api/reservation";
import {
  fairCancellationLabel,
  formatEntryTime,
  formatRemaining,
  formatVisitDateDow,
  inactiveReservationStatuses,
  reservationStatusLabels,
  reservationStatusTones,
} from "./reservationDisplay";

export function MyReservationsPage() {
  const [reservations, setReservations] = useState<ReservationListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // 결제 대기 예약의 남은 시간을 1초마다 다시 그리기 위한 시계.
  const [now, setNow] = useState(() => Date.now());

  // 결제 대기 건이 하나도 없으면 시계를 돌리지 않는다 - 목록 전체를 1초마다 리렌더할 이유가 없다.
  const hasPendingPayment = reservations.some((item) => item.paymentAvailable && item.paymentExpiresAt);

  useEffect(() => {
    let alive = true;
    getMyReservations()
      .then((res) => {
        if (alive) setReservations(res.items);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "예약 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    if (!hasPendingPayment) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [hasPendingPayment]);

  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader
        eyebrow="내 예약"
        title="내 예약 목록"
        description="예매한 행사를 눌러 방문일·입장 정보와 입장 QR을 확인하고, 예약을 변경하거나 취소할 수 있어요."
      />

      {loading ? (
        <p className="py-16 text-center text-sm text-muted">예약 목록을 불러오는 중이에요…</p>
      ) : error ? (
        <EmptyState title="예약 목록을 불러오지 못했어요." description={error} />
      ) : reservations.length === 0 ? (
        <EmptyState
          title="아직 예약한 행사가 없어요."
          description="티켓 예매에서 관심 있는 행사를 예약하면 이곳에서 확인할 수 있어요."
          actionTo="/fairs/upcoming"
          actionLabel="티켓 예매하러 가기"
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {reservations.map((item) => {
            const inactive = inactiveReservationStatuses.includes(item.reservationStatus);
            // 결제 마감까지 남은 시간. 지났으면 null이고, 곧 만료 배치가 상태를 정리한다.
            const remaining = item.paymentAvailable ? formatRemaining(item.paymentExpiresAt, now) : null;
            return (
              <li key={item.reservationId}>
                <Link
                  to={`/reservations/me/${item.reservationId}`}
                  aria-label={`${item.fairName} 예약 상세 보기`}
                  className={`surface flex items-center gap-4 p-4 transition-colors hover:border-muted ${inactive ? "opacity-60" : ""}`}
                >
                  {item.fairPosterImageUrl ? (
                    <img
                      src={item.fairPosterImageUrl}
                      alt=""
                      className={`h-28 w-20 shrink-0 rounded-card object-cover ${inactive ? "grayscale" : ""}`}
                    />
                  ) : (
                    <div className="flex h-28 w-20 shrink-0 items-center justify-center rounded-card bg-surface-alt text-muted">
                      <Ticket size={24} aria-hidden="true" />
                    </div>
                  )}

                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <div className="flex items-start justify-between gap-2">
                      <h3 className="truncate font-bold text-ink">{item.fairName}</h3>
                      <div className="flex shrink-0 items-center gap-1.5">
                        {/* 주최측 취소는 내가 취소한 것과 다르므로 배지로 먼저 구분해 준다. */}
                        {item.canceledByFairCancellation && (
                          <Badge tone="sun">{fairCancellationLabel}</Badge>
                        )}
                        <Badge tone={reservationStatusTones[item.reservationStatus]}>
                          {reservationStatusLabels[item.reservationStatus]}
                        </Badge>
                      </div>
                    </div>
                    <p className="text-sm text-muted">
                      {formatVisitDateDow(item.visitDate)} · {formatEntryTime(item.entryStartTime)}~{formatEntryTime(item.entryEndTime)}
                    </p>
                    {item.amount > 0 && (
                      <p className="text-sm text-muted">{item.amount.toLocaleString()}원</p>
                    )}
                    {/* 결제 대기 예약: 카드를 누르면 상세에서 결제를 이어갈 수 있음을 알린다.
                        남은 시간을 같이 보여준다 - "결제 대기" 배지만으로는 언제까지 결제해야
                        하는지 알 수 없어, 그냥 두면 자동 만료되는 걸 모른 채 지나친다. */}
                    {item.paymentAvailable && (
                      <span className="mt-0.5 text-sm font-bold text-primary-strong">
                        결제 계속하기 ›
                        {remaining && (
                          <span className="ml-2 font-normal text-muted">결제 마감까지 {remaining} 남음</span>
                        )}
                      </span>
                    )}
                  </div>

                  <ChevronRight size={18} className="shrink-0 text-muted" aria-hidden="true" />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
