import { useEffect, useMemo, useState } from "react";
import { getPublicFairs, type FairPublicListItem } from "../../api/fair";
import { BoothPreviewSection } from "../../components/home/BoothPreviewSection";
import { HeroSection } from "../../components/home/HeroSection";
import { HomeNoticeMarquee } from "../../components/home/HomeNoticeMarquee";
import { HomeStatsStrip } from "../../components/home/HomeStatsStrip";
import { PopupModal } from "../../components/home/PopupModal";
import { QuickMenuSection } from "../../components/home/QuickMenuSection";
import { UpcomingFairSection } from "../../components/home/UpcomingFairSection";
import { PetopiaNewsSection } from "../../components/home/PetopiaNewsSection";
import { isFairInProgress } from "../fair/fairCard";

/**
 * 홈. 히어로 배너·공지 띠·소식은 각자 자기 데이터를 불러오고, 행사 목록만 여기서 한 번
 * 불러 아래 세 곳(숫자 띠·다가오는 행사·참여 부스)이 나눠 쓴다 - 같은 응답을 쓰는데
 * 섹션마다 부르면 홈을 열 때마다 같은 API를 세 번 호출하게 된다.
 *
 * <p>실패해도 화면을 깨지 않는다: 빈 배열로 두면 각 섹션이 "행사 없음" 상태로 알아서
 * 처리한다(안내를 남기거나 스스로 사라진다).
 *
 * <p>섹션 배경은 흰색과 연회색을 번갈아 깔아 스크롤 구간을 나눈다. 배경 띠는 각 섹션이
 * 직접 두르므로(HomeBand), 데이터가 없어 사라지는 섹션은 색칠된 빈 줄도 남기지 않는다.
 */
export function HomePage() {
  const [fairs, setFairs] = useState<FairPublicListItem[] | null>(null);
  // 이미 끝난 행사. 상단 숫자 띠의 "지금까지 열린 행사"에만 쓴다.
  const [pastFairs, setPastFairs] = useState<FairPublicListItem[] | null>(null);

  useEffect(() => {
    let ignore = false;
    // UPCOMING은 아직 끝나지 않은 공개 행사를 서버가 임박한 순으로 내려준다(별도 정렬 불필요).
    getPublicFairs("UPCOMING")
      .then((data) => { if (!ignore) setFairs(data); })
      .catch(() => { if (!ignore) setFairs([]); });
    // 지난 행사는 따로 부른다. 한 번에 묶어서 부르면 둘 중 하나만 실패해도 홈의 행사 섹션
    // 전체가 함께 비어버린다 - 숫자 띠 하나 때문에 본문을 잃을 이유는 없다.
    getPublicFairs("PAST")
      .then((data) => { if (!ignore) setPastFairs(data); })
      .catch(() => {});
    return () => { ignore = true; };
  }, []);

  /*
   * 지금까지 열린 행사 수 = 이미 끝난 행사 + 지금 열려 있는 행사. 둘 다 "개최까지 간" 행사라
   * 한 숫자로 묶는다(예매만 열린 미래 행사는 아직 열린 게 아니라서 뺀다).
   *
   * 공개(published)되고 취소되지 않은 행사만 세는 셈이다 - 목록 API가 그런 것만 내려주기
   * 때문이고, 홈에 보여줄 숫자로는 그게 맞다. 관리자 대시보드의 totalFairs는 심사·준비 중인
   * 행사까지 포함하므로 숫자가 다르다.
   *
   * 지난 행사를 못 불러왔으면 null을 넘겨 띠 자체를 감춘다(0으로 속여 보여주지 않는다).
   */
  const openedFairCount = useMemo(() => {
    if (pastFairs === null || fairs === null) return null;
    const inProgress = fairs.filter((fair) =>
      isFairInProgress(fair.status, fair.operationStartDate, fair.operationEndDate),
    ).length;
    return pastFairs.length + inProgress;
  }, [pastFairs, fairs]);

  /*
   * 참여 부스 섹션에 쓸 대표 행사 하나. 지금 열려 있는 행사가 있으면 그걸 먼저 고르고,
   * 없으면 가장 임박한 행사를 쓴다 - "이번 행사의 참여 부스"라는 제목에 맞는 순서다.
   */
  const featuredFair = useMemo(() => {
    if (!fairs || fairs.length === 0) return null;
    return (
      fairs.find((fair) => isFairInProgress(fair.status, fair.operationStartDate, fair.operationEndDate)) ?? fairs[0]
    );
  }, [fairs]);

  return (
    <>
      <PopupModal />
      <HeroSection />
      <HomeNoticeMarquee />
      <HomeStatsStrip openedFairCount={openedFairCount} />
      <QuickMenuSection />
      <UpcomingFairSection fairs={fairs} />
      <BoothPreviewSection key={featuredFair?.fairId ?? "none"} fair={featuredFair} />
      <PetopiaNewsSection />
    </>
  );
}
