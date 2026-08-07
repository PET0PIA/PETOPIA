import { ChevronLeft, QrCode } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { DropdownMenu } from "../../components/ui/DropdownMenu";
import { Input } from "../../components/ui/Input";
import { QrCanvas } from "../../components/ui/QrCanvas";
import { useConfirm } from "../../components/ui/useConfirm";
import {
  mockReservations,
  type ReservationListItem,
  type ReservationStatus,
  type ReservationType,
} from "../../mocks/reservations";

// 목록 화면과 같은 상태 표시 규칙(색 규칙)을 그대로 쓴다.
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
const typeLabels: Record<ReservationType, string> = {
  ADVANCE: "사전예약",
  ONSITE_DIRECT: "현장예매",
};

function formatTime(time: string) {
  return time.slice(0, 5);
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

  // 백엔드 연동 전이라 mock에서 찾아 초기값으로 쓴다. 방문일 변경·취소는 화면 안에서만 반영한다.
  const initial = mockReservations.find((item) => String(item.reservationId) === reservationId) ?? null;
  const [reservation, setReservation] = useState<ReservationListItem | null>(initial);
  const [dateDialogOpen, setDateDialogOpen] = useState(false);
  const [newDate, setNewDate] = useState("");

  if (!reservation) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="예약을 찾을 수 없어요."
            description="목록에서 예약을 다시 선택해 주세요."
            actionTo="/reservations/me"
            actionLabel="내 예약 목록으로"
          />
        </div>
      </div>
    );
  }

  // 케밥 메뉴 노출 규칙(백엔드 정책을 화면에서 흉내낸다):
  // - 방문일 변경: 확정된 사전예약이고 아직 종료 안 됨
  // - 예약 취소: 결제 대기 또는 확정, 아직 종료 안 됨
  const canChangeDate =
    reservation.reservationType === "ADVANCE" &&
    reservation.reservationStatus === "CONFIRMED" &&
    !reservation.isEnded;
  const canCancel =
    !reservation.isEnded &&
    (reservation.reservationStatus === "PENDING_PAYMENT" || reservation.reservationStatus === "CONFIRMED");

  const handleCancel = async () => {
    const proceed = await confirm({
      title: "예약을 취소할까요?",
      description: `${reservation.fairName} (${reservation.visitDate}) 예약을 취소해요. 취소하면 되돌릴 수 없어요.`,
      confirmLabel: "예약 취소",
    });
    if (!proceed) return;
    // 화면에서만 상태를 "취소됨"으로 바꾼다(서버 연동은 나중에).
    setReservation((previous) =>
      previous ? { ...previous, reservationStatus: "CANCELED", qrAvailable: false } : previous,
    );
  };

  const handleChangeDate = () => {
    // 화면에서만 방문일을 바꾼다(서버 연동은 나중에).
    setReservation((previous) => (previous ? { ...previous, visitDate: newDate } : previous));
    setDateDialogOpen(false);
  };

  const menuItems: { label: string; onSelect: () => void }[] = [];
  if (canChangeDate) {
    menuItems.push({
      label: "방문일 변경",
      onSelect: () => {
        setNewDate(reservation.visitDate);
        setDateDialogOpen(true);
      },
    });
  }
  if (canCancel) {
    menuItems.push({ label: "예약 취소", onSelect: handleCancel });
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <BackLink />

      <div className="mt-4 mb-6 flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Badge tone={statusTones[reservation.reservationStatus]}>
              {statusLabels[reservation.reservationStatus]}
            </Badge>
            <span className="text-xs font-bold text-muted">{typeLabels[reservation.reservationType]}</span>
          </div>
          <h1 className="mt-2 text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">
            {reservation.fairName}
          </h1>
        </div>
        {menuItems.length > 0 && <DropdownMenu label="관리" items={menuItems} />}
      </div>

      {/* 입장 QR (지금은 자리만, 실제 QR 코드는 나중에 제공되는 코드로 교체) */}
      <Card className="mb-4 p-6">
        <div className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
          <QrCode size={16} />
          입장 QR
        </div>
        {reservation.qrAvailable ? (
          <div className="grid place-items-center gap-3 py-4">
            {/* 지금은 목업 토큰으로 QR을 그린다. 서버 연동 시 실제 입장 QR 토큰을 value로 넘기면 된다. */}
            <QrCanvas value={`MOCK-QR-${reservation.reservationId}`} size={176} />
            <p className="text-xs text-muted">
              현장 입장 스캐너에 보여주세요. 화면 밝기를 높이면 인식이 잘 돼요.
            </p>
          </div>
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
          <DetailRow label="예약 유형" value={typeLabels[reservation.reservationType]} />
          <DetailRow label="방문일" value={reservation.visitDate} />
          <DetailRow
            label="입장 시간"
            value={`${formatTime(reservation.entryStartTime)} ~ ${formatTime(reservation.entryEndTime)}`}
          />
          <DetailRow
            label="결제 금액"
            value={reservation.amount === 0 ? "무료" : `${reservation.amount.toLocaleString()}원`}
          />
          <DetailRow label="예약 확정시각" value={reservation.reservedAt ?? "-"} />
          <DetailRow label="최초 입장시각" value={reservation.checkedInAt ?? "-"} />
        </dl>
      </Card>

      <Dialog open={dateDialogOpen} onClose={() => setDateDialogOpen(false)} title="방문일 변경">
        <div className="space-y-4">
          <p className="text-sm text-muted">
            새 방문일을 선택해 주세요. (지금은 화면에서만 바뀌고, 서버 연동은 나중이에요.)
          </p>
          <Input type="date" value={newDate} onChange={(event) => setNewDate(event.target.value)} />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setDateDialogOpen(false)}>
              닫기
            </Button>
            <Button onClick={handleChangeDate} disabled={!newDate}>
              변경
            </Button>
          </div>
        </div>
      </Dialog>

      {confirmDialog}
    </div>
  );
}
