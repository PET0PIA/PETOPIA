import { AlertCircle, ChevronLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getMyApplicationDetail, type FairApplicationDetail } from "../../api/fair";

const statusLabels: Record<string, string> = {
  RECEIVED: "심사 대기",
  REJECTED: "반려됨",
  EXPIRED: "만료됨",
  PAYMENT_PENDING: "개설비 결제 대기",
  PREPARING: "준비 중",
  IN_PROGRESS: "진행 중",
  ENDED: "종료",
};
const statusTones: Record<string, "primary" | "sun" | "leaf" | "neutral"> = {
  RECEIVED: "sun",
  REJECTED: "neutral",
  EXPIRED: "neutral",
  PAYMENT_PENDING: "primary",
  PREPARING: "leaf",
  IN_PROGRESS: "leaf",
  ENDED: "neutral",
};

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

function formatDate(value: string | null) {
  return value ?? "-";
}

function BackLink() {
  return (
    <Link
      to="/fair-applications/me"
      className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink"
    >
      <ChevronLeft size={16} />
      내 신청 현황
    </Link>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold text-muted">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

export function MyFairApplicationDetailPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const id = Number(fairId);
  const idValid = Number.isInteger(id) && id > 0;

  const [detail, setDetail] = useState<FairApplicationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!idValid) return;
    let alive = true;
    getMyApplicationDetail(id)
      .then((res) => {
        if (alive) setDetail(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "신청서를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [id, idValid]);

  if (!idValid) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="잘못된 신청서 주소예요."
            description="신청서 주소가 올바르지 않아요. 목록에서 다시 선택해 주세요."
            actionTo="/fair-applications/me"
            actionLabel="내 신청 현황으로"
          />
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <p className="py-16 text-center text-sm text-muted">신청서를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (error || !detail) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="신청서를 찾을 수 없어요."
            description={error ?? "목록에서 신청서를 다시 선택해 주세요."}
            actionTo="/fair-applications/me"
            actionLabel="내 신청 현황으로"
          />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <BackLink />

      <div className="mt-4 mb-6">
        <div className="mb-2 flex items-center gap-2">
          <Badge tone={statusTones[detail.status] ?? "neutral"}>
            {statusLabels[detail.status] ?? detail.status}
          </Badge>
        </div>
        <h1 className="text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">{detail.name}</h1>
      </div>

      {detail.status === "REJECTED" && detail.rejectReason && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>반려 사유: {detail.rejectReason}</p>
        </div>
      )}

      {detail.status === "PAYMENT_PENDING" && detail.paymentDueAt && (
        <div className="surface mb-6 p-4 text-sm text-ink">
          개설비 결제 기한: {formatDateTime(detail.paymentDueAt)}까지 결제하지 않으면 신청이 만료돼요.
        </div>
      )}

      <div className="space-y-6">
        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">기본 정보</h3>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Field label="카테고리" value={detail.category ?? "-"} />
            <Field label="행사 소개" value={detail.description ?? "-"} />
            <Field label="유의사항" value={detail.noticeText ?? "-"} />
          </dl>
        </Card>

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">장소</h3>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Field label="장소명" value={detail.placeName ?? "-"} />
            <Field
              label="실내/실외"
              value={detail.indoorOutdoor === "INDOOR" ? "실내" : detail.indoorOutdoor === "OUTDOOR" ? "실외" : "-"}
            />
            <Field label="주소" value={detail.address ?? "-"} />
          </dl>
        </Card>

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">일정</h3>
          <dl className="grid gap-4 sm:grid-cols-3">
            <Field
              label="참가업체 모집"
              value={`${formatDate(detail.vendorRecruitStartDate)} ~ ${formatDate(detail.vendorRecruitEndDate)}`}
            />
            <Field
              label="사전예약"
              value={`${formatDate(detail.reservationStartDate)} ~ ${formatDate(detail.reservationEndDate)}`}
            />
            <Field
              label="행사 운영"
              value={`${formatDate(detail.operationStartDate)} ~ ${formatDate(detail.operationEndDate)}`}
            />
          </dl>
        </Card>

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">예약 정책</h3>
          <dl className="grid gap-4 sm:grid-cols-3">
            <Field
              label="예약금"
              value={detail.reservationFee !== null ? `${detail.reservationFee.toLocaleString("ko-KR")}원` : "-"}
            />
            <Field
              label="취소 가능 기한"
              value={detail.reservationCancelDeadlineHours !== null ? `${detail.reservationCancelDeadlineHours}시간` : "-"}
            />
            <Field
              label="변경 가능 기한"
              value={detail.reservationChangeDeadlineHours !== null ? `${detail.reservationChangeDeadlineHours}시간` : "-"}
            />
          </dl>
        </Card>

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">담당자 정보</h3>
          <dl className="grid gap-4 sm:grid-cols-3">
            <Field label="이름" value={detail.managerName} />
            <Field label="연락처" value={detail.managerPhone ?? "-"} />
            <Field label="이메일" value={detail.managerEmail} />
          </dl>
        </Card>
      </div>
    </div>
  );
}
