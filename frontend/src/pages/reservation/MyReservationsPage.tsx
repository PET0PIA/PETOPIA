import { ChevronRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Table } from "../../components/ui/Table";
import { ApiError } from "../../api/client";
import { getMyReservations, type ReservationListItem, type ReservationStatus } from "../../api/reservation";

// 예약 상태 표시 규칙(AI_UI_RULES 색 규칙):
// 빨강=결제 대기(사용자가 이어서 결제해야 하는 핵심 행동), 초록=확정·입장 완료(정상),
// 회색=취소·만료(무효/종료).
const statusLabels: Record<ReservationStatus, string> = {
  PENDING_PAYMENT: "결제 대기",
  CONFIRMED: "예약 확정",
  CHECKED_IN: "입장 완료",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};
const statusTones: Record<ReservationStatus, "primary" | "leaf" | "neutral"> = {
  PENDING_PAYMENT: "primary",
  CONFIRMED: "leaf",
  CHECKED_IN: "leaf",
  CANCELED: "neutral",
  EXPIRED: "neutral",
};

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
    <div className="mx-auto max-w-5xl py-2">
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
          actionTo="/tickets"
          actionLabel="티켓 예매하러 가기"
        />
      ) : (
        <Table>
          <thead>
            <tr className="border-b border-line text-xs font-bold text-muted">
              <th className="px-4 py-3">행사</th>
              <th className="px-4 py-3">방문일</th>
              <th className="px-4 py-3">입장 시간</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3" aria-label="상세" />
            </tr>
          </thead>
          <tbody>
            {reservations.map((item) => (
              <tr key={item.reservationId} className="border-b border-line last:border-0 hover:bg-page">
                <td className="px-4 py-3">
                  <Link
                    to={`/reservations/me/${item.reservationId}`}
                    className="font-bold text-ink hover:underline"
                  >
                    {item.fairName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-muted">{item.visitDate}</td>
                <td className="px-4 py-3 text-muted">
                  {formatTime(item.entryStartTime)} ~ {formatTime(item.entryEndTime)}
                </td>
                <td className="px-4 py-3">
                  <Badge tone={statusTones[item.reservationStatus]}>
                    {statusLabels[item.reservationStatus]}
                  </Badge>
                </td>
                <td className="px-4 py-3 text-right">
                  <Link
                    to={`/reservations/me/${item.reservationId}`}
                    aria-label={`${item.fairName} 예약 상세 보기`}
                    className="inline-flex items-center justify-center rounded-button p-1 text-muted hover:text-ink"
                  >
                    <ChevronRight size={16} aria-hidden="true" />
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
}
