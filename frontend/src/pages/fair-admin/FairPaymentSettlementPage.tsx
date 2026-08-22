import { AlertCircle, Calculator, Check, ChevronDown, ChevronUp, RefreshCw, RotateCcw } from "lucide-react";
import { Fragment, useEffect, useRef, useState } from "react";
import { ApiError } from "../../api/client";
import { getPayments, type PaymentDetail, type PaymentListResult, type PaymentStatus } from "../../api/payment";
import { getFairRevenueSummary, type FairRevenueSummaryResponse, type SettlementStatus } from "../../api/settlement";
import {
  calculateFairSettlement,
  confirmFairSettlement,
  getFairSettlement,
  recalculateFairSettlement,
  reopenFairSettlement,
  type FairSettlementResponse,
} from "../../api/fairSettlement";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { useConfirm } from "../../components/ui/useConfirm";
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

// 담당 행사의 매출 요약(티켓예매+참가비 합산) - SUPER_ADMIN 정산·수수료율 화면(admin/SettlementPage.tsx)의
// "행사별 매출 요약"과 같은 집계를 담당 행사 하나로 좁힌 버전(2026-08-22, 정산 내역 탭에 같이 보여준다).
function RevenueSummarySection({ fairId }: { fairId: number }) {
  const [summary, setSummary] = useState<FairRevenueSummaryResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const requestIdRef = useRef(0);

  useEffect(() => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);
    getFairRevenueSummary(fairId)
      .then((data) => { if (requestIdRef.current === requestId) setSummary(data); })
      .catch((err) => {
        if (requestIdRef.current !== requestId) return;
        setSummary(null);
        setError(errorMessage(err, "매출 요약을 불러오지 못했어요."));
      })
      .finally(() => { if (requestIdRef.current === requestId) setLoading(false); });
  }, [fairId]);

  if (loading) {
    return <div className="surface mb-6 grid min-h-24 place-items-center text-sm text-muted">불러오는 중이에요...</div>;
  }
  if (error) {
    return (
      <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
        <AlertCircle size={18} className="mt-0.5 shrink-0" />
        <p>{error}</p>
      </div>
    );
  }
  if (!summary) return null;

  return (
    <Card className="mb-6 p-5">
      <h3 className="mb-3 text-sm font-extrabold text-muted">행사 매출 요약</h3>
      <dl className="grid gap-4 sm:grid-cols-3">
        <div>
          <dt className="text-xs font-bold text-muted">티켓예매 총금액</dt>
          <dd className="mt-1 text-sm text-ink">{formatAmount(summary.ticketAmount)}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">참가비용 총금액</dt>
          <dd className="mt-1 text-sm text-ink">{formatAmount(summary.vendorFeeAmount)}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">전체금액</dt>
          <dd className="mt-1 text-sm font-bold text-ink">{formatAmount(summary.grossAmount)}</dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">행사업체금액</dt>
          <dd className="mt-1 text-sm text-ink">
            {formatAmount(summary.businessAmount)}
            <span className="ml-1 text-xs text-muted">({formatRatePercent(1 - summary.commissionRate)})</span>
          </dd>
        </div>
        <div>
          <dt className="text-xs font-bold text-muted">플랫폼금액</dt>
          <dd className="mt-1 text-sm text-ink">
            {formatAmount(summary.platformAmount)}
            <span className="ml-1 text-xs text-muted">({formatRatePercent(summary.commissionRate)})</span>
          </dd>
        </div>
      </dl>
    </Card>
  );
}

