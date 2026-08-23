import { AlertCircle, ChevronLeft, ChevronRight, Search } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getPayments, type PaymentDetail, type PaymentListResult, type PaymentStatus, type PaymentType } from "../../api/payment";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { formatAmount, formatDateTime, paymentStatusLabel, paymentStatusTone, paymentTypeLabels } from "./paymentDisplay";

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

function PaymentTable({ result }: { result: PaymentListResult }) {
  if (result.content.length === 0) {
    return <EmptyState title="결제 내역이 없어요" description="조건에 맞는 결제가 아직 없어요." />;
  }
  return (
    <Table>
      <thead>
        <tr className="border-b border-line bg-page text-xs font-bold text-muted">
          <th className="px-4 py-3">결제ID</th>
          <th className="px-4 py-3">행사이름</th>
          <th className="px-4 py-3">참가업체 이름</th>
          <th className="px-4 py-3">유형</th>
          <th className="px-4 py-3">금액</th>
          <th className="px-4 py-3">상태</th>
          <th className="px-4 py-3">요청 시각</th>
          <th className="px-4 py-3" />
        </tr>
      </thead>
      <tbody>
        {result.content.map((row: PaymentDetail) => (
          <tr key={row.paymentId} className="border-b border-line last:border-b-0">
            <td className="whitespace-nowrap px-4 py-3 text-ink">#{row.paymentId}</td>
            <td className="whitespace-nowrap px-4 py-3 text-ink">{row.fairName ?? "-"}</td>
            <td className="whitespace-nowrap px-4 py-3 text-ink">{row.businessName ?? "-"}</td>
            <td className="whitespace-nowrap px-4 py-3 text-ink">{paymentTypeLabels[row.paymentType]}</td>
            <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatAmount(row.amount)}</td>
            <td className="whitespace-nowrap px-4 py-3">
              <Badge tone={paymentStatusTone(row.status, row.refundStatus)}>{paymentStatusLabel(row.status, row.refundStatus)}</Badge>
            </td>
            <td className="whitespace-nowrap px-4 py-3 text-muted">{formatDateTime(row.createdAt)}</td>
            <td className="whitespace-nowrap px-4 py-3">
              <Link to={`/admin/payments?id=${row.paymentId}`} className="text-sm font-bold text-primary-strong hover:underline">상세</Link>
            </td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

function Pagination({ result, onPage }: { result: PaymentListResult; onPage: (page: number) => void }) {
  if (result.totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-between text-sm text-muted">
      <span>{result.totalElements}건 중 {result.page + 1} / {result.totalPages} 페이지</span>
      <div className="flex gap-2">
        <Button variant="outline" onClick={() => onPage(result.page - 1)} disabled={result.page <= 0}>
          <ChevronLeft size={16} />이전
        </Button>
        <Button variant="outline" onClick={() => onPage(result.page + 1)} disabled={result.page + 1 >= result.totalPages}>
          다음<ChevronRight size={16} />
        </Button>
      </div>
    </div>
  );
}

type IdFilterKind = "fairId" | "businessId" | "reservationId";

const idFilterLabels: Record<IdFilterKind, string> = {
  fairId: "행사ID",
  businessId: "사업자ID",
  reservationId: "예약ID",
};

// 조건별 결제 목록(관리자용). fairId/businessId/reservationId/paymentType/status 전부 선택적
// 필터로 AND 조합된다. 행사ID·사업자ID·예약ID는 결제당 의미가 겹치지 않는 배타적 조건이라(한
// 결제는 셋 중 하나만 채워짐) 별도 칸으로 나누지 않고 "ID유형" 드롭다운 + 값 입력 한 쌍으로
// 합쳤다(2026-08-21, 결제유형 드롭다운과 같은 패턴).
function AdminListSection() {
  const [idFilterKind, setIdFilterKind] = useState<IdFilterKind>("fairId");
  const [idInput, setIdInput] = useState("");
  const [paymentType, setPaymentType] = useState<PaymentType | "">("");
  const [status, setStatus] = useState<PaymentStatus | "">("");
  const [result, setResult] = useState<PaymentListResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load(page: number) {
    setLoading(true);
    setError(null);
    try {
      const idValue = idInput ? Number(idInput) : undefined;
      const data = await getPayments({
        fairId: idFilterKind === "fairId" ? idValue : undefined,
        businessId: idFilterKind === "businessId" ? idValue : undefined,
        reservationId: idFilterKind === "reservationId" ? idValue : undefined,
        paymentType: paymentType || undefined,
        status: status || undefined,
        page,
      });
      setResult(data);
    } catch (err) {
      setResult(null);
      setError(errorMessage(err, "결제 목록을 불러오지 못했어요."));
    } finally {
      setLoading(false);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    load(0);
  }

  return (
    <section className="mb-10">
      <form onSubmit={handleSubmit} className="surface mb-6 grid gap-3 p-5 sm:grid-cols-5 sm:items-end">
        <div>
          <label htmlFor="al-id-kind" className="mb-1.5 block text-sm font-bold text-ink">ID유형</label>
          <Select id="al-id-kind" value={idFilterKind} onChange={(event) => setIdFilterKind(event.target.value as IdFilterKind)}>
            {(Object.keys(idFilterLabels) as IdFilterKind[]).map((kind) => (
              <option key={kind} value={kind}>{idFilterLabels[kind]}</option>
            ))}
          </Select>
        </div>
        <div>
          <label htmlFor="al-id-value" className="mb-1.5 block text-sm font-bold text-ink">{idFilterLabels[idFilterKind]}</label>
          <Input id="al-id-value" className="input-no-spinner" type="number" min={1} value={idInput} onChange={(event) => setIdInput(event.target.value)} placeholder="전체" />
        </div>
        <div>
          <label htmlFor="al-type" className="mb-1.5 block text-sm font-bold text-ink">결제 유형</label>
          <Select id="al-type" value={paymentType} onChange={(event) => setPaymentType(event.target.value as PaymentType | "")}>
            <option value="">전체</option>
            <option value="RESERVATION_DEPOSIT">관람객 예약금</option>
            <option value="VENDOR_FEE">참가업체 참가비</option>
            <option value="FAIR_OPENING_FEE">행사 개설비</option>
          </Select>
        </div>
        <div>
          <label htmlFor="al-status" className="mb-1.5 block text-sm font-bold text-ink">상태</label>
          <Select id="al-status" value={status} onChange={(event) => setStatus(event.target.value as PaymentStatus | "")}>
            <option value="">전체</option>
            <option value="PENDING">결제 대기</option>
            <option value="COMPLETED">결제 완료</option>
            <option value="FAILED">결제 실패</option>
            <option value="CANCELED">결제 취소</option>
            <option value="EXPIRED">만료됨</option>
          </Select>
        </div>
        <Button type="submit" variant="outline" disabled={loading}>
          <Search size={16} />조회
        </Button>
      </form>

      {error && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      {!result && !loading && !error && (
        <EmptyState title="조건을 입력하고 조회해 주세요" description="필터는 모두 선택 사항이라, 비워두고 조회하면 전체 결제가 나와요." />
      )}

      {loading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {result && !loading && (
        <>
          <PaymentTable result={result} />
          <Pagination result={result} onPage={load} />
        </>
      )}
    </section>
  );
}

export function PaymentListPage() {
  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="결제" title="결제 목록" description="조건별 전체 결제 목록을 조회해요." />
      <AdminListSection />
    </div>
  );
}
