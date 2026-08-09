import { FairPublicListPage } from "./FairPublicListPage";

/** "지난 행사" - 이미 종료된 공개 행사 목록. 예매가 불가능해 카드는 클릭할 수 없다. */
export function FairPastPage() {
  return (
    <FairPublicListPage
      filter="PAST"
      eyebrow="행사"
      title="지난 행사"
      description="이미 종료된 행사를 최근에 끝난 순서로 모아봤어요."
      emptyTitle="종료된 행사가 아직 없어요."
      emptyDescription="행사가 종료되면 이곳에서 모아볼 수 있어요."
    />
  );
}
