import { AlertCircle } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getPayments, type PaymentDetail, type PaymentListResult, type PaymentStatus } from "../../api/payment";
import { getFairRevenueSummary, type FairRevenueSummaryResponse } from "../../api/settlement";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { useFairSelector } from "../../contexts/FairSelectorContext";
import { formatAmount, formatDateTime, statusLabels, statusTone } from "../payment/paymentDisplay";

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
                <th className="px-4 py-3">참가업체</th>
                <th className="px-4 py-3">결제</th>
                <th className="px-4 py-3">금액</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">요청 시각</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {result.content.map((row: PaymentDetail) => (
                <tr key={row.paymentId} className="border-b border-line last:border-b-0">
                  <td className="whitespace-nowrap px-4 py-3 text-ink">
                    {row.businessId !== null ? (
                      <>
                        {row.businessName ?? "-"} <span className="text-muted">#{row.businessId}</span>
                      </>
                    ) : (
                      "-"
                    )}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">#{row.paymentId}</td>
                  <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatAmount(row.amount)}</td>
                  <td className="whitespace-nowrap px-4 py-3">
                    <Badge tone={statusTone[row.status]}>{statusLabels[row.status]}</Badge>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-muted">{formatDateTime(row.createdAt)}</td>
                  <td className="whitespace-nowrap px-4 py-3">
                    {/* 최고관리자 결제상세(PaymentDetailPage)를 그대로 재사용한다 - 쿼리파라미터
                        방식이라 관리자 전용 로직이 없고, 백엔드도 담당 EVENT_ADMIN의 조회를
                        이미 허용한다(PaymentService.getPayment). fair-admin 라우트로 한 번 더
                        노출만 해준다(2026-08-24). */}
                    <Link to={`/fair-admin/payment-detail?id=${row.paymentId}`} className="text-sm font-bold text-primary-strong hover:underline">
                      상세
                    </Link>
                  </td>
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

