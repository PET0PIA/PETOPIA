import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { MyFairReviewList } from "../../components/review/MyFairReviewList";

/** 마이페이지 - 내가 쓴 리뷰 목록. "내 예약 목록"/"내 행사 신청 목록"과 같은 방식으로
 * MyPage.tsx에서는 링크만 연결하고, 실제 데이터는 이 전용 페이지에서 불러온다. */
export function MyReviewsPage() {
  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="마이페이지" title="내 리뷰" description="내가 남긴 행사 리뷰를 확인하고 수정·삭제할 수 있어요." />
      <MyFairReviewList />
    </PageContainer>
  );
}
