import { useEffect, useRef, useState, type ReactNode } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { FairPublicListItem } from "../../api/fair";
import { SectionHeader } from "../common/SectionHeader";
import { Badge } from "../ui/Badge";
// 행사 목록 페이지가 쓰는 카드·버튼 판정을 그대로 재사용한다(홈에만 다른 카드를 새로 만들면
// 같은 행사가 화면마다 다르게 보인다). 파일 위치가 pages/fair인 건 목록 페이지와 함께
// 만들어졌기 때문이고, 여러 화면이 공유하는 부품이라 여기서도 그대로 가져다 쓴다.
import { FairPublicCard } from "../../pages/fair/FairPublicCard";
import { isFairInProgress, reserveCta } from "../../pages/fair/fairCard";
import { daysUntilInSeoul } from "../../utils/date";
import { HomeBand } from "./HomeBand";

/** 홈 캐러셀에 올릴 최대 개수. 나머지는 "행사 전체 보기"로 목록 화면에서 본다. */
const MAX_CARDS = 8;

/**
 * 포스터 오른쪽 위 배지. 이미 열린 행사는 "진행 중", 아직이면 시작까지 남은 날짜를 D-n으로.
 *
 * 진행 중 판정을 날짜 계산보다 먼저 하는 이유: 여러 날 열리는 행사는 시작일이 이미 지나서
 * 남은 날짜가 음수가 되는데, 그래도 지금 열려 있는 행사다. 일정이 미정이거나(날짜 null)
 * 계산이 안 되는 경우엔 배지를 아예 그리지 않는다 - 틀린 숫자보다 없는 게 낫다.
 */
function posterBadge(fair: FairPublicListItem): ReactNode {
  if (isFairInProgress(fair.status, fair.operationStartDate, fair.operationEndDate)) {
    return <Badge tone="leaf">진행 중</Badge>;
  }
  const days = daysUntilInSeoul(fair.operationStartDate);
  if (days === null || days < 0) return null;
  return <Badge tone="ink">{days === 0 ? "오늘 시작" : `D-${days}`}</Badge>;
}

/** 로딩 중 자리표시. 실제 카드와 같은 폭·비율이라 데이터가 도착할 때 화면이 튀지 않는다. */
function CardSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-hidden="true">
      <div className="aspect-[4/5] animate-pulse rounded-card bg-line" />
      <div className="space-y-2">
        <div className="h-4 w-2/3 animate-pulse rounded bg-line" />
        <div className="h-3 w-1/2 animate-pulse rounded bg-line" />
      </div>
      <div className="h-11 w-full animate-pulse rounded-pill bg-line" />
    </div>
  );
}

/**
 * 홈의 "다가오는 행사" 캐러셀. 목록은 HomePage가 한 번 불러서 넘겨준다(숫자 띠·추천 부스와
 * 같은 응답을 쓰기 때문에, 섹션마다 따로 부르면 같은 API를 세 번 호출하게 된다).
 *
 * @param fairs 아직 안 끝난 공개 행사들(임박한 순, 서버 정렬). null이면 로딩 중.
 */
export function UpcomingFairSection({ fairs }: { fairs: FairPublicListItem[] | null }) {
  const scroller = useRef<HTMLDivElement>(null);
  const scroll = (dir: number) => {
    const el = scroller.current;
    if (!el) return;
    const card = el.firstElementChild as HTMLElement | null;
    const step = card ? card.offsetWidth + 20 : 300;
    el.scrollBy({ left: dir * step, behavior: "smooth" });
  };

  const loading = fairs === null;
  const items = (fairs ?? []).slice(0, MAX_CARDS);

  /*
   * 좌우 버튼은 실제로 넘길 카드가 있을 때만 띄운다. 한 화면에 보이는 카드 수가 폭마다
   * 다르므로(모바일 1장·sm 2장·lg 4장) 개수로 어림잡지 않고, 스크롤 영역이 넘치는지를
   * 직접 재서 판단한다. 창 크기가 바뀌면 다시 재고, 카드 수가 바뀌면 관찰을 새로 건다
   * (칸 자체의 폭은 그대로라 내용이 늘어난 것만으로는 ResizeObserver가 울리지 않는다).
   */
  const [scrollable, setScrollable] = useState(false);
  useEffect(() => {
    const el = scroller.current;
    if (!el || typeof ResizeObserver === "undefined") return;
    // 1~2px 차이는 반올림 오차라 버튼을 띄울 이유가 못 된다.
    const observer = new ResizeObserver(() => setScrollable(el.scrollWidth - el.clientWidth > 8));
    observer.observe(el);
    return () => observer.disconnect();
  }, [items.length]);

  return (
    <HomeBand tone="alt">
      <section>
        <SectionHeader
          title="사랑하는 반려동물과 함께할 다음 행사를 찾아보세요"
          linkTo="/fairs/upcoming"
          linkLabel="행사 전체 보기"
          centered
        />

        {/* 등록된 행사가 아직 없을 때. 홈의 핵심 섹션이라 통째로 감추지 않고 안내를 남긴다 -
            섹션이 사라지면 사용자는 "행사가 없다"가 아니라 "화면이 덜 나왔다"로 읽는다. */}
        {!loading && items.length === 0 ? (
          <div className="grid min-h-40 place-items-center rounded-card bg-card px-6 text-center text-sm text-muted ring-1 ring-line">
            아직 공개된 행사가 없어요. 새 행사가 열리면 이곳에서 가장 먼저 보여드릴게요.
          </div>
        ) : (
          <div className="relative">
            {scrollable && (
              <button
                type="button"
                onClick={() => scroll(-1)}
                aria-label="이전 행사"
                className="absolute -left-3 top-[38%] z-10 hidden -translate-y-1/2 rounded-pill border border-line bg-card p-2.5 shadow-sm transition hover:bg-page md:block"
              >
                <ChevronLeft size={20} />
              </button>
            )}

            <div
              ref={scroller}
              className="flex snap-x gap-5 overflow-x-auto scroll-smooth pb-2 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
            >
              {loading
                ? Array.from({ length: 4 }).map((_, i) => (
                    <div key={i} className="w-[85%] shrink-0 sm:w-[calc(50%_-_0.625rem)] lg:w-[calc(25%_-_0.9375rem)]">
                      <CardSkeleton />
                    </div>
                  ))
                : items.map((fair) => (
                    <div key={fair.fairId} className="w-[85%] shrink-0 snap-start sm:w-[calc(50%_-_0.625rem)] lg:w-[calc(25%_-_0.9375rem)]">
                      <FairPublicCard
                        fair={fair}
                        to={`/fairs/${fair.fairId}`}
                        action={reserveCta(fair, false)}
                        posterBadge={posterBadge(fair)}
                      />
                    </div>
                  ))}
            </div>

            {scrollable && (
              <button
                type="button"
                onClick={() => scroll(1)}
                aria-label="다음 행사"
                className="absolute -right-3 top-[38%] z-10 hidden -translate-y-1/2 rounded-pill border border-line bg-card p-2.5 shadow-sm transition hover:bg-page md:block"
              >
                <ChevronRight size={20} />
              </button>
            )}
          </div>
        )}
      </section>
    </HomeBand>
  );
}