// 담당 행사의 최종정산(플랫폼 ↔ 행사, 업체 구분 없음, 2026-08-22 재설계) - 그 행사에 참가한
// 모든 업체의 참가비를 합쳐 행사 하나당 정산 1건만 계산·확정한다("업체가 100개면 확정을
// 100번 하라는 거냐"는 지적으로 단위를 업체별에서 행사별로 바꿈, admin/SettlementPage.tsx의
// "행사비 조회·정산·확정"과 같은 API·같은 패턴). 되돌리기는 SUPER_ADMIN 전용이라
// EVENT_ADMIN이 누르면 백엔드가 거부한다(그대로 에러 메시지로 안내).
function SettlementSection({ fairId }: { fairId: number }) {
  const { confirm, confirmDialog } = useConfirm();
  // undefined = 로딩 전, null = 조회했지만 계산된 정산 없음, 객체 = 정산 있음.
  const [settlement, setSettlement] = useState<FairSettlementResponse | null | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [calcSubmitting, setCalcSubmitting] = useState(false);
  const [actioning, setActioning] = useState(false);
  // "상세" 클릭 시 확정 시각·확정한 관리자를 그 행 바로 아래에 펼쳐 보여준다(admin/SettlementPage.tsx의
  // "행사비 조회·정산·확정" 표와 동일한 상호작용).
  const [detailExpanded, setDetailExpanded] = useState(false);

  const requestIdRef = useRef(0);

  useEffect(() => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);
    setDetailExpanded(false);
    getFairSettlement(fairId)
      .then((data) => { if (requestIdRef.current === requestId) setSettlement(data ?? null); })
      .catch((err) => {
        if (requestIdRef.current !== requestId) return;
        setSettlement(undefined);
        setError(errorMessage(err, "정산 내역을 불러오지 못했어요."));
      })
      .finally(() => { if (requestIdRef.current === requestId) setLoading(false); });
  }, [fairId]);

  async function handleCalculate() {
    const requestId = requestIdRef.current;
    setCalcSubmitting(true);
    setError(null);
    try {
      const created = await calculateFairSettlement(fairId);
      if (requestIdRef.current !== requestId) return;
      setSettlement(created);
    } catch (err) {
      if (requestIdRef.current !== requestId) return;
      setError(errorMessage(err, "정산 계산에 실패했어요."));
    } finally {
      if (requestIdRef.current === requestId) setCalcSubmitting(false);
    }
  }

  async function handleRecalculate() {
    if (!settlement) return;
    const requestId = requestIdRef.current;
    setError(null);
    setActioning(true);
    try {
      const result = await recalculateFairSettlement(settlement.fairSettlementId);
      if (requestIdRef.current !== requestId) return;
      setSettlement(result);
    } catch (err) {
      if (requestIdRef.current !== requestId) return;
      setError(errorMessage(err, "정산 재계산에 실패했어요."));
    } finally {
      setActioning(false);
    }
  }

  async function handleConfirm() {
    if (!settlement) return;
    const requestId = requestIdRef.current;
    const ok = await confirm({
      title: "정산 확정",
      description: `행사 정산(${formatAmount(settlement.netAmount)})을 확정할까요?\n확정 이후에는 금액을 되돌릴 수 없어요.`,
      confirmLabel: "확정",
    });
    if (!ok || requestIdRef.current !== requestId) return;

    setError(null);
    setActioning(true);
    try {
      const result = await confirmFairSettlement(settlement.fairSettlementId);
      if (requestIdRef.current !== requestId) return;
      setSettlement(result);
    } catch (err) {
      if (requestIdRef.current !== requestId) return;
      if (err instanceof ApiError && err.code === "ST005") {
        setError('계산 이후 환불이 반영되지 않았어요. 먼저 "재계산"을 눌러 주세요.');
      } else {
        setError(errorMessage(err, "정산 확정에 실패했어요."));
      }
    } finally {
      setActioning(false);
    }
  }

  async function handleReopen() {
    if (!settlement) return;
    const requestId = requestIdRef.current;
    const ok = await confirm({
      title: "정산 확정 되돌리기",
      description: `행사 정산(${formatAmount(settlement.netAmount)})을 확정 전 상태로 되돌릴까요?\n되돌린 뒤엔 재계산으로 최신 금액을 반영하고 다시 확정해야 해요.`,
      confirmLabel: "되돌리기",
    });
    if (!ok || requestIdRef.current !== requestId) return;

    setError(null);
    setActioning(true);
    try {
      const result = await reopenFairSettlement(settlement.fairSettlementId);
      if (requestIdRef.current !== requestId) return;
      setSettlement(result);
    } catch (err) {
      if (requestIdRef.current !== requestId) return;
      setError(errorMessage(err, "정산 되돌리기에 실패했어요(SUPER_ADMIN만 가능해요)."));
    } finally {
      setActioning(false);
    }
  }

  return (
    <section>
      <RevenueSummarySection fairId={fairId} />

      <p className="mb-4 text-sm text-muted">담당 행사의 최종정산(플랫폼 ↔ 행사)이에요. 참가업체별 지급액이 아니라, 그 행사에 참가한 모든 업체의 참가비를 합친 정산 1건이에요.</p>

      {error && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      {loading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {!loading && (
        <div className="space-y-4">
          {settlement === null && (
            <Card className="p-5">
              <p className="mb-3 text-sm text-muted">아직 계산된 정산이 없어요. 그 행사에 참가한 모든 업체의 완료된 참가비를 합산해서 계산해요.</p>
              <Button type="button" variant="outline" onClick={handleCalculate} disabled={calcSubmitting}>
                <Calculator size={16} />
                {calcSubmitting ? "계산 중..." : "정산 계산"}
              </Button>
            </Card>
          )}

          {settlement && (
            <Table>
              <thead>
                <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                  <th className="px-4 py-3">총 참가비</th>
                  <th className="px-4 py-3">수수료</th>
                  <th className="px-4 py-3">지급액</th>
                  <th className="px-4 py-3">상태</th>
                  <th className="px-4 py-3">동작</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody>
                <Fragment>
                  <tr className="border-b border-line last:border-b-0">
                    {/* "총 참가비"는 환불 차감 전 원본 합계(grossAmount)가 아니라 환불액을 뺀
                        값으로 보여준다 - 환불액은 상세 펼치면 따로 확인 가능. */}
                    <td className="whitespace-nowrap px-4 py-3 text-ink">{formatAmount(settlement.grossAmount - settlement.refundAmount)}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-ink">
                      {formatAmount(settlement.commissionAmount)} <span className="text-muted">({formatRatePercent(settlement.commissionRate)})</span>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatAmount(settlement.netAmount)}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <Badge tone={settlementStatusTones[settlement.status]}>{settlementStatusLabels[settlement.status]}</Badge>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      {settlement.status === "PENDING" ? (
                        <div className="flex gap-2">
                          <Button variant="outline" onClick={handleRecalculate} disabled={actioning}>
                            <RefreshCw size={14} />
                            재계산
                          </Button>
                          <Button onClick={handleConfirm} disabled={actioning}>
                            <Check size={14} />
                            {actioning ? "처리 중..." : "확정"}
                          </Button>
                        </div>
                      ) : settlement.status === "CONFIRMED" ? (
                        <div className="flex flex-col items-start gap-1.5">
                          <span className="text-sm text-muted">
                            {settlement.confirmedAt ? `${formatDateTime(settlement.confirmedAt)} 확정` : "-"}
                          </span>
                          <Button variant="outline" onClick={handleReopen} disabled={actioning}>
                            <RotateCcw size={14} />
                            {actioning ? "처리 중..." : "되돌리기"}
                          </Button>
                        </div>
                      ) : (
                        <span className="text-sm text-muted">
                          {settlement.confirmedAt ? `${formatDateTime(settlement.confirmedAt)} 확정` : "-"}
                        </span>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <button type="button" onClick={() => setDetailExpanded((current) => !current)} className="flex items-center gap-1 text-sm font-bold text-primary-strong hover:underline">
                        {detailExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                        {detailExpanded ? "접기" : "상세"}
                      </button>
                    </td>
                  </tr>
                  {detailExpanded && (
                    <tr className="border-b border-line bg-page last:border-b-0">
                      <td colSpan={6} className="px-4 py-3">
                        <dl className="grid gap-4 sm:grid-cols-3">
                          <div>
                            <dt className="text-xs font-bold text-muted">환불액</dt>
                            <dd className="mt-1 text-sm text-ink">{formatAmount(settlement.refundAmount)}</dd>
                          </div>
                          <div>
                            <dt className="text-xs font-bold text-muted">확정 시각</dt>
                            <dd className="mt-1 text-sm text-ink">{settlement.confirmedAt ? formatDateTime(settlement.confirmedAt) : "-"}</dd>
                          </div>
                          <div>
                            <dt className="text-xs font-bold text-muted">확정한 관리자</dt>
                            <dd className="mt-1 text-sm text-ink">{settlement.confirmedByUserId !== null ? `#${settlement.confirmedByUserId}` : "-"}</dd>
                          </div>
                        </dl>
                      </td>
                    </tr>
                  )}
                </Fragment>
              </tbody>
            </Table>
          )}
        </div>
      )}

      {confirmDialog}
    </section>
  );
}

// EVENT_ADMIN용 담당 행사 참가업체 결제·정산 화면 (이슈 #131). 정산 내역 탭은 2026-08-22에
// 행사별 최종정산(계산·재계산·확정 포함)으로 재설계됨 - admin/SettlementPage.tsx의
// "행사비 조회·정산·확정"과 동일한 API를 그 행사 하나로 좁혀서 쓴다.
export function FairPaymentSettlementPage() {
  const { fairId } = useFairSelector();
  const [tab, setTab] = useState<"payment" | "settlement">("payment");

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="박람회 관리자" title="결제·정산" description="담당 행사의 참가비 결제 현황과 정산 내역을 확인해요." />

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
