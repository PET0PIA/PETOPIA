import { AlertCircle, ChevronLeft, Paperclip } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useEffect } from "react";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { createVendorFeePayment } from "../../api/payment";
import { useAuth } from "../../contexts/AuthContext";
import { getApplicationDetail, type ApplicationDetail, type ApplicationStatus } from "../../api/application";

const statusLabels: Record<ApplicationStatus, string> = {
  PENDING_REVIEW: "심사 대기",
  PAYMENT_PENDING: "참가비 결제 대기",
  CONFIRMED: "확정",
  REJECTED: "반려됨",
  CANCELED: "취소됨",
};
const statusTones: Record<ApplicationStatus, "primary" | "sun" | "leaf" | "neutral"> = {
  PENDING_REVIEW: "sun",
  PAYMENT_PENDING: "primary",
  CONFIRMED: "leaf",
  REJECTED: "neutral",
  CANCELED: "neutral",
};

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold text-muted">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

function BackLink() {
  return (
    <Link
      to="/participations/me"
      className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink"
    >
      <ChevronLeft size={16} />
      참가 신청 현황
    </Link>
  );
}

export function ApplicationDetailPage() {
  const { applicationId } = useParams<{ applicationId: string }>();
  const id = Number(applicationId);
  const idValid = Number.isInteger(id) && id > 0;

  if (!idValid) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="잘못된 신청서 주소예요."
            description="신청서 주소가 올바르지 않아요. 목록에서 다시 선택해 주세요."
            actionTo="/participations/me"
            actionLabel="참가 신청 현황으로"
          />
        </div>
      </div>
    );
  }

  // id별로 key를 줘서, 다른 신청서 상세로 이동할 때(같은 라우트라 컴포넌트가 재사용됨)
  // 이전 신청서의 상태가 잔류하지 않고 완전히 새로 마운트되게 한다
  // (MyFairApplicationDetailPage와 동일한 이유).
  return <ApplicationDetailContent key={id} id={id} />;
}

function ApplicationDetailContent({ id }: { id: number }) {
  const { user } = useAuth();
  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
  const [paymentReady, setPaymentReady] = useState(false);

  useEffect(() => {
    let alive = true;
    getApplicationDetail(id)
      .then((res) => {
        if (alive) setDetail(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "신청서를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [id]);

  async function handlePay() {
    if (!detail || !user || detail.finalPrice === null) return;
    setPaying(true);
    setPayError(null);
    try {
      // TODO: 결제 생성까지만 처리한다. 토스 결제창 연동(requestPayment ~ /payments/success
      // 착지)은 별도 확인 후 진행 예정 — 지금은 결제 row 준비까지만 확인한다.
      await createVendorFeePayment(
        detail.applicationId,
        { fairId: detail.fairId, businessId: detail.businessId, amount: detail.finalPrice },
        user.userId
      );
      setPaymentReady(true);
    } catch (err) {
      setPayError(err instanceof ApiError ? err.message : "결제를 준비하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setPaying(false);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <p className="py-16 text-center text-sm text-muted">신청서를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (loadError || !detail) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="신청서를 찾을 수 없어요."
            description={loadError ?? "목록에서 신청서를 다시 선택해 주세요."}
            actionTo="/participations/me"
            actionLabel="참가 신청 현황으로"
          />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <BackLink />

      <div className="mt-4 mb-6">
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <Badge tone={statusTones[detail.status]}>{statusLabels[detail.status]}</Badge>
          {detail.cancelRequestStatus === "REQUESTED" && <Badge tone="sun">취소 요청중</Badge>}
        </div>
        <h1 className="text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">참가 신청서</h1>
      </div>

      {detail.status === "REJECTED" && detail.rejectReason && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>반려 사유: {detail.rejectReason}</p>
        </div>
      )}

      <div className="space-y-6">
        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">선택한 부스 슬롯</h3>
          <ul className="space-y-1 text-sm text-ink">
            {detail.slots.map((slot) => (
              <li key={slot.boothSlotsId} className="flex justify-between">
                <span>{slot.slotNumber}</span>
                <span className="text-muted">{slot.priceAtSelection.toLocaleString()}원</span>
              </li>
            ))}
          </ul>
          <div className="flex justify-between border-t border-line pt-3 text-sm font-bold text-ink">
            <span>합계</span>
            <span>{detail.slots.reduce((sum, slot) => sum + slot.priceAtSelection, 0).toLocaleString()}원</span>
          </div>

          {detail.status === "PAYMENT_PENDING" && (
            <div className="border-t border-line pt-4">
              {payError && (
                <div className="mb-3 flex items-start gap-2 text-sm text-primary-strong">
                  <AlertCircle size={16} className="mt-0.5 shrink-0" />
                  <p>{payError}</p>
                </div>
              )}
              {paymentReady ? (
                <p className="text-right text-sm font-bold text-ink">결제가 준비됐어요. (결제창 연동은 곧 추가될 예정이에요)</p>
              ) : (
                <div className="flex justify-end">
                  <Button type="button" onClick={handlePay} disabled={paying}>
                    {paying ? "준비 중..." : "결제하기"}
                  </Button>
                </div>
              )}
            </div>
          )}
        </Card>

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">신청 내용</h3>
          <dl className="space-y-4">
            <Field label="참가 목적" value={detail.purpose} />
            <Field label="판매·전시 품목" value={detail.itemsDesc} />
            {detail.attachmentUrl && (
              <div>
                <dt className="text-xs font-bold text-muted">첨부파일</dt>
                <dd className="mt-1 text-sm text-ink">
                  <a
                    href={detail.attachmentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 font-bold text-primary-strong hover:underline"
                  >
                    <Paperclip size={14} />
                    첨부파일 보기
                  </a>
                </dd>
              </div>
            )}
          </dl>
        </Card>

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">담당자 정보</h3>
          <dl className="grid gap-4 sm:grid-cols-3">
            <Field label="이름" value={detail.managerName} />
            <Field label="연락처" value={detail.managerPhone} />
            <Field label="이메일" value={detail.managerEmail} />
          </dl>
        </Card>

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">진행 이력</h3>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Field label="제출일시" value={formatDateTime(detail.submittedAt)} />
            <Field label="심사일시" value={formatDateTime(detail.reviewedAt)} />
          </dl>
        </Card>
      </div>
    </div>
  );
}