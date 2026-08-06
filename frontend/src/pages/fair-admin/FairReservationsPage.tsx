import { useMemo, useState } from "react";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import {
  mockAdminReservations,
  mockOperationDates,
  mockReservationSummary,
} from "../../mocks/adminReservations";
import type { ReservationStatus } from "../../mocks/reservations";

// 상태 표시 규칙(AI_UI_RULES 색 규칙): 빨강=결제 대기, 초록=확정·입장 완료, 회색=취소·만료.
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

// 필터 드롭다운에 쓸 상태 목록(라벨과 함께)
const statusFilterOptions = Object.keys(statusLabels) as ReservationStatus[];

const summaryTiles = [
  { label: "정원", key: "capacity", tone: "text-ink" },
  { label: "예약", key: "reserved", tone: "text-primary-strong" },
  { label: "입장 완료", key: "checkedIn", tone: "text-ink" },
  { label: "취소·만료", key: "closed", tone: "text-muted" },
] as const;

export function FairReservationsPage() {
  const [dateFilter, setDateFilter] = useState<string>("ALL");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");

  // 필터는 화면 안에서만 동작한다(서버 호출 없음).
  const filtered = useMemo(
    () =>
      mockAdminReservations.filter(
        (row) =>
          (dateFilter === "ALL" || row.visitDate === dateFilter) &&
          (statusFilter === "ALL" || row.reservationStatus === statusFilter),
      ),
    [dateFilter, statusFilter],
  );

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader
        eyebrow="박람회 관리자"
        title="예약 현황"
        description="담당 행사의 예약 상태를 운영일·상태별로 확인해요."
      />

      <div className="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
        {summaryTiles.map((tile) => (
          <Card key={tile.key} className="p-5">
            <p className="text-sm text-muted">{tile.label}</p>
            <p className={`mt-1 text-2xl font-extrabold ${tile.tone}`}>
              {mockReservationSummary[tile.key].toLocaleString()}
            </p>
          </Card>
        ))}
      </div>

      <div className="mb-6 flex flex-col gap-3 sm:flex-row">
        <div className="sm:w-56">
          <span className="mb-1.5 block text-sm font-bold text-ink">운영일</span>
          <Select value={dateFilter} onChange={(event) => setDateFilter(event.target.value)} aria-label="운영일 필터">
            <option value="ALL">전체 운영일</option>
            {mockOperationDates.map((date) => (
              <option key={date} value={date}>{date}</option>
            ))}
          </Select>
        </div>
        <div className="sm:w-56">
          <span className="mb-1.5 block text-sm font-bold text-ink">상태</span>
          <Select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} aria-label="상태 필터">
            <option value="ALL">전체 상태</option>
            {statusFilterOptions.map((status) => (
              <option key={status} value={status}>{statusLabels[status]}</option>
            ))}
          </Select>
        </div>
      </div>

      <p className="mb-3 text-sm text-muted">
        총 <span className="font-bold text-ink">{filtered.length}</span>건
      </p>

      <Table>
        <thead>
          <tr className="border-b border-line text-xs font-bold text-muted">
            <th className="px-4 py-3">예약번호</th>
            <th className="px-4 py-3">예약자</th>
            <th className="px-4 py-3">방문일</th>
            <th className="px-4 py-3">상태</th>
            <th className="px-4 py-3 text-right">금액</th>
            <th className="px-4 py-3">예약일시</th>
          </tr>
        </thead>
        <tbody>
          {filtered.length === 0 ? (
            <tr>
              <td colSpan={6} className="px-4 py-10 text-center text-sm text-muted">
                조건에 맞는 예약이 없어요.
              </td>
            </tr>
          ) : (
            filtered.map((row) => (
              <tr key={row.reservationId} className="border-b border-line last:border-0">
                <td className="px-4 py-3 font-mono text-sm font-bold text-ink">{row.reservationNo}</td>
                <td className="px-4 py-3">{row.visitorName}</td>
                <td className="px-4 py-3 text-muted">{row.visitDate}</td>
                <td className="px-4 py-3">
                  <Badge tone={statusTones[row.reservationStatus]}>
                    {statusLabels[row.reservationStatus]}
                  </Badge>
                </td>
                <td className="px-4 py-3 text-right text-muted">{row.amount.toLocaleString()}원</td>
                <td className="px-4 py-3 text-muted">{row.reservedAt}</td>
              </tr>
            ))
          )}
        </tbody>
      </Table>
    </div>
  );
}
