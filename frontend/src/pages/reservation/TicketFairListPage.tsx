import { FairPublicListPage } from "../fair/FairPublicListPage";

/** "티켓 예매" 진입 목록. 예매 가능한(=아직 끝나지 않은) 공개 행사 중 하나를 골라
 * TicketReservationPage(/tickets/:fairId)로 들어간다. */
export function TicketFairListPage() {
  return (
    <FairPublicListPage
      filter="UPCOMING"
      eyebrow="티켓 예매"
      title="티켓 예매"
      description="티켓을 예매할 행사를 선택해 주세요."
      emptyTitle="예매 가능한 행사가 아직 없어요."
      emptyDescription="새 행사가 공개되면 이곳에서 예매를 시작할 수 있어요."
      linkTo={(fairId) => `/tickets/${fairId}`}
    />
  );
}
