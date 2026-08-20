import { AlertCircle, CheckCircle2, ChevronLeft, CreditCard, Pencil } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getMyApplicationDetail, type FairApplicationDetail } from "../../api/fair";

// 신청서 수정(재제출)은 RECEIVED(심사 대기)·REJECTED(반려) 상태에서만 가능하다
// (FairService#updateApplication 참고).
const EDITABLE_STATUSES = new Set(["RECEIVED", "REJECTED"]);

const statusLabels: Record<string, string> = {
  RECEIVED: "심사 대기",
  REJECTED: "반려됨",
  EXPIRED: "만료됨",
  PAYMENT_PENDING: "개설비 결제 대기",
  PREPARING: "행사 준비중",
  IN_PROGRESS: "진행 중",
  ENDED: "종료",
  CANCELED: "취소됨",
};
const statusTones: Record<string, "primary" | "sun" | "leaf" | "neutral"> = {
  RECEIVED: "sun",
  REJECTED: "neutral",
  EXPIRED: "neutral",
  PAYMENT_PENDING: "primary",
  PREPARING: "leaf",
  IN_PROGRESS: "leaf",
  ENDED: "neutral",
  CANCELED: "neutral",
};

// "행사 준비중"이 "결제 준비중"으로 오해되기 쉬워서(2026-08-21 사용자 피드백), 개설비 결제가
// 이미 끝난 상태에는 별도로 "결제완료" 표시를 같이 보여준다.
const PAID_STATUSES = new Set(["PREPARING", "IN_PROGRESS"]);

// 취소 승인(FairCancelRequestService#review)은 fairs.canceled_at만 채우고 status는 그대로
// 두므로, 화면에 보여줄 상태는 status 필드가 아니라 canceledAt 유무로 먼저 판단해야 한다
// (그렇지 않으면 취소된 행사가 계속 "진행 중"/"행사 준비중"으로 보인다).
function resolveDisplayStatus(detail: FairApplicationDetail): string {
  return detail.canceledAt ? "CANCELED" : detail.status;
}

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

  // id별로 key를 줘서, 다른 신청서 상세로 이동할 때(같은 라우트라 컴포넌트가 재사용됨)
  // 이전 신청서의 detail/loading/error 상태가 새 요청이 끝날 때까지 잔류하지 않고
  // 완전히 새로 마운트되게 한다.
  return <MyFairApplicationDetailContent key={id} id={id} />;
}

function MyFairApplicationDetailContent({ id }: { id: number }) {
  const [detail, setDetail] = useState<FairApplicationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
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
  }, [id]);

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

  const displayStatus = resolveDisplayStatus(detail);

  return (
    <div className="mx-auto max-w-3xl py-2">
      <BackLink />

      <div className="mt-4 mb-6 flex items-start justify-between gap-4">
        <div>
          <div className="mb-2 flex items-center gap-2">
            <Badge tone={statusTones[displayStatus] ?? "neutral"}>
              {statusLabels[displayStatus] ?? displayStatus}
            </Badge>
            {!detail.canceledAt && PAID_STATUSES.has(detail.status) && (
              <Badge tone="neutral">
                <CheckCircle2 size={12} className="mr-1" />결제완료
              </Badge>
            )}
          </div>
          <h1 className="text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">{detail.name}</h1>
        </div>
        {!detail.canceledAt && EDITABLE_STATUSES.has(detail.status) && (
          <Link
            to={`/fair-applications/me/${detail.fairId}/edit`}
            className="inline-flex min-h-11 shrink-0 items-center justify-center gap-2 rounded-button border border-line bg-card px-4 text-sm font-bold text-ink hover:bg-page"
          >
            <Pencil size={16} />수정하기
          </Link>
        )}
      </div>

      {detail.canceledAt && (
        <div className="surface mb-6 p-4 text-sm text-ink">
          이 행사는 {formatDateTime(detail.canceledAt)}에 취소가 확정됐어요.
        </div>
      )}

      {detail.status === "REJECTED" && detail.rejectReason && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>반려 사유: {detail.rejectReason}</p>
        </div>
      )}

      {!detail.canceledAt && detail.status === "PAYMENT_PENDING" && detail.paymentDueAt && (
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

        {!detail.canceledAt && detail.status === "PAYMENT_PENDING" && (
          <Link
            to={`/payments/fair-opening-fee/${detail.fairId}`}
            className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-button bg-primary-strong px-4 text-sm font-bold text-white transition hover:opacity-90"
          >
            <CreditCard size={16} />결제하러가기
          </Link>
        )}
      </div>
    </div>
  );
}
