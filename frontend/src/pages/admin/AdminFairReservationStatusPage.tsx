import { ArrowLeft } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { ReservationStatusSection } from "../../components/fair-admin/ReservationStatusSection";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";

/**
 * 최고관리자 콘솔에서 특정 행사의 실시간 예약 현황을 본다. fair-admin 콘솔
 * (ReservationStatusPage)과 같은 데이터·SSE를 쓰지만, 콘솔을 벗어나지 않도록
 * FairSelectorContext 대신 URL 파라미터로 fairId를 받는다(VisitStatisticsPage의
 * /admin/dashboard/fairs/:fairId 드릴다운과 같은 패턴).
 */
export function AdminFairReservationStatusPage() {
  const { fairId: fairIdParam } = useParams<{ fairId: string }>();
  const parsed = Number(fairIdParam);
  const fairId = Number.isInteger(parsed) && parsed > 0 ? parsed : null;

  return (
    <div className="mx-auto max-w-6xl py-2">
      <Link to="/admin/dashboard" className="mb-4 inline-flex items-center gap-1 text-sm font-bold text-muted hover:text-primary-strong">
        <ArrowLeft size={16} />
        전체 운영 대시보드로
      </Link>

      <PageHeader
        eyebrow="전체 운영"
        title="예약 현황"
        description="운영일마다 예약 상태별 건수와 QR 발급 현황을 확인해요. 예약 상태가 바뀌면 화면이 실시간으로 갱신돼요."
      />

      {fairId === null ? (
        <EmptyState title="잘못된 행사 경로예요." description="전체 운영 대시보드에서 다시 시도해 주세요." />
      ) : (
        <ReservationStatusSection fairId={fairId} />
      )}
    </div>
  );
}
