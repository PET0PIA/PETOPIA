import { ChevronRight, Ticket } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { ApiError } from "../../api/client";
import { getMyReservations, type ReservationListItem, type ReservationStatus } from "../../api/reservation";

// 예약 상태 표시 규칙: 초록=확정·입장 완료(정상), 회색=취소·만료(무효/종료),
// 결제 대기는 "이어서 결제해야 하는 핵심 행동"이라 눈에 띄게(현재 primary, 이후 빨강 토큰으로 교체 예정).
const statusLabels: Record<ReservationStatus, string> = {
  PENDING_PAYMENT: "결제 대기",
  CONFIRMED: "예약 확정",
  CHECKED_IN: "입장 완료",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};
// 초록=결제 대기(이어서 결제), 검정=예약 확정, 회색=지난/무효(입장 완료·취소·만료).
const statusTones: Record<ReservationStatus, "leaf" | "ink" | "neutral"> = {
  PENDING_PAYMENT: "leaf",
  CONFIRMED: "ink",
  CHECKED_IN: "neutral",
  CANCELED: "neutral",
  EXPIRED: "neutral",
};

// 카드 전체를 흐릿하게(지난 예약 느낌) 처리할 상태.
const inactiveStatuses: ReservationStatus[] = ["CHECKED_IN", "CANCELED", "EXPIRED"];

function formatTime(time: string) {
  return time.slice(0, 5);
}

export function MyReservationsPage() {
  const [reservations, setReservations] = useState<ReservationListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
            const inactive = inactiveStatuses.includes(item.reservationStatus);
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
                      <Badge tone={statusTones[item.reservationStatus]} className="shrink-0">
                        {statusLabels[item.reservationStatus]}
                      </Badge>
                    </div>
                    <p className="text-sm text-muted">
                      {item.visitDate} · {formatTime(item.entryStartTime)}~{formatTime(item.entryEndTime)}
                    </p>
                    {item.amount > 0 && (
                      <p className="text-sm text-muted">{item.amount.toLocaleString()}원</p>
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
