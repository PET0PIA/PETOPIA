import { AlertCircle } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { getPayment, type PaymentDetail } from "../../api/payment";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import {
  formatAmount,
  formatDateTime,
  paymentStatusLabel,
  paymentStatusTone,
  paymentTypeLabels,
  refundReasonLabels,
  refundRequestedByDomainLabels,
  refundStatusLabels,
} from "./paymentDisplay";

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold text-muted">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

export function PaymentDetailPage() {
  const [searchParams] = useSearchParams();
  const [detail, setDetail] = useState<PaymentDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 목록 페이지의 "상세" 링크(?id=)로만 들어오는 화면이라, 요청도 그 하나뿐이다. 그래도 id가
  // 바뀌면서 새 요청이 겹칠 수 있어(뒤로가기+다른 상세 링크 등) 요청 순번은 계속 추적한다.
  const latestRequestIdRef = useRef(0);

  const loadPayment = useCallback(async (paymentId: number) => {
    const requestId = ++latestRequestIdRef.current;
    setLoading(true);
    setLoadError(null);
    try {
      const data = await getPayment(paymentId);
      if (latestRequestIdRef.current !== requestId) return;
      setDetail(data);
    } catch (error) {
      if (latestRequestIdRef.current !== requestId) return;
      setDetail(null);
      setLoadError(error instanceof ApiError ? error.message : "결제 정보를 불러오지 못했어요.");
    } finally {
      if (latestRequestIdRef.current === requestId) setLoading(false);
    }
  }, []);

  // 목록 페이지의 "상세" 링크(?id=)로 들어왔을 때 자동으로 조회한다.
  // loadPayment의 setState 호출이 effect 본문에서 "동기적으로" 실행되는 것으로 잡히지 않도록
  // 마이크로태스크로 한 틱 미룬다(react-hooks/set-state-in-effect).
  useEffect(() => {
    const idParam = Number(searchParams.get("id"));
    if (!Number.isInteger(idParam) || idParam <= 0) {
      // id가 없어지거나 잘못된 값이 되면(URL 직접 수정 등) 이전 결제의 detail이 화면에 그대로
      // 남지 않도록 비운다 - 진행 중이던 요청의 응답도 무시하게 요청 순번을 먼저 무효화한다
      // (CodeRabbit 지적, PR #258).
      latestRequestIdRef.current++;
      setDetail(null);
      setLoadError(null);
      setLoading(false);
      return;
    }
    queueMicrotask(() => loadPayment(idParam));
  }, [loadPayment, searchParams]);

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="결제" title="결제 상세 조회" description="결제 목록에서 상세 링크로 들어오면 결제 내역을 확인해요." />

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {!detail && !loading && !loadError && (
        <EmptyState title="결제 ID가 없어요." description="결제 목록에서 '상세' 링크로 들어오면 자동으로 조회돼요." />
      )}

      {loading && <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {detail && !loading && (
        <div className="space-y-6">
          <Card className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-extrabold">{paymentTypeLabels[detail.paymentType] ?? detail.paymentType}</h2>
                <Badge tone={paymentStatusTone(detail.status, detail.refundStatus)}>{paymentStatusLabel(detail.status, detail.refundStatus)}</Badge>
              </div>
            </div>
            <p className="text-2xl font-extrabold text-ink">{formatAmount(detail.amount)}</p>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">결제 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="결제 ID" value={`#${detail.paymentId}`} />
              <Field label="결제 금액" value={formatAmount(detail.amount)} />
              <Field label="결제 수단" value={detail.method} />
              <Field label="결제 요청 시각" value={formatDateTime(detail.createdAt)} />
              <Field label="결제 완료 시각" value={formatDateTime(detail.paidAt)} />
            </dl>

            {detail.refundId !== null && (
              <>
                <h3 className="pt-2 text-sm font-extrabold text-muted">환불 정보</h3>
                <dl className="grid gap-4 sm:grid-cols-3">
                  <Field label="환불 ID" value={`#${detail.refundId}`} />
                  <Field label="환불 상태" value={refundStatusLabels[detail.refundStatus ?? ""] ?? detail.refundStatus ?? "-"} />
                  <Field label="환불 금액" value={detail.refundAmount !== null ? formatAmount(detail.refundAmount) : "-"} />
                  <Field label="환불 사유" value={refundReasonLabels[detail.refundReason ?? ""] ?? detail.refundReason ?? "-"} />
                  <Field label="요청 도메인" value={refundRequestedByDomainLabels[detail.refundRequestedByDomain ?? ""] ?? detail.refundRequestedByDomain ?? "-"} />
                  <Field label="환불 요청 시각" value={formatDateTime(detail.refundRequestedAt)} />
                  <Field label="환불 처리 완료 시각" value={formatDateTime(detail.refundProcessedAt)} />
                </dl>
              </>
            )}
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">연관 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              {/* 행사비(개설비)·참가비는 행사관리자/부스관리자 결제상세와 같은 구성으로 맞췄다
                  (2026-08-23) - 행사ID·행사명처럼 ID+이름은 한 필드에 같이 보여준다. 예약금은
                  별도 스펙이 없어서 기존 필드 그대로 둔다. */}
              {detail.paymentType === "FAIR_OPENING_FEE" && (
                <>
                  <Field label="행사ID · 행사명" value={`#${detail.fairId} · ${detail.fairName ?? "-"}`} />
                  <Field label="행사 담당자" value={detail.fairManagerName ?? "-"} />
                  <Field label="행사 담당자 연락처" value={detail.fairManagerPhone ?? "-"} />
                  <Field label="행사 담당자 이메일" value={detail.fairManagerEmail ?? "-"} />
                </>
              )}
              {detail.paymentType === "VENDOR_FEE" && (
                <>
                  <Field label="행사ID · 행사명" value={`#${detail.fairId} · ${detail.fairName ?? "-"}`} />
                  <Field
                    label="참가ID · 참가명"
                    value={detail.applicationId !== null ? `#${detail.applicationId} · ${detail.businessName ?? "-"}` : "-"}
                  />
                  <Field label="부스신청 담당자" value={detail.applicationManagerName ?? "-"} />
                  <Field label="부스신청 담당자 연락처" value={detail.applicationManagerPhone ?? "-"} />
                  <Field label="부스신청 담당자 이메일" value={detail.applicationManagerEmail ?? "-"} />
                </>
              )}
              {detail.paymentType === "RESERVATION_DEPOSIT" && (
                <>
                  <Field label="행사 이름" value={detail.fairName ?? "-"} />
                  <Field label="결제자 사용자 ID" value={detail.payerUserId !== null ? `#${detail.payerUserId}` : "-"} />
                  <Field label="예약 ID" value={detail.reservationId !== null ? `#${detail.reservationId}` : "-"} />
                </>
              )}
            </dl>
          </Card>
        </div>
      )}
    </div>
  );
}
