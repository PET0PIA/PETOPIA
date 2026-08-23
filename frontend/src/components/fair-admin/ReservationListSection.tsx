import { useEffect, useState } from "react";
import { ApiError } from "../../api/client";
import {
  cancelReservationByAdmin,
  getFairReservationsForAdmin,
  type AdminReservationItem,
  type ReservationStatus,
} from "../../api/reservation";
import { Badge } from "../ui/Badge";
import { Button } from "../ui/Button";
import { Dialog } from "../ui/Dialog";
import { Select } from "../ui/Select";
import { Table } from "../ui/Table";
import { Textarea } from "../ui/Textarea";

const PAGE_SIZE = 20;
const REASON_MAX_LENGTH = 500;

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

/**
 * 관리자가 대신 취소해줄 수 있는 상태. 백엔드 ReservationCancellationService.ADMIN_CANCELABLE_STATUSES와
 * 같은 기준이다 - 이미 입장했거나(CHECKED_IN) 이미 끝난(CANCELED/EXPIRED) 예약은 손댈 게 없다.
 */
const cancelableStatuses: ReservationStatus[] = ["PENDING_PAYMENT", "CONFIRMED"];

/**
 * 확정 시각은 결제 대기·만료·결제 전 취소 건에서 비어 있다(reservations.reserved_at은 NULL 허용).
 * null 가드를 빼면 그 행이 섞인 페이지에서 표 전체가 렌더 중 터진다 - ReservationDetailPage의
 * 같은 이름 함수와 동일한 처리다.
 */
function formatDateTime(value: string | null) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

/** 취소하면 돈이 어떻게 되는지 - 관리자가 버튼을 누르기 전에 알아야 하는 유일한 정보다. */
function refundNotice(row: AdminReservationItem) {
  if (row.reservationStatus === "PENDING_PAYMENT") {
    return "아직 결제 전 예약이에요. 환불할 금액은 없고, 진행 중인 결제가 있으면 함께 취소돼요.";
  }
  if (row.amount > 0) {
    return `결제까지 끝난 예약이에요. 취소하면 ${row.amount.toLocaleString()}원이 전액 환불돼요.`;
  }
  return "무료 예약이라 환불할 금액이 없어요.";
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
 *
 * <p>행사 관리자 콘솔(/fair-admin/reservations)과 최고관리자 드릴다운
 * (/admin/dashboard/fairs/:fairId/reservations)이 이 컴포넌트를 공유하므로, 대행 취소 버튼도
 * 양쪽에 동시에 뜬다. "남의 행사는 못 건드린다"는 백엔드가 담당 행사 검사로 막는다.
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
  /** 취소를 처리한 뒤 목록을 다시 불러오려고 올리는 값. */
  const [reloadKey, setReloadKey] = useState(0);

  const [cancelTarget, setCancelTarget] = useState<AdminReservationItem | null>(null);
  const [cancelReason, setCancelReason] = useState("");
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [canceledNotice, setCanceledNotice] = useState<string | null>(null);

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

  function openCancelDialog(row: AdminReservationItem) {
    setCancelTarget(row);
    setCancelReason("");
    setCancelError(null);
    setCanceledNotice(null);
  }

  function closeCancelDialog() {
    if (canceling) return;
    setCancelTarget(null);
  }

  async function handleCancelSubmit() {
    if (!cancelTarget) return;
    const reason = cancelReason.trim();
    if (!reason) {
      setCancelError("취소 사유를 입력해 주세요.");
      return;
    }

    setCanceling(true);
    setCancelError(null);
    try {
      const result = await cancelReservationByAdmin(fairId, cancelTarget.reservationId, reason);
      setCanceledNotice(
        result.refunded
          ? `${cancelTarget.reservationNo} 예약을 취소하고 ${(result.refundAmount ?? 0).toLocaleString()}원을 환불했어요.`
          : `${cancelTarget.reservationNo} 예약을 취소했어요.`,
      );
      setCancelTarget(null);
      setReloadKey((key) => key + 1);
    } catch (err: unknown) {
      setCancelError(err instanceof ApiError ? err.message : "예약을 취소하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setCanceling(false);
    }
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
  }, [fairId, visitDate, status, page, reloadKey]);

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

      {canceledNotice && (
        <div className="surface mb-4 border-leaf/30 bg-leaf-soft p-4 text-sm font-bold text-ink" role="status">
          {canceledNotice}
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
            <th className="px-4 py-3">확정일시</th>
            <th className="px-4 py-3 text-right">관리</th>
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td colSpan={7} className="px-4 py-10 text-center text-sm text-muted">불러오는 중이에요...</td>
            </tr>
          ) : items.length === 0 ? (
            <tr>
              <td colSpan={7} className="px-4 py-10 text-center text-sm text-muted">조건에 맞는 예약이 없어요.</td>
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
                <td className="px-4 py-3 text-right">
                  {cancelableStatuses.includes(row.reservationStatus) ? (
                    <Button
                      variant="outline"
                      className="min-h-9 px-3"
                      onClick={() => openCancelDialog(row)}
                    >
                      예약 취소
                    </Button>
                  ) : (
                    <span className="text-sm text-muted">-</span>
                  )}
                </td>
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

      <Dialog open={cancelTarget !== null} onClose={closeCancelDialog} title="예약 대행 취소">
        {cancelTarget && (
          <div className="space-y-4">
            <dl className="surface bg-surface-alt p-4 text-sm">
              <div className="flex justify-between gap-4 py-0.5">
                <dt className="text-muted">예약번호</dt>
                <dd className="font-mono font-bold text-ink">{cancelTarget.reservationNo}</dd>
              </div>
              <div className="flex justify-between gap-4 py-0.5">
                <dt className="text-muted">예약자</dt>
                <dd className="font-bold text-ink">{cancelTarget.reserverName}</dd>
              </div>
              <div className="flex justify-between gap-4 py-0.5">
                <dt className="text-muted">방문일</dt>
                <dd className="text-ink">{cancelTarget.visitDate}</dd>
              </div>
            </dl>

            <p className="text-sm text-ink">{refundNotice(cancelTarget)}</p>
            <p className="text-sm text-muted">
              취소하면 되돌릴 수 없어요. 관람객에게 취소 알림이 사유와 함께 전달되고, 처리 내역은 감사 로그에 남아요.
            </p>

            <div>
              <label htmlFor="admin-cancel-reason" className="mb-1.5 block text-sm font-bold text-ink">
                취소 사유 <span className="text-primary-strong">*</span>
              </label>
              <Textarea
                id="admin-cancel-reason"
                value={cancelReason}
                maxLength={REASON_MAX_LENGTH}
                onChange={(event) => setCancelReason(event.target.value)}
                placeholder="예) 관람객 전화 요청 - 중복 예약 정리"
              />
              <p className="mt-1 text-right text-xs text-muted">{cancelReason.length}/{REASON_MAX_LENGTH}</p>
            </div>

            {cancelError && (
              <div className="surface border-primary-strong/30 bg-primary-soft p-3 text-sm text-primary-strong" role="alert">
                {cancelError}
              </div>
            )}

            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={closeCancelDialog} disabled={canceling}>닫기</Button>
              <Button onClick={handleCancelSubmit} disabled={canceling || cancelReason.trim().length === 0}>
                {canceling ? "취소 처리 중..." : "취소 처리하기"}
              </Button>
            </div>
          </div>
        )}
      </Dialog>
    </div>
  );
}
