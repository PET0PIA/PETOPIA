import { AlertCircle, ChevronLeft, ChevronRight, Search, User } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getMyPayments, getPayments, type PaymentDetail, type PaymentListResult, type PaymentStatus, type PaymentType } from "../../api/payment";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { formatAmount, formatDateTime, paymentTypeLabels, statusLabels, statusTone } from "./paymentDisplay";

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
          <th className="px-4 py-3">결제</th>
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
            <td className="whitespace-nowrap px-4 py-3 text-ink">{paymentTypeLabels[row.paymentType]}</td>
            <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatAmount(row.amount)}</td>
            <td className="whitespace-nowrap px-4 py-3">
              <Badge tone={statusTone[row.status]}>{statusLabels[row.status]}</Badge>
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

// 조건별 결제 목록(관리자용). fairId/businessId/paymentType/status 전부 선택적 필터로 AND 조합된다.
function AdminListSection() {
  const [fairIdInput, setFairIdInput] = useState("");
  const [businessIdInput, setBusinessIdInput] = useState("");
  const [paymentType, setPaymentType] = useState<PaymentType | "">("");
  const [status, setStatus] = useState<PaymentStatus | "">("");
  const [result, setResult] = useState<PaymentListResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load(page: number) {
    setLoading(true);
    setError(null);
    try {
      const data = await getPayments({
        fairId: fairIdInput ? Number(fairIdInput) : undefined,
        businessId: businessIdInput ? Number(businessIdInput) : undefined,
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
          <label htmlFor="al-fair-id" className="mb-1.5 block text-sm font-bold text-ink">행사 ID</label>
          <Input id="al-fair-id" className="input-no-spinner" type="number" min={1} value={fairIdInput} onChange={(event) => setFairIdInput(event.target.value)} placeholder="전체" />
        </div>
        <div>
          <label htmlFor="al-business-id" className="mb-1.5 block text-sm font-bold text-ink">업체 ID</label>
          <Input id="al-business-id" className="input-no-spinner" type="number" min={1} value={businessIdInput} onChange={(event) => setBusinessIdInput(event.target.value)} placeholder="전체" />
        </div>
        <div>
          <label htmlFor="al-type" className="mb-1.5 block text-sm font-bold text-ink">결제 유형</label>
          <Select id="al-type" value={paymentType} onChange={(event) => setPaymentType(event.target.value as PaymentType | "")}>
            <option value="">전체</option>
            <option value="RESERVATION_DEPOSIT">예약 예약금</option>
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

// 로그인 사용자 본인의 결제 내역(마이페이지). 인증 도메인이 아직 없어서 userId를 직접 입력받는다.
function MyPaymentsSection() {
  const [userIdInput, setUserIdInput] = useState("1");
  const [result, setResult] = useState<PaymentListResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load(page: number) {
    const parsed = Number(userIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setError("사용자 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const data = await getMyPayments(parsed, page);
      setResult(data);
    } catch (err) {
      setResult(null);
      setError(errorMessage(err, "내 결제 내역을 불러오지 못했어요."));
    } finally {
      setLoading(false);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    load(0);
  }

  return (
    <section>
      <Card className="mb-6 p-5">
        <form onSubmit={handleSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label htmlFor="mp-user-id" className="mb-1.5 block text-sm font-bold text-ink">사용자 ID (임시 헤더 X-User-Id)</label>
            <Input id="mp-user-id" className="input-no-spinner" type="number" min={1} value={userIdInput} onChange={(event) => setUserIdInput(event.target.value)} placeholder="예: test1" />
          </div>
          <Button type="submit" variant="outline" disabled={loading}>
            <User size={16} />내 결제 조회
          </Button>
        </form>
      </Card>

      {error && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{error}</p>
        </div>
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
  const [tab, setTab] = useState<"admin" | "me">("admin");

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="결제" title="결제 목록" description="조건별 전체 결제 목록과 특정 사용자의 결제 내역을 조회해요." />
      <div className="mb-6 flex gap-2">
        <Button variant={tab === "admin" ? "primary" : "outline"} onClick={() => setTab("admin")}>전체 목록</Button>
        <Button variant={tab === "me" ? "primary" : "outline"} onClick={() => setTab("me")}>내 결제 내역</Button>
      </div>
      {tab === "admin" ? <AdminListSection /> : <MyPaymentsSection />}
    </div>
  );
}
