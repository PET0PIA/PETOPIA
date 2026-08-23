import { ImageIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { ApiError } from "../../api/client";
import { getMyReviews, type MyReviewListItem } from "../../api/myReviews";
import type { CompanionType, VisitPurpose } from "../../api/fairReviewSubmission";

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
 * 마이페이지 - 내 리뷰. 여러 행사에 걸쳐 내가 쓴 태그 기반 통합 리뷰(V39)를 최신순으로
 * 보여준다. 조회 전용이다 - 수정·삭제 API는 없다("행사당 1건만 새로 작성" 정책,
 * petopia-review-feature-plan 스킬 참고). 부적절한 리뷰 삭제는 행사관리자/최고관리자만
 * 할 수 있다.
 */
export function MyReviewsPage() {
  const [items, setItems] = useState<MyReviewListItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    getMyReviews(0, PAGE_SIZE)
      .then((data) => {
        if (ignore) return;
        setItems(data.items);
        setHasNext(data.hasNext);
        setPage(0);
      })
      .catch((err) => {
        if (ignore) return;
        setError(err instanceof ApiError ? err.message : "내 리뷰 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, []);

  function loadMore() {
    const nextPage = page + 1;
    setLoadingMore(true);
    getMyReviews(nextPage, PAGE_SIZE)
      .then((data) => {
        setItems((prev) => [...prev, ...data.items]);
        setHasNext(data.hasNext);
        setPage(nextPage);
      })
      .catch(() => {
        /* 더 보기 실패는 조용히 무시 - 이미 보이는 목록은 유지 */
      })
      .finally(() => setLoadingMore(false));
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="마이페이지" title="내 리뷰" description="내가 남긴 행사 리뷰를 최신순으로 볼 수 있어요." />

      {loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!loading && error && <EmptyState title="목록을 불러올 수 없어요" description={error} />}

      {!loading && !error && items.length === 0 && (
        <EmptyState
          title="아직 남긴 리뷰가 없어요"
          description="방문한 행사 상세 페이지에서 리뷰를 작성할 수 있어요."
          actionTo="/fairs/upcoming"
          actionLabel="행사 목록 보기"
        />
      )}

      {!loading && !error && items.length > 0 && (
        <>
          <ul className="flex flex-col gap-4">
            {items.map((review) => (
              <li key={review.reviewId}>
                <Link
                  to={`/fairs/${review.fairId}#reviews`}
                  className="surface flex gap-3 p-5 hover:opacity-80"
                >
                  <div className="grid size-12 shrink-0 place-items-center overflow-hidden rounded-full bg-primary-soft text-primary-strong">
                    {review.posterImageUrl ? (
                      <img src={review.posterImageUrl} alt="" className="size-full object-cover" />
                    ) : (
                      <ImageIcon size={18} />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <p className="truncate font-bold text-ink">{review.fairName}</p>
                      <span className="shrink-0 text-xs text-muted">{formatReviewDate(review.createdAt)}</span>
                    </div>
                    <p className="mt-1 text-xs text-muted">
                      {companionTypeLabels[review.companionType]} · {visitPurposeLabels[review.visitPurpose]} ·{" "}
                      {review.wouldRevisit ? "재방문 의향 있음" : "재방문 의향 없음"}
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
                  </div>
                </Link>
              </li>
            ))}
          </ul>

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
        </>
      )}
    </PageContainer>
  );
}
