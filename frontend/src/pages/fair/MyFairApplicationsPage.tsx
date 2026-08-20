import { ChevronRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Table } from "../../components/ui/Table";
import { ApiError } from "../../api/client";
import { getMyApplications, type FairApplicationSummary } from "../../api/fair";

// AI_UI_RULES 색 규칙: 빨강(primary)=사용자가 이어서 해야 할 핵심 행동(개설비 결제),
// 노랑(sun)=대기 중(심사 대기), 초록(leaf)=정상 진행, 회색(neutral)=종료·무효.
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
// 이미 끝난 상태에는 별도로 "결제완료" 표시를 상태 배지 앞에 같이 보여준다.
const PAID_STATUSES = new Set(["PREPARING", "IN_PROGRESS"]);

// 취소 승인은 status는 그대로 두고 canceledAt만 채우므로(FairService.getMyApplications 참고),
// 목록에 보여줄 상태는 status가 아니라 canceledAt 유무로 먼저 판단해야 한다.
function resolveDisplayStatus(application: FairApplicationSummary): string {
  return application.canceledAt ? "CANCELED" : application.status;
}

function formatPeriod(start: string | null, end: string | null) {
  if (!start) return "-";
  if (!end || end === start) return start;
  return `${start} ~ ${end}`;
}

export function MyFairApplicationsPage() {
  const [applications, setApplications] = useState<FairApplicationSummary[]>([]);
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
        title="내 신청 현황"
        description="내가 신청한 행사의 심사·개설 진행 상황을 확인할 수 있어요."
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
          title="아직 신청한 행사가 없어요."
          description="행사 신청을 하면 이곳에서 심사 진행 상황을 확인할 수 있어요."
          actionTo="/fair-applications/new"
          actionLabel="행사 신청하러 가기"
        />
      ) : (
        <Table>
          <thead>
            <tr className="border-b border-line text-xs font-bold text-muted">
              <th className="px-4 py-3">행사명</th>
              <th className="px-4 py-3">운영 기간</th>
              <th className="px-4 py-3">신청일</th>
              <th className="px-4 py-3">상태</th>
              <th className="px-4 py-3" aria-label="상세" />
            </tr>
          </thead>
          <tbody>
            {applications.map((application) => (
              <tr key={application.fairId} className="border-b border-line last:border-0 hover:bg-page">
                <td className="px-4 py-3">
                  <Link
                    to={`/fair-applications/me/${application.fairId}`}
                    className="font-bold text-ink hover:underline"
                  >
                    {application.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-muted">
                  {formatPeriod(application.operationStartDate, application.operationEndDate)}
                </td>
                <td className="px-4 py-3 text-muted">{application.createdAt.slice(0, 10)}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    {!application.canceledAt && PAID_STATUSES.has(application.status) && (
                      <Badge tone="neutral">결제완료</Badge>
                    )}
                    <Badge tone={statusTones[resolveDisplayStatus(application)] ?? "neutral"}>
                      {statusLabels[resolveDisplayStatus(application)] ?? resolveDisplayStatus(application)}
                    </Badge>
                  </div>
                </td>
                <td className="px-4 py-3 text-right">
                  <Link
                    to={`/fair-applications/me/${application.fairId}`}
                    aria-label={`${application.name} 신청 상세 보기`}
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
