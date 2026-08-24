import { AlertCircle, Search } from "lucide-react";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getPayment, getPayments, type PaymentDetail, type PaymentListResult, type PaymentStatus } from "../../api/payment";
import { getFairRevenueSummary, type FairRevenueSummaryResponse } from "../../api/settlement";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Input } from "../../components/ui/Input";
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

/**
 * 참가비 결제 현황·예약티켓 예매결제 현황이 공유하는 검색 폼(2026-08-24). 세 칸(연관ID/결제상태/
 * 결제ID)을 동시에 보여주고 전부 선택 입력이다 - 비워두고 조회하면 그 칸은 필터에서 빠져서
 * 전체 조회가 된다. admin/SettlementPage.tsx의 "행사비 조회" 폼과 레이아웃을 맞췄다.
 */
function PaymentSearchForm({
  idLabel,
  idPlaceholder,
  idValue,
  onIdChange,
  statusValue,
  onStatusChange,
  // 참가비 결제만 가상계좌를 쓸 수 있어서 "입금 대기"는 그쪽 드롭다운에만 넣는다(2026-08-24) -
  // 예약금 결제는 가상계좌 자체가 안 되니(ReservationPaymentMethod 주석 참고) 이 상태가 나올 수 없다.
  includeWaitingForDeposit,
  paymentIdValue,
  onPaymentIdChange,
  loading,
  onSubmit,
}: {
  idLabel: string;
  idPlaceholder: string;
  idValue: string;
  onIdChange: (value: string) => void;
  statusValue: PaymentStatus | "";
  onStatusChange: (value: PaymentStatus | "") => void;
  includeWaitingForDeposit?: boolean;
  paymentIdValue: string;
  onPaymentIdChange: (value: string) => void;
  loading: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form onSubmit={onSubmit} className="surface mb-6 flex flex-col gap-3 sm:flex-row sm:items-end p-5">
      <div className="flex-1">
        <label className="mb-1.5 block text-sm font-bold text-ink">{idLabel}</label>
        <Input
          className="input-no-spinner"
          type="number"
          min={1}
          value={idValue}
          onChange={(event) => onIdChange(event.target.value)}
          placeholder={idPlaceholder}
        />
      </div>
      <div className="flex-1">
        <label className="mb-1.5 block text-sm font-bold text-ink">결제상태</label>
        <Select aria-label="결제 상태" value={statusValue} onChange={(event) => onStatusChange(event.target.value as PaymentStatus | "")}>
          <option value="">전체</option>
          <option value="PENDING">결제 대기</option>
          {includeWaitingForDeposit && <option value="WAITING_FOR_DEPOSIT">입금 대기</option>}
          <option value="COMPLETED">결제 완료</option>
          <option value="FAILED">결제 실패</option>
          <option value="CANCELED">결제 취소</option>
          <option value="EXPIRED">만료됨</option>
        </Select>
      </div>
      <div className="flex-1">
        <label className="mb-1.5 block text-sm font-bold text-ink">결제ID</label>
        <Input
          className="input-no-spinner"
          type="number"
          min={1}
          value={paymentIdValue}
          onChange={(event) => onPaymentIdChange(event.target.value)}
          placeholder="예: 10"
        />
      </div>
      {/* Button 기본 높이(min-h-11=44px)가 Input/Select 높이(h-12=48px)보다 낮아서 나란히 두면
          어긋나 보였다(2026-08-24, admin/SettlementPage.tsx의 "행사비 조회"와 같은 문제) - h-12로 맞춘다. */}
      <Button type="submit" variant="outline" className="h-12" disabled={loading}>
        <Search size={16} />
        조회
      </Button>
    </form>
  );
}

/** 선택 입력 숫자 필드 파싱 - 빈 값이면 필터 없음(null), 잘못된 값이면 "invalid". */
function parseOptionalId(raw: string): number | null | "invalid" {
  if (!raw.trim()) return null;
  const parsed = Number(raw);
  if (!Number.isInteger(parsed) || parsed <= 0) return "invalid";
  return parsed;
}

