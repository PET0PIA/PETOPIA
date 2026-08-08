import { AlertCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../api/client";
import {
  getAdminDashboardFairs,
  getAdminDashboardSummary,
  type AdminDashboardSummary,
  type FairOperationStatus,
  type FairSummary,
} from "../../api/adminDashboard";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";

const statusLabels: Record<FairOperationStatus, string> = {
  PREPARING: "준비 중",
  IN_PROGRESS: "진행 중",
  ENDED: "종료",
};

const statusTones: Record<FairOperationStatus, "sun" | "leaf" | "neutral"> = {
  PREPARING: "sun",
  IN_PROGRESS: "leaf",
  ENDED: "neutral",
};

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

export function AdminDashboardPage() {
  const [summary, setSummary] = useState<AdminDashboardSummary | null>(null);
  const [fairs, setFairs] = useState<FairSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setLoadError(null);
    Promise.all([getAdminDashboardSummary(), getAdminDashboardFairs()])
      .then(([summaryData, fairsData]) => {
        if (ignore) return;
        setSummary(summaryData);
        setFairs(fairsData);
      })
      .catch((error) => {
        if (ignore) return;
        setSummary(null);
        setFairs([]);
        setLoadError(errorMessage(error, "운영 현황을 불러오지 못했어요."));
      })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, []);

  const tiles = summary
    ? [
        { label: "전체 행사", value: `${summary.totalFairs}건` },
        { label: "진행 중", value: `${summary.inProgressFairs}건` },
        { label: "준비 중", value: `${summary.preparingFairs}건` },
        { label: "종료", value: `${summary.endedFairs}건` },
        { label: "전체 확정 예약", value: `${summary.totalReservations.toLocaleString("ko-KR")}건` },
        { label: "전체 방문자", value: `${summary.totalVisitors.toLocaleString("ko-KR")}명` },
      ]
    : [];

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader eyebrow="전체 운영" title="전체 운영 대시보드" description="모든 행사의 운영 현황과 예약·방문 지표를 한눈에 확인해요." />

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>
      )}

      {!loading && !loadError && (
        <div className="flex flex-col gap-6">
          {summary && (
            <Card className="p-6">
              <div className="mb-5">
                <h2 className="text-lg font-extrabold text-ink">운영 요약</h2>
                <p className="mt-1 text-sm text-muted">심사·결제 대기 중인 행사는 집계에서 제외돼요.</p>
              </div>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                {tiles.map((tile) => (
                  <div key={tile.label} className="rounded-card bg-page px-4 py-3">
                    <p className="text-xs font-bold text-muted">{tile.label}</p>
                    <p className="mt-1 text-xl font-extrabold tabular-nums text-ink">{tile.value}</p>
                  </div>
                ))}
              </div>
            </Card>
          )}

          <Card className="p-6">
            <div className="mb-5">
              <h2 className="text-lg font-extrabold text-ink">행사별 현황</h2>
              <p className="mt-1 text-sm text-muted">운영 시작일이 최신인 순서예요.</p>
            </div>

            {fairs.length === 0 ? (
              <EmptyState title="표시할 행사가 없어요." description="심사·결제가 완료돼 운영 단계에 들어간 행사가 없어요." />
            ) : (
              <Table>
                <thead>
                  <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                    <th className="px-4 py-3">행사명</th>
                    <th className="px-4 py-3">상태</th>
                    <th className="px-4 py-3">운영 기간</th>
                    <th className="px-4 py-3">확정 예약</th>
                    <th className="px-4 py-3">방문자</th>
                  </tr>
                </thead>
                <tbody>
                  {fairs.map((fair) => (
                    <tr key={fair.fairId} className="border-b border-line last:border-b-0">
                      <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">
                        <Link to={`/admin/dashboard/fairs/${fair.fairId}`} className="hover:text-primary-strong hover:underline">
                          {fair.fairName}
                        </Link>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3">
                        <Badge tone={statusTones[fair.status]}>{statusLabels[fair.status]}</Badge>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-ink">{fair.operationStartDate} ~ {fair.operationEndDate}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-ink">{fair.totalReservations.toLocaleString("ko-KR")}건</td>
                      <td className="whitespace-nowrap px-4 py-3 text-ink">{fair.totalVisitors.toLocaleString("ko-KR")}명</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </Card>
        </div>
      )}
    </div>
  );
}