// 담당 행사의 예약티켓 예매결제 현황(예약금 결제 목록, RESERVATION_DEPOSIT 고정, 2026-08-24).
// VendorPaymentSection과 같은 구조 - "참가비 결제 현황"과 "정산 내역" 사이에 넣는다.
function ReservationPaymentSection({ fairId }: { fairId: number }) {
  const [status, setStatus] = useState<PaymentStatus | "">("");
  const [result, setResult] = useState<PaymentListResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const requestIdRef = useRef(0);

  async function load(page: number) {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);
    try {
      const data = await getPayments({ fairId, paymentType: "RESERVATION_DEPOSIT", status: status || undefined, page });
      if (requestIdRef.current !== requestId) return;
      setResult(data);
    } catch (err) {
      if (requestIdRef.current !== requestId) return;
      setResult(null);
      setError(errorMessage(err, "예약티켓 예매결제 현황을 불러오지 못했어요."));
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
        <p className="text-sm text-muted">담당 행사의 예약티켓 예매결제 현황이에요.</p>
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
        <EmptyState title="예약티켓 예매결제 내역이 없어요" description="아직 이 행사의 예약금 결제가 없어요." />
      )}

      {!loading && result && result.content.length > 0 && (
        <div className="space-y-4">
          <Table>
            <thead>
              <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                <th className="px-4 py-3">예약</th>
                <th className="px-4 py-3">예약자</th>
                <th className="px-4 py-3">행사</th>
                <th className="px-4 py-3">예약금액</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">결제완료 시각</th>
              </tr>
            </thead>
            <tbody>
              {result.content.map((row: PaymentDetail) => (
                <tr key={row.paymentId} className="border-b border-line last:border-b-0">
                  <td className="whitespace-nowrap px-4 py-3 text-ink">{row.reservationId !== null ? `#${row.reservationId}` : "-"}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">{row.payerNickname ?? "-"}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">{row.fairName ?? "-"} <span className="text-muted">#{row.fairId}</span></td>
                  <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatAmount(row.amount)}</td>
                  <td className="whitespace-nowrap px-4 py-3">
                    <Badge tone={statusTone[row.status]}>{statusLabels[row.status]}</Badge>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-muted">{row.paidAt ? formatDateTime(row.paidAt) : "-"}</td>
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

// 담당 행사의 매출 요약(예약금+참가비 합산) - SUPER_ADMIN 정산·수수료율 화면(admin/SettlementPage.tsx)의
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
    <div className="mb-6">
      <h3 className="mb-3 text-sm font-extrabold text-muted">행사 매출 요약</h3>
      <Table>
        <thead>
          <tr className="border-b border-line bg-page text-xs font-bold text-muted">
            <th className="px-4 py-3">행사</th>
            <th className="px-4 py-3">예약금 총금액</th>
            <th className="px-4 py-3">참가비 총금액</th>
            <th className="px-4 py-3">전체금액</th>
            <th className="px-4 py-3">주최측금액</th>
            <th className="px-4 py-3">플랫폼금액</th>
          </tr>
        </thead>
        <tbody>
          <tr className="border-b border-line last:border-b-0">
            <td className="whitespace-nowrap px-4 py-3 text-ink">{summary.fairName} <span className="text-muted">#{summary.fairId}</span></td>
            <td className="whitespace-nowrap px-4 py-3 text-ink">{formatAmount(summary.ticketAmount)}</td>
            <td className="whitespace-nowrap px-4 py-3 text-ink">{formatAmount(summary.vendorFeeAmount)}</td>
            <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatAmount(summary.grossAmount)}</td>
            <td className="whitespace-nowrap px-4 py-3 text-ink">
              {formatAmount(summary.businessAmount)}
              <span className="ml-1 text-xs text-muted">({formatRatePercent(1 - summary.commissionRate)})</span>
            </td>
            <td className="whitespace-nowrap px-4 py-3 text-ink">
              {formatAmount(summary.platformAmount)}
              <span className="ml-1 text-xs text-muted">({formatRatePercent(summary.commissionRate)})</span>
            </td>
          </tr>
        </tbody>
      </Table>
    </div>
  );
}

// EVENT_ADMIN용 담당 행사 참가업체 결제·정산 화면 (이슈 #131). 정산 내역 탭은 2026-08-22에
// 행사별 매출요약(조회 전용)만 보여주는 걸로 재조정됨 - 재계산·확정은 "최고관리자 업무"로
// 판단해 EVENT_ADMIN 화면에서 빼고, 백엔드도 같이 SUPER_ADMIN 전용으로 좁혔다(SecurityConfig +
// FairSettlementService, admin/SettlementPage.tsx의 "행사비 조회·정산·확정"에서만 처리).
export function FairPaymentSettlementPage() {
  const { fairId } = useFairSelector();
  const [tab, setTab] = useState<"payment" | "reservation" | "settlement">("payment");

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="행사 관리자" title="결제·정산" description="담당 행사의 참가비 결제 현황과 정산 내역을 확인해요." />

      {fairId === null ? (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 결제·정산 현황이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      ) : (
        <>
          <div className="mb-6 flex gap-2">
            <Button variant={tab === "payment" ? "primary" : "outline"} onClick={() => setTab("payment")}>참가비 결제 현황</Button>
            <Button variant={tab === "reservation" ? "primary" : "outline"} onClick={() => setTab("reservation")}>예약티켓 예매결제 현황</Button>
            <Button variant={tab === "settlement" ? "primary" : "outline"} onClick={() => setTab("settlement")}>정산 내역</Button>
          </div>
          {tab === "payment" && <VendorPaymentSection fairId={fairId} />}
          {tab === "reservation" && <ReservationPaymentSection fairId={fairId} />}
          {tab === "settlement" && <RevenueSummarySection fairId={fairId} />}
        </>
      )}
    </div>
  );
}
