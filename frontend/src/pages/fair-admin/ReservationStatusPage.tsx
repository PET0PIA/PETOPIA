import { ReservationStatusSection } from "../../components/fair-admin/ReservationStatusSection";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { useFairSelector } from "../../contexts/FairSelectorContext";

export function ReservationStatusPage() {
  const { fairId } = useFairSelector();

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader
        eyebrow="박람회 관리자"
        title="예약 현황"
        description="운영일마다 예약 상태별 건수와 QR 발급 현황을 확인해요. 예약 상태가 바뀌면 화면이 실시간으로 갱신돼요."
      />

      {fairId === null ? (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 운영일별 예약 현황이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      ) : (
        <ReservationStatusSection fairId={fairId} />
      )}
    </div>
  );
}
