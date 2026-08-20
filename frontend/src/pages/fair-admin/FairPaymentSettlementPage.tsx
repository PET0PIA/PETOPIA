import { AlertCircle, Download } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../api/client";
import { getPayments, type PaymentDetail, type PaymentListResult, type PaymentStatus } from "../../api/payment";
import { downloadSettlementsExcel, getSettlementsByFair, type SettlementResponse, type SettlementStatus } from "../../api/settlement";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { useFairSelector } from "../../contexts/FairSelectorContext";
import { formatAmount, formatDateTime, statusLabels, statusTone } from "../payment/paymentDisplay";

const settlementStatusLabels: Record<SettlementStatus, string> = {
  PENDING: "대기 중",
  CONFIRMED: "확정됨",
  PAID: "지급 완료",
};

const settlementStatusTones: Record<SettlementStatus, "sun" | "leaf" | "primary"> = {
  PENDING: "sun",
  CONFIRMED: "leaf",
  PAID: "primary",
};

function formatRatePercent(rate: number) {
  return `${(rate * 100).toFixed(2)}%`;
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

// 담당 행사의 참가비 결제 현황(참가업체 결제 목록, VENDOR_FEE 고정). 행사 전환 시 자동으로 다시 조회한다.
function VendorPaymentSection({ fairId }: { fairId: number }) {
  const [status, setStatus] = useState<PaymentStatus | "">("");
  const [result, setResult] = useState<PaymentListResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 행사 전환·상태 필터 변경이 겹쳐 일어날 수 있어서, 먼저 시작했지만 나중에 끝난 요청이
  // 최신 화면을 덮어쓰지 않도록 요청 순번을 추적한다.
  const requestIdRef = useRef(0);

  async function load(page: number) {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);
    try {
      const data = await getPayments({ fairId, paymentType: "VENDOR_FEE", status: status || undefined, page });
      if (requestIdRef.current !== requestId) return;
      setResult(data);
    } catch (err) {
      if (requestIdRef.current !== requestId) return;
      setResult(null);
      setError(errorMessage(err, "참가비 결제 현황을 불러오지 못했어요."));
    } finally {
      if (requestIdRef.current === requestId) setLoading(false);
    }
  }

  useEffect(() => {
    void load(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fairId, status]);

  return (
    <section>
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="text-sm text-muted">담당 행사의 참가업체 참가비 결제 현황이에요.</p>
        <div className="w-44">
          <Select aria-label="결제 상태 필터" value={status} onChange={(event) => setStatus(event.target.value as PaymentStatus | "")}>
            <option value="">전체 상태</option>
            <option value="PENDING">결제 대기</option>
            <option value="COMPLETED">결제 완료</option>
            <option value="FAILED">결제 실패</option>
            <option value="CANCELED">결제 취소</option>
            <option value="EXPIRED">만료됨</option>
          </Select>
        </div>
      </div>

      {error && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      {loading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {!loading && !error && result && result.content.length === 0 && (
        <EmptyState title="참가비 결제 내역이 없어요" description="아직 이 행사의 참가업체 결제가 없어요." />
      )}

      {!loading && result && result.content.length > 0 && (
        <div className="space-y-4">
          <Table>
            <thead>
              <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                <th className="px-4 py-3">결제</th>
                <th className="px-4 py-3">업체</th>
                <th className="px-4 py-3">금액</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">요청 시각</th>
              </tr>
            </thead>
            <tbody>
              {result.content.map((row: PaymentDetail) => (
                <tr key={row.paymentId} className="border-b border-line last:border-b-0">
                  <td className="whitespace-nowrap px-4 py-3 text-ink">#{row.paymentId}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">{row.businessId !== null ? `#${row.businessId}` : "-"}</td>
                  <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatAmount(row.amount)}</td>
                  <td className="whitespace-nowrap px-4 py-3">
                    <Badge tone={statusTone[row.status]}>{statusLabels[row.status]}</Badge>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-muted">{formatDateTime(row.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </Table>

          {result.totalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-muted">
              <span>{result.totalElements}건 중 {result.page + 1} / {result.totalPages} 페이지</span>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => load(result.page - 1)} disabled={result.page <= 0}>이전</Button>
                <Button variant="outline" onClick={() => load(result.page + 1)} disabled={result.page + 1 >= result.totalPages}>다음</Button>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

// 담당 행사의 참가업체별 정산 내역(조회 전용 — 계산·확정은 이 화면 스코프 밖).
function SettlementSection({ fairId }: { fairId: number }) {
  const [settlements, setSettlements] = useState<SettlementResponse[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const requestIdRef = useRef(0);

  useEffect(() => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);
    getSettlementsByFair(fairId)
      .then((data) => { if (requestIdRef.current === requestId) setSettlements(data); })
      .catch((err) => {
        if (requestIdRef.current !== requestId) return;
        setSettlements(null);
        setError(errorMessage(err, "정산 내역을 불러오지 못했어요."));
      })
      .finally(() => { if (requestIdRef.current === requestId) setLoading(false); });
  }, [fairId]);

  async function handleExport() {
    setExporting(true);
    setExportError(null);
    try {
      await downloadSettlementsExcel(fairId);
    } catch (err) {
      setExportError(errorMessage(err, "엑셀 파일을 내려받지 못했어요."));
    } finally {
      setExporting(false);
    }
  }

  return (
    <section>
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="text-sm text-muted">담당 행사의 참가업체별 정산 내역이에요.</p>
        <Button variant="outline" onClick={handleExport} disabled={exporting || loading}>
          <Download size={16} />
          {exporting ? "내보내는 중..." : "엑셀로 내보내기"}
        </Button>
      </div>

      {exportError && <p className="mb-4 text-sm text-primary-strong">{exportError}</p>}

      {error && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      {loading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {!loading && !error && settlements && settlements.length === 0 && (
        <EmptyState title="정산 내역이 없어요" description="아직 계산된 정산이 없어요." />
      )}

      {!loading && settlements && settlements.length > 0 && (
        <Table>
          <thead>
            <tr className="border-b border-line bg-page text-xs font-bold text-muted">
              <th className="px-4 py-3">업체</th>
              <th className="px-4 py-3">총 참가비</th>
              <th className="px-4 py-3">환불액</th>
              <th className="px-4 py-3">수수료</th>
              <th className="px-4 py-3">지급액</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3">확정 시각</th>
            </tr>
          </thead>
          <tbody>
            {settlements.map((row) => (
              <tr key={row.settlementId} className="border-b border-line last:border-b-0">
                <td className="whitespace-nowrap px-4 py-3 text-ink">#{row.businessId}</td>
                <td className="whitespace-nowrap px-4 py-3 text-ink">{formatAmount(row.grossAmount)}</td>
                <td className="whitespace-nowrap px-4 py-3 text-ink">{formatAmount(row.refundAmount)}</td>
                <td className="whitespace-nowrap px-4 py-3 text-ink">
                  {formatAmount(row.commissionAmount)} <span className="text-muted">({formatRatePercent(row.commissionRate)})</span>
                </td>
                <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatAmount(row.netAmount)}</td>
                <td className="whitespace-nowrap px-4 py-3">
                  <Badge tone={settlementStatusTones[row.status]}>{settlementStatusLabels[row.status]}</Badge>
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-muted">{row.confirmedAt ? formatDateTime(row.confirmedAt) : "-"}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </section>
  );
}

// EVENT_ADMIN용 담당 행사 참가업체 결제·정산 조회 화면 (이슈 #131). 계산·확정 같은 쓰기 동작은
// 스코프 밖 — 필요해지면 admin/SettlementPage.tsx의 해당 로직을 참고해 추가하면 된다.
export function FairPaymentSettlementPage() {
  const { fairId } = useFairSelector();
  const [tab, setTab] = useState<"payment" | "settlement">("payment");

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="박람회 관리자" title="참가업체 결제·정산" description="담당 행사의 참가비 결제 현황과 정산 내역을 확인해요." />

      {fairId === null ? (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 결제·정산 현황이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      ) : (
        <>
          <div className="mb-6 flex gap-2">
            <Button variant={tab === "payment" ? "primary" : "outline"} onClick={() => setTab("payment")}>참가비 결제 현황</Button>
            <Button variant={tab === "settlement" ? "primary" : "outline"} onClick={() => setTab("settlement")}>정산 내역</Button>
          </div>
          {tab === "payment" ? <VendorPaymentSection fairId={fairId} /> : <SettlementSection fairId={fairId} />}
        </>
      )}
    </div>
  );
}
