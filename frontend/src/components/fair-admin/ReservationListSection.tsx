import { useEffect, useState } from "react";
import { ApiError } from "../../api/client";
import {
  getFairReservationsForAdmin,
  type AdminReservationItem,
  type ReservationStatus,
} from "../../api/reservation";
import { Badge } from "../ui/Badge";
import { Button } from "../ui/Button";
import { Select } from "../ui/Select";
import { Table } from "../ui/Table";

const PAGE_SIZE = 20;

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
const statusFilterOptions = Object.keys(statusLabels) as ReservationStatus[];

function formatDateTime(value: string) {
  return value.replace("T", " ").slice(0, 16);
}

interface ReservationListSectionProps {
  fairId: number;
  /** 위쪽 운영일별 집계 표에서 이미 가져온 운영일 목록 - 따로 조회하지 않고 재사용한다. */
  operationDates: string[];
}

/**
 * 운영일별 집계(ReservationStatusSection)만으로는 "누가" 예약했는지 알 수 없어서, 예약자별
 * 상세 목록을 그 아래에 붙인다. 원래 이 목록은 목업(FairReservationsPage, /fair-admin/reservations/list)
 * 으로 따로 있었는데 메뉴에도 없고 실제 데이터도 아니어서 여기로 합쳤다.
 */
export function ReservationListSection({ fairId, operationDates }: ReservationListSectionProps) {
  const [visitDate, setVisitDate] = useState("ALL");
  const [status, setStatus] = useState("ALL");
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<AdminReservationItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 필터를 바꿀 때 첫 페이지로 되돌린다(useEffect가 아니라 핸들러에서 - 필터 변경과
  // 페이지 초기화는 한 사용자 조작에 붙은 동작이라 렌더 이펙트로 미룰 이유가 없다).
  function handleVisitDateChange(value: string) {
    setVisitDate(value);
    setPage(0);
  }
  function handleStatusChange(value: string) {
    setStatus(value);
    setPage(0);
  }

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(null);
    getFairReservationsForAdmin(fairId, {
      visitDate: visitDate === "ALL" ? undefined : visitDate,
      status: status === "ALL" ? undefined : (status as ReservationStatus),
      page,
      size: PAGE_SIZE,
    })
      .then((response) => {
        if (ignore) return;
        setItems(response.items);
        setTotalElements(response.totalElements);
        setTotalPages(response.totalPages);
      })
      .catch((err: unknown) => {
        if (ignore) return;
        setItems([]);
        setError(err instanceof ApiError ? err.message : "예약자 목록을 불러오지 못했어요.");
      })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [fairId, visitDate, status, page]);

  return (
    <div>
      <h2 className="mb-3 mt-8 text-lg font-extrabold text-ink">예약자 목록</h2>

      <div className="mb-4 flex flex-col gap-3 sm:flex-row">
        <div className="sm:w-56">
          <span className="mb-1.5 block text-sm font-bold text-ink">운영일</span>
          <Select value={visitDate} onChange={(event) => handleVisitDateChange(event.target.value)} aria-label="운영일 필터">
            <option value="ALL">전체 운영일</option>
            {operationDates.map((date) => (
              <option key={date} value={date}>{date}</option>
            ))}
          </Select>
        </div>
        <div className="sm:w-56">
          <span className="mb-1.5 block text-sm font-bold text-ink">상태</span>
          <Select value={status} onChange={(event) => handleStatusChange(event.target.value)} aria-label="상태 필터">
            <option value="ALL">전체 상태</option>
            {statusFilterOptions.map((s) => (
              <option key={s} value={s}>{statusLabels[s]}</option>
            ))}
          </Select>
        </div>
      </div>

      {error && (
        <div className="surface mb-4 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          {error}
        </div>
      )}

      {!error && (
        <p className="mb-3 text-sm text-muted">
          총 <span className="font-bold text-ink">{totalElements.toLocaleString()}</span>건
        </p>
      )}

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
          {loading ? (
            <tr>
              <td colSpan={6} className="px-4 py-10 text-center text-sm text-muted">불러오는 중이에요...</td>
            </tr>
          ) : items.length === 0 ? (
            <tr>
              <td colSpan={6} className="px-4 py-10 text-center text-sm text-muted">조건에 맞는 예약이 없어요.</td>
            </tr>
          ) : (
            items.map((row) => (
              <tr key={row.reservationId} className="border-b border-line last:border-0">
                <td className="px-4 py-3 font-mono text-sm font-bold text-ink">{row.reservationNo}</td>
                <td className="px-4 py-3">{row.reserverName}</td>
                <td className="px-4 py-3 text-muted">{row.visitDate}</td>
                <td className="px-4 py-3">
                  <Badge tone={statusTones[row.reservationStatus]}>{statusLabels[row.reservationStatus]}</Badge>
                </td>
                <td className="px-4 py-3 text-right text-muted">{row.amount.toLocaleString()}원</td>
                <td className="px-4 py-3 text-muted">{formatDateTime(row.reservedAt)}</td>
              </tr>
            ))
          )}
        </tbody>
      </Table>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <Button variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>이전</Button>
          <span className="text-sm text-muted">{page + 1} / {totalPages}</span>
          <Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>다음</Button>
        </div>
      )}
    </div>
  );
}
