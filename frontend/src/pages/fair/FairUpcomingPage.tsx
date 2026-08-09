import { FairPublicListPage } from "./FairPublicListPage";

/** "예정 행사" - 아직 끝나지 않았거나(또는 일정 미정) 앞으로 열릴 공개 행사 목록. */
export function FairUpcomingPage() {
  return (
    <FairPublicListPage
      filter="UPCOMING"
      eyebrow="행사"
      title="예정 행사"
      description="곧 열리거나 진행 중인 행사를 둘러보고 티켓 예매까지 이어가 보세요."
      emptyTitle="예정된 행사가 아직 없어요."
      emptyDescription="새 행사가 공개되면 이곳에서 확인할 수 있어요."
      linkTo={(fairId) => `/tickets/${fairId}`}
    />
  );
}