// 담당 행사의 참가비 결제 현황(참가업체 결제 목록, VENDOR_FEE 고정). "전체상태" 필터 대신
// 참가업체ID/결제상태/결제ID 세 칸을 동시에 두고 필요한 칸만 채워 조회한다(2026-08-24) -
// 전부 비워두면 결제ID만 없다는 뜻이라 businessId/status 없이 그대로 전체 조회로 이어진다.
function VendorPaymentSection({ fairId }: { fairId: number }) {
  const [businessIdInput, setBusinessIdInput] = useState("");
  const [status, setStatus] = useState<PaymentStatus | "">("");
  const [paymentIdInput, setPaymentIdInput] = useState("");
  const [searched, setSearched] = useState(false);
  const [result, setResult] = useState<PaymentListResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 검색이 겹쳐 일어날 수 있어서, 먼저 시작했지만 나중에 끝난 요청이 최신 화면을
  // 덮어쓰지 않도록 요청 순번을 추적한다.
  const requestIdRef = useRef(0);

  async function search(page: number) {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);
    try {
      const paymentIdParsed = parseOptionalId(paymentIdInput);
      if (paymentIdParsed === "invalid") {
        setError("결제 ID는 1 이상의 숫자로 입력해 주세요.");
        setResult(null);
        return;
      }
      // 결제ID가 있으면 그 결제 하나만 단건 조회한다 - 참가업체ID/상태는 결제ID와 같이
      // 좁혀 쓸 이유가 없어서(결제ID 자체가 이미 유일 식별자) 무시한다.
      if (paymentIdParsed !== null) {
        const detail = await getPayment(paymentIdParsed);
        if (requestIdRef.current !== requestId) return;
        const matches = detail.fairId === fairId && detail.paymentType === "VENDOR_FEE";
        setResult({ content: matches ? [detail] : [], page: 0, size: 1, totalElements: matches ? 1 : 0, totalPages: matches ? 1 : 0 });
        return;
      }

      const businessIdParsed = parseOptionalId(businessIdInput);
      if (businessIdParsed === "invalid") {
        setError("참가업체 ID는 1 이상의 숫자로 입력해 주세요.");
        setResult(null);
        return;
      }
      const data = await getPayments({
        fairId,
        paymentType: "VENDOR_FEE",
        businessId: businessIdParsed ?? undefined,
        status: status || undefined,
        page,
      });
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

  // 행사를 바꾸면 이전 행사 기준 검색 결과가 그대로 남아 헷갈리니 초기화한다.
  useEffect(() => {
    setBusinessIdInput("");
    setStatus("");
    setPaymentIdInput("");
    setSearched(false);
    setResult(null);
    setError(null);
  }, [fairId]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearched(true);
    void search(0);
  }

  return (
    <section>
      <p className="mb-4 text-sm text-muted">담당 행사의 참가업체 참가비 결제 현황이에요. 조건을 입력하고 조회해 주세요 - 모두 비워두면 전체가 조회돼요.</p>

      <PaymentSearchForm
        idLabel="참가업체ID"
        idPlaceholder="예: 1"
        idValue={businessIdInput}
        onIdChange={setBusinessIdInput}
        statusValue={status}
        onStatusChange={setStatus}
        includeWaitingForDeposit
        paymentIdValue={paymentIdInput}
        onPaymentIdChange={setPaymentIdInput}
        loading={loading}
        onSubmit={handleSubmit}
      />

      {error && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      {!searched && !loading && !error && (
        <EmptyState title="조회해 주세요" description="참가업체ID, 결제상태, 결제ID로 좁혀 찾을 수 있고, 모두 비워두면 전체 참가비 결제가 조회돼요." />
      )}

      {loading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {searched && !loading && !error && result && result.content.length === 0 && (
        <EmptyState title="검색 결과가 없어요" description="입력한 조건에 맞는 참가비 결제가 없어요." />
      )}

      {!loading && result && result.content.length > 0 && (
        <div className="space-y-4">
          <Table>
            <thead>
              <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                <th className="px-4 py-3">참가업체ID · 참가업체명</th>
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
                    {row.businessId !== null ? `#${row.businessId}·${row.businessName ?? "-"}` : "-"}
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
                <Button variant="outline" onClick={() => search(result.page - 1)} disabled={result.page <= 0}>이전</Button>
                <Button variant="outline" onClick={() => search(result.page + 1)} disabled={result.page + 1 >= result.totalPages}>다음</Button>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

// 담당 행사의 예매결제 현황(예약금 결제 목록, RESERVATION_DEPOSIT 고정, 2026-08-24).
// VendorPaymentSection과 같은 검색 구조(예약ID/결제상태/결제ID) - "참가비 결제 현황"과
// "정산 내역" 사이에 넣는다.
function ReservationPaymentSection({ fairId }: { fairId: number }) {
  const [reservationIdInput, setReservationIdInput] = useState("");
  const [status, setStatus] = useState<PaymentStatus | "">("");
  const [paymentIdInput, setPaymentIdInput] = useState("");
  const [searched, setSearched] = useState(false);
  const [result, setResult] = useState<PaymentListResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const requestIdRef = useRef(0);

  async function search(page: number) {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError(null);
    try {
      const paymentIdParsed = parseOptionalId(paymentIdInput);
      if (paymentIdParsed === "invalid") {
        setError("결제 ID는 1 이상의 숫자로 입력해 주세요.");
        setResult(null);
        return;
      }
      if (paymentIdParsed !== null) {
        const detail = await getPayment(paymentIdParsed);
        if (requestIdRef.current !== requestId) return;
        const matches = detail.fairId === fairId && detail.paymentType === "RESERVATION_DEPOSIT";
        setResult({ content: matches ? [detail] : [], page: 0, size: 1, totalElements: matches ? 1 : 0, totalPages: matches ? 1 : 0 });
        return;
      }

      const reservationIdParsed = parseOptionalId(reservationIdInput);
      if (reservationIdParsed === "invalid") {
        setError("예약 ID는 1 이상의 숫자로 입력해 주세요.");
        setResult(null);
        return;
      }
      const data = await getPayments({
        fairId,
        paymentType: "RESERVATION_DEPOSIT",
        reservationId: reservationIdParsed ?? undefined,
        status: status || undefined,
        page,
      });
      if (requestIdRef.current !== requestId) return;
      setResult(data);
    } catch (err) {
      if (requestIdRef.current !== requestId) return;
      setResult(null);
      setError(errorMessage(err, "예매결제 현황을 불러오지 못했어요."));
    } finally {
      if (requestIdRef.current === requestId) setLoading(false);
    }
  }

  useEffect(() => {
    setReservationIdInput("");
    setStatus("");
    setPaymentIdInput("");
    setSearched(false);
    setResult(null);
    setError(null);
  }, [fairId]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearched(true);
    void search(0);
  }

  return (
    <section>
      <p className="mb-4 text-sm text-muted">담당 행사의 예매결제 현황이에요. 조건을 입력하고 조회해 주세요 - 모두 비워두면 전체가 조회돼요.</p>

      <PaymentSearchForm
        idLabel="예약ID"
        idPlaceholder="예: 1"
        idValue={reservationIdInput}
        onIdChange={setReservationIdInput}
        statusValue={status}
        onStatusChange={setStatus}
        paymentIdValue={paymentIdInput}
        onPaymentIdChange={setPaymentIdInput}
        loading={loading}
        onSubmit={handleSubmit}
      />

      {error && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      {!searched && !loading && !error && (
        <EmptyState title="조회해 주세요" description="예약ID, 결제상태, 결제ID로 좁혀 찾을 수 있고, 모두 비워두면 전체 예매결제가 조회돼요." />
      )}

      {loading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {searched && !loading && !error && result && result.content.length === 0 && (
        <EmptyState title="검색 결과가 없어요" description="입력한 조건에 맞는 예매결제가 없어요." />
      )}

      {!loading && result && result.content.length > 0 && (
        <div className="space-y-4">
          <Table>
            <thead>
              <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                <th className="px-4 py-3">예약ID</th>
                <th className="px-4 py-3">예약자명</th>
                <th className="px-4 py-3">행사</th>
                <th className="px-4 py-3">예약금액</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">결제완료 시각</th>
                <th className="px-4 py-3" />
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
                  <td className="whitespace-nowrap px-4 py-3">
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
                <Button variant="outline" onClick={() => search(result.page - 1)} disabled={result.page <= 0}>이전</Button>
                <Button variant="outline" onClick={() => search(result.page + 1)} disabled={result.page + 1 >= result.totalPages}>다음</Button>
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
            <th className="px-4 py-3">행사ID·행사명</th>
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
            <Button variant={tab === "reservation" ? "primary" : "outline"} onClick={() => setTab("reservation")}>예매결제현황</Button>
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
