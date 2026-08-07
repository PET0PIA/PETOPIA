import { AlertCircle, Radio, Search } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  getQrIssuanceSummary,
  getReservationDashboard,
  subscribeReservationDashboard,
  type QrIssuanceSummary,
  type ReservationDateSummary,
} from "../../api/statistics";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Input } from "../../components/ui/Input";
import { Table } from "../../components/ui/Table";

function formatTime(time: string) {
  return time.slice(0, 5);
}

/** 확정(CONFIRMED)+입장완료(CHECKED_IN) 대비 입장완료 비율. 결제대기·취소·만료는 분모에서 제외한다. */
function entryRate(row: ReservationDateSummary): string {
  const validCount = row.confirmedCount + row.checkedInCount;
  if (validCount === 0) return "-";
  return `${Math.round((row.checkedInCount / validCount) * 100)}%`;
}

export function ReservationStatusPage() {
  // TODO 관리자 세션에 현재 담당 행사(fairId)가 연결되면 이 입력을 없애고 세션 값을 바로 쓴다.
  const [fairIdInput, setFairIdInput] = useState("");
  const [fairId, setFairId] = useState<number | null>(null);

  const [summary, setSummary] = useState<ReservationDateSummary[]>([]);
  const [qrSummary, setQrSummary] = useState<QrIssuanceSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [liveConnected, setLiveConnected] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);

  useEffect(() => {
    if (fairId === null) return;
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    Promise.all([getReservationDashboard(fairId), getQrIssuanceSummary(fairId)])
      .then(([dashboard, qr]) => {
        if (ignore) return;
        setSummary(dashboard);
        setQrSummary(qr);
      })
      .catch((error) => {
        if (!ignore) {
          setSummary([]);
          setQrSummary([]);
          setLoadError(error instanceof ApiError ? error.message : "예약 현황을 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId, reloadTick]);

  // 결제완료·취소·QR 스캔 등 예약 상태가 바뀔 때마다 서버가 최신 요약을 밀어준다.
  useEffect(() => {
    if (fairId === null) return;
    setLiveConnected(false);
    const unsubscribe = subscribeReservationDashboard(fairId, (data) => {
      setLiveConnected(true);
      setSummary(data);
      getQrIssuanceSummary(fairId).then(setQrSummary).catch(() => {});
    });
    return () => {
      unsubscribe();
      setLiveConnected(false);
    };
  }, [fairId]);

  function handleLoadFair(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(fairIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setLoadError("행사 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    if (parsed === fairId) {
      setReloadTick((tick) => tick + 1);
    } else {
      setFairId(parsed);
    }
  }

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader
        eyebrow="박람회 관리자"
        title="예약 현황"
        description="운영일마다 예약 상태별 건수와 QR 발급 현황을 확인해요. 예약 상태가 바뀌면 화면이 실시간으로 갱신돼요."
      />

      <form onSubmit={handleLoadFair} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label htmlFor="reservation-fair-id" className="mb-1.5 block text-sm font-bold text-ink">관리할 행사 ID</label>
          <Input id="reservation-fair-id" type="number" min={1} value={fairIdInput} onChange={(event) => setFairIdInput(event.target.value)} placeholder="예: 1" />
        </div>
        <Button type="submit" variant="outline"><Search size={16} />불러오기</Button>
        {fairId !== null && (
          <span className={`inline-flex min-h-11 items-center gap-1.5 rounded-button px-3 text-xs font-bold ${liveConnected ? "bg-leaf-soft text-ink" : "bg-page text-muted"}`}>
            <Radio size={13} className={liveConnected ? "animate-pulse" : undefined} />
            {liveConnected ? "실시간 연동 중" : "실시간 연결 대기 중"}
          </span>
        )}
      </form>

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="행사 ID를 먼저 입력해 주세요." description="담당 행사의 ID를 입력하고 불러오기를 누르면 운영일별 예약 현황이 표시돼요." />
      )}

      {fairId !== null && loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">예약 현황을 불러오는 중이에요...</div>
      )}

      {fairId !== null && !loading && summary.length === 0 && !loadError && (
        <EmptyState title="등록된 운영일이 없어요." description="운영일이 먼저 등록돼야 예약 현황을 볼 수 있어요." />
      )}

      {fairId !== null && !loading && summary.length > 0 && (
        <>
          <Table>
            <thead>
              <tr className="border-b border-line text-xs font-bold text-muted">
                <th className="px-4 py-3">운영일</th>
                <th className="px-4 py-3">입장 가능 시간</th>
                <th className="px-4 py-3">정원 / 잔여</th>
                <th className="px-4 py-3">전체</th>
                <th className="px-4 py-3">확정</th>
                <th className="px-4 py-3">입장완료</th>
                <th className="px-4 py-3">결제대기</th>
                <th className="px-4 py-3">취소</th>
                <th className="px-4 py-3">만료</th>
                <th className="px-4 py-3">입장률</th>
              </tr>
            </thead>
            <tbody>
              {summary.map((row) => (
                <tr key={row.fairDateId} className="border-b border-line last:border-0">
                  <td className="px-4 py-3 font-bold">{row.operationDate}</td>
                  <td className="px-4 py-3 text-muted">{formatTime(row.entryStartTime)} ~ {formatTime(row.entryEndTime)}</td>
                  <td className="px-4 py-3 text-muted">
                    <span className={row.remainingCapacity < 0 ? "font-bold text-primary-strong" : undefined}>
                      {row.capacity} / {row.remainingCapacity}
                    </span>
                  </td>
                  <td className="px-4 py-3 tabular-nums">{row.totalCount}</td>
                  <td className="px-4 py-3 tabular-nums">{row.confirmedCount}</td>
                  <td className="px-4 py-3 tabular-nums font-bold text-ink">{row.checkedInCount}</td>
                  <td className="px-4 py-3 tabular-nums text-muted">{row.pendingCount}</td>
                  <td className="px-4 py-3 tabular-nums text-muted">{row.canceledCount}</td>
                  <td className="px-4 py-3 tabular-nums text-muted">{row.expiredCount}</td>
                  <td className="px-4 py-3 tabular-nums font-bold text-ink">{entryRate(row)}</td>
                </tr>
              ))}
            </tbody>
          </Table>

          <h2 className="mb-3 mt-8 text-lg font-extrabold text-ink">QR 발급 현황</h2>
          <Table>
            <thead>
              <tr className="border-b border-line text-xs font-bold text-muted">
                <th className="px-4 py-3">운영일</th>
                <th className="px-4 py-3">발급</th>
                <th className="px-4 py-3">유효</th>
                <th className="px-4 py-3">폐기</th>
              </tr>
            </thead>
            <tbody>
              {qrSummary.map((row) => (
                <tr key={row.fairDateId} className="border-b border-line last:border-0">
                  <td className="px-4 py-3 font-bold">{row.operationDate}</td>
                  <td className="px-4 py-3 tabular-nums">{row.qrIssuedCount}</td>
                  <td className="px-4 py-3 tabular-nums text-ink">{row.qrActiveCount}</td>
                  <td className="px-4 py-3 tabular-nums text-muted">{row.qrRevokedCount}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </>
      )}
    </div>
  );
}
