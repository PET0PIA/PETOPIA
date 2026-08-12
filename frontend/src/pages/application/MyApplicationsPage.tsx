import { ChevronRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Table } from "../../components/ui/Table";
import { ApiError } from "../../api/client";
import { getMyApplications, type ApplicationStatus, type ApplicationSummary } from "../../api/application";

// AI_UI_RULES 색 규칙: 빨강(primary)=사용자가 이어서 해야 할 핵심 행동(참가비 결제),
// 노랑(sun)=대기 중(심사 대기), 초록(leaf)=정상 진행(확정), 회색(neutral)=종료·무효.
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

function formatPrice(finalPrice: number | null) {
  return finalPrice === null ? "-" : `${finalPrice.toLocaleString()}원`;
}

export function MyApplicationsPage() {
  const [applications, setApplications] = useState<ApplicationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getMyApplications()
      .then((res) => {
        if (alive) setApplications(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "신청 현황을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader
        eyebrow="마이페이지"
        title="참가 신청 현황"
        description="내가 신청한 부스의 심사·결제 진행 상황을 확인할 수 있어요."
      />

      {loading ? (
        <p className="py-16 text-center text-sm text-muted">신청 현황을 불러오는 중이에요…</p>
      ) : error ? (
        <EmptyState
          title="신청 현황을 불러오지 못했어요."
          description={error}
          actionTo="/login"
          actionLabel="로그인하러 가기"
        />
      ) : applications.length === 0 ? (
        <EmptyState
          title="아직 신청한 부스가 없어요."
          description="모집 중인 행사에서 참가 신청을 하면 이곳에서 진행 상황을 확인할 수 있어요."
          actionTo="/fairs/upcoming"
          actionLabel="모집 중인 행사 보러 가기"
        />
      ) : (
        <Table>
          <thead>
            <tr className="border-b border-line text-xs font-bold text-muted">
              <th className="px-4 py-3">행사명</th>
              <th className="px-4 py-3">참가비</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3" aria-label="상세" />
            </tr>
          </thead>
          <tbody>
            {applications.map((application) => (
              <tr key={application.applicationId} className="border-b border-line last:border-0 hover:bg-page">
                <td className="px-4 py-3">
                  <Link
                    to={`/participations/me/${application.applicationId}`}
                    className="font-bold text-ink hover:underline"
                  >
                    {application.fairName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-muted">{formatPrice(application.finalPrice)}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <Badge tone={statusTones[application.status]}>
                      {statusLabels[application.status]}
                    </Badge>
                    {application.cancelRequestStatus === "REQUESTED" && (
                      <Badge tone="sun">취소 요청중</Badge>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3 text-right">
                  <Link
                    to={`/participations/me/${application.applicationId}`}
                    aria-label={`${application.fairName} 신청 상세 보기`}
                    className="inline-flex items-center justify-center rounded-button p-1 text-muted hover:text-ink"
                  >
                    <ChevronRight size={16} aria-hidden="true" />
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
}