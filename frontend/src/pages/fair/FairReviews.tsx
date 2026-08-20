import { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { getFairReviewList, getFairReviewSummary, type FairReviewList, type FairReviewListItem, type FairReviewSummary } from "../../api/fairReviewList";
import { getMyReviewStatus, type CompanionType, type MyReviewStatus, type VisitPurpose } from "../../api/fairReviewSubmission";
import { Button } from "../../components/ui/Button";

const PAGE_SIZE = 10;

const companionTypeLabels: Record<CompanionType, string> = {
  ALONE: "혼자 방문",
  WITH_PET: "반려동물과 함께",
  WITH_FAMILY: "가족과 함께",
  WITH_FRIEND: "친구와 함께",
};

const visitPurposeLabels: Record<VisitPurpose, string> = {
  SHOPPING: "쇼핑",
  EXPERIENCE: "체험",
  INFO: "정보 습득",
  ETC: "기타",
};

// "2026-08-14T10:30:00" → "2026.08.14"
function formatReviewDate(iso: string): string {
  return /^\d{4}-\d{2}-\d{2}/.test(iso) ? iso.slice(0, 10).replace(/-/g, ".") : iso;
}

/**
 * 행사 상세의 "리뷰" 섹션. 태그 기반 통합 리뷰(V39)의 공개 목록·요약을 보여준다.
 * 작성은 이 섹션 안에서 하지 않고 전용 마법사 페이지(/fairs/:fairId/reviews/new)로
 * 이동한다 - 별점+텍스트 시절의 인라인 작성 폼·신고·담당자 답글 기능은 새 백엔드에
 * 대응하는 API가 없어 전부 제거했다(petopia-review-feature-plan 스킬 참고).
 *
 * 리뷰가 0개고 비로그인 상태면 섹션 자체를 숨긴다(부가 정보라 빈 섹션으로 페이지를 늘리지
 * 않는다). 로그인 상태면 리뷰가 0개여도 "첫 리뷰를 남겨보세요" 형태로 작성 진입을 보여준다.
 */
export function FairReviews({ fairId }: { fairId: number }) {
  const { status } = useAuth();
  const loggedIn = status === "authenticated";
  const navigate = useNavigate();

  const [summary, setSummary] = useState<FairReviewSummary | null>(null);
  const [items, setItems] = useState<FairReviewListItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [myStatus, setMyStatus] = useState<MyReviewStatus | null>(null);

  useEffect(() => {
    let alive = true;
    const requests: [Promise<FairReviewSummary>, Promise<FairReviewList>, Promise<MyReviewStatus> | null] = [
      getFairReviewSummary(fairId),
      getFairReviewList(fairId, 0, PAGE_SIZE),
      loggedIn ? getMyReviewStatus(fairId) : null,
    ];
    Promise.allSettled(requests).then(([summaryRes, listRes, statusRes]) => {
      if (!alive) return;
      if (summaryRes.status === "fulfilled") setSummary(summaryRes.value);
      if (listRes.status === "fulfilled") {
        setItems(listRes.value.items);
        setHasNext(listRes.value.hasNext);
        setPage(0);
      }
      if (statusRes && statusRes.status === "fulfilled") setMyStatus(statusRes.value);
      setLoading(false);
    });
    return () => {
      alive = false;
    };
  }, [fairId, loggedIn]);

  function loadMore() {
    const nextPage = page + 1;
    setLoadingMore(true);
    getFairReviewList(fairId, nextPage, PAGE_SIZE)
      .then((res) => {
        setItems((prev) => [...prev, ...res.items]);
        setHasNext(res.hasNext);
        setPage(nextPage);
      })
      .catch(() => {
        /* 더 보기 실패는 조용히 무시 - 이미 보이는 목록은 유지 */
      })
      .finally(() => setLoadingMore(false));
  }

  function handleWriteClick() {
    if (!loggedIn) {
      navigate("/login");
      return;
    }
    navigate(`/fairs/${fairId}/reviews/new`);
  }

  const count = summary?.reviewCount ?? 0;
  if (loading) return null;
  if (count === 0 && !loggedIn) return null;

  const writeDisabled = loggedIn && myStatus != null && (myStatus.alreadyReviewed || !myStatus.hasVisited);
  const writeLabel = !loggedIn
    ? "리뷰 작성"
    : myStatus?.alreadyReviewed
      ? "리뷰 작성 완료"
      : myStatus != null && !myStatus.hasVisited
        ? "리뷰 작성 불가"
        : "리뷰 작성";
  const writeTitle = loggedIn && myStatus != null && !myStatus.alreadyReviewed && !myStatus.hasVisited ? "행사를 방문한 기록이 있어야 작성할 수 있어요." : undefined;

  return (
    <section id="reviews" className="mt-10 scroll-mt-24">
      <div className="flex items-baseline justify-between gap-3">
        <div className="flex items-baseline gap-3">
          <h2 className="text-lg font-extrabold">
            리뷰 <span className="text-sm font-normal text-muted">{count}</span>
          </h2>
          {summary != null && summary.reviewCount > 0 && (
            <span className="flex items-center gap-1 text-sm font-bold">
              <Sparkles size={16} className="text-sun" aria-hidden="true" />
              재방문 의향 {Math.round(summary.revisitRate * 100)}%
            </span>
          )}
        </div>
        <Button type="button" variant="outline" onClick={handleWriteClick} disabled={writeDisabled} title={writeTitle}>
          {writeLabel}
        </Button>
      </div>

      {count === 0 ? (
        <p className="mt-4 py-6 text-center text-sm text-muted">아직 등록된 리뷰가 없어요. 첫 리뷰를 남겨보세요!</p>
      ) : (
        <ul className="mt-4 flex flex-col gap-4">
          {items.map((review) => (
            <li key={review.reviewId} className="border-b border-line pb-4 last:border-0">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-bold">{review.nickname}</span>
                <span className="text-xs text-muted">{formatReviewDate(review.createdAt)}</span>
              </div>
              <p className="mt-1 text-xs text-muted">
                {companionTypeLabels[review.companionType]} · {visitPurposeLabels[review.visitPurpose]} · {review.wouldRevisit ? "재방문 의향 있음" : "재방문 의향 없음"}
              </p>
              {review.fairTagLabels.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {review.fairTagLabels.map((label) => (
                    <span key={label} className="rounded-full bg-primary-soft px-2.5 py-1 text-xs font-bold text-primary-strong">
                      {label}
                    </span>
                  ))}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {hasNext && (
        <button
          type="button"
          onClick={loadMore}
          disabled={loadingMore}
          className="mt-4 inline-flex min-h-11 w-full items-center justify-center rounded-button border border-line bg-card text-sm font-bold transition hover:bg-page disabled:opacity-50"
        >
          {loadingMore ? "불러오는 중…" : "리뷰 더 보기"}
        </button>
      )}
    </section>
  );
}
