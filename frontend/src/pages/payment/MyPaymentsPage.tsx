import { ChevronLeft, ChevronRight, Receipt } from "lucide-react";
import { useEffect, useState } from "react";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Table } from "../../components/ui/Table";
import { ApiError } from "../../api/client";
import { getMyPayments, type PaymentListResult } from "../../api/payment";
import { useAuth } from "../../contexts/AuthContext";
import { formatAmount, formatDateTime, paymentTypeLabels, statusLabels, statusTone } from "./paymentDisplay";

// 마이페이지 "내 결제 내역". 관리자용 결제 목록(PaymentListPage)과 달리 userId를 직접
// 입력받지 않고, 로그인한 본인의 userId(useAuth)를 그대로 써서 자동 조회한다.
export function MyPaymentsPage() {
  const { user } = useAuth();
  const [result, setResult] = useState<PaymentListResult | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) return;
    let alive = true;
    setLoading(true);
    getMyPayments(user.userId, page)
      .then((res) => {
        if (alive) setResult(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "결제 내역을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [user, page]);

  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader eyebrow="내 결제" title="내 결제 내역" description="예약금·참가비·개설비 등 내가 결제한 내역을 확인할 수 있어요." />

      {loading ? (
        <p className="py-16 text-center text-sm text-muted">결제 내역을 불러오는 중이에요…</p>
      ) : error ? (
        <EmptyState title="결제 내역을 불러오지 못했어요." description={error} />
      ) : !result || result.content.length === 0 ? (
        <EmptyState
          title="아직 결제한 내역이 없어요."
          description="예약·참가·개설비를 결제하면 이곳에서 확인할 수 있어요."
        />
      ) : (
        <>
          <Table>
            <thead>
              <tr className="border-b border-line text-xs font-bold text-muted">
                <th className="px-4 py-3">유형</th>
                <th className="px-4 py-3">결제ID</th>
                <th className="px-4 py-3">금액</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">결제일시</th>
              </tr>
            </thead>
            <tbody>
              {result.content.map((payment) => (
                <tr key={payment.paymentId} className="border-b border-line last:border-0">
                  <td className="px-4 py-3 text-ink">
                    <span className="flex items-center gap-2">
                      <Receipt size={14} className="text-muted" />
                      {paymentTypeLabels[payment.paymentType]}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-muted">#{payment.paymentId}</td>
                  <td className="px-4 py-3 font-bold text-ink">{formatAmount(payment.amount)}</td>
                  <td className="px-4 py-3">
                    <Badge tone={statusTone[payment.status]}>{statusLabels[payment.status]}</Badge>
                  </td>
                  <td className="px-4 py-3 text-muted">{formatDateTime(payment.paidAt ?? payment.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </Table>

          {result.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between text-sm text-muted">
              <span>{result.totalElements}건 중 {result.page + 1} / {result.totalPages} 페이지</span>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => setPage((p) => p - 1)} disabled={result.page <= 0}>
                  <ChevronLeft size={16} />이전
                </Button>
                <Button variant="outline" onClick={() => setPage((p) => p + 1)} disabled={result.page + 1 >= result.totalPages}>
                  다음<ChevronRight size={16} />
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
