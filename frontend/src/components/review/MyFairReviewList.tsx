import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ImageOff, Pencil, Star, Trash2 } from "lucide-react";
import { Button } from "../ui/Button";
import { Card } from "../ui/Card";
import { useConfirm } from "../ui/useConfirm";
import { ApiError } from "../../api/client";
import { deleteFairReview, getMyFairReviews, type MyFairReviewItem } from "../../api/fairReviewActions";
import { FairReviewForm } from "./FairReviewForm";

const PAGE_SIZE = 10;

// "2026-08-14T10:30:00" → "2026.08.14"
function formatReviewDate(iso: string): string {
  return /^\d{4}-\d{2}-\d{2}/.test(iso) ? iso.slice(0, 10).replace(/-/g, ".") : iso;
}

function StarRating({ rating }: { rating: number }) {
  return (
    <span className="inline-flex gap-0.5" aria-label={`별점 ${rating}점`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Star key={n} size={14} fill={n <= rating ? "currentColor" : "none"} className={n <= rating ? "text-sun" : "text-muted"} aria-hidden="true" />
      ))}
    </span>
  );
}

/**
 * 마이페이지 "내 리뷰" 목록. 독립 모듈이라 아직 마이페이지 화면에 연결돼 있지 않다 - 실제로
 * 붙이는 작업은 mypage 도메인 담당자와 협의해 별도로 진행한다(petopia-review-feature-plan
 * 스킬 참고). 수정은 FairReviewForm(reviewId 전달)을 그대로 재사용하고, 삭제는 기존
 * DELETE /api/fairs/{fairId}/reviews/{reviewId}(B.4)를 호출한다.
 */
export function MyFairReviewList() {
  const [items, setItems] = useState<MyFairReviewItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingReviewId, setEditingReviewId] = useState<number | null>(null);
  const [deletingReviewId, setDeletingReviewId] = useState<number | null>(null);
  const { confirm, confirmDialog } = useConfirm();

  useEffect(() => {
    let alive = true;
    getMyFairReviews(0, PAGE_SIZE)
      .then((res) => {
        if (!alive) return;
        setItems(res.items);
        setHasNext(res.hasNext);
        setPage(0);
      })
      .catch((err) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "내 리뷰를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  function loadMore() {
    const nextPage = page + 1;
    setLoadingMore(true);
    getMyFairReviews(nextPage, PAGE_SIZE)
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

  async function handleDelete(review: MyFairReviewItem) {
    if (!(await confirm({ title: "리뷰를 삭제할까요?", description: "삭제하면 되돌릴 수 없어요.", confirmLabel: "삭제" }))) return;
    setDeletingReviewId(review.reviewId);
    try {
      await deleteFairReview(review.fairId, review.reviewId);
      setItems((prev) => prev.filter((item) => item.reviewId !== review.reviewId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "리뷰를 삭제하지 못했어요.");
    } finally {
      setDeletingReviewId(null);
    }
  }

  if (loading) {
    return <div className="h-40 animate-pulse rounded-card bg-surface-alt" aria-hidden="true" />;
  }
  if (error && items.length === 0) {
    return <p className="text-sm text-muted">{error}</p>;
  }
  if (items.length === 0) {
    return <p className="py-8 text-center text-sm text-muted">아직 작성한 리뷰가 없어요.</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      {items.map((review) =>
        editingReviewId === review.reviewId ? (
          <Card key={review.reviewId} className="p-4">
            <FairReviewForm
              fairId={review.fairId}
              reviewId={review.reviewId}
              initialRating={review.rating}
              initialContent={review.content}
              submitLabel="수정 완료"
              onCancel={() => setEditingReviewId(null)}
              onSuccess={(result) => {
                setItems((prev) =>
                  prev.map((item) =>
                    item.reviewId === review.reviewId
                      ? { ...item, rating: result.rating, content: result.content, updatedAt: result.updatedAt }
                      : item,
                  ),
                );
                setEditingReviewId(null);
              }}
            />
          </Card>
        ) : (
          <Card key={review.reviewId} className="flex gap-3 p-4">
            <Link to={`/fairs/${review.fairId}`} className="shrink-0">
              {review.fairPosterImageUrl ? (
                <img src={review.fairPosterImageUrl} alt="" className="size-16 rounded-lg object-cover" />
              ) : (
                <div className="grid size-16 place-items-center rounded-lg bg-surface-alt text-muted">
                  <ImageOff size={20} aria-hidden="true" />
                </div>
              )}
            </Link>
            <div className="min-w-0 flex-1">
              <div className="flex items-start justify-between gap-2">
                <Link to={`/fairs/${review.fairId}`} className="truncate text-sm font-bold hover:underline">
                  {review.fairName}
                </Link>
                <span className="shrink-0 text-xs text-muted">{formatReviewDate(review.createdAt)}</span>
              </div>
              <div className="mt-1">
                <StarRating rating={review.rating} />
              </div>
              <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-ink">{review.content}</p>
              <div className="mt-3 flex gap-3">
                <button
                  type="button"
                  onClick={() => setEditingReviewId(review.reviewId)}
                  className="inline-flex items-center gap-1 text-xs font-bold text-muted hover:text-ink"
                >
                  <Pencil size={13} aria-hidden="true" />
                  수정
                </button>
                <button
                  type="button"
                  onClick={() => handleDelete(review)}
                  disabled={deletingReviewId === review.reviewId}
                  className="inline-flex items-center gap-1 text-xs font-bold text-muted hover:text-primary-strong disabled:opacity-50"
                >
                  <Trash2 size={13} aria-hidden="true" />
                  {deletingReviewId === review.reviewId ? "삭제 중…" : "삭제"}
                </button>
              </div>
            </div>
          </Card>
        ),
      )}

      {error && <p className="text-sm font-bold text-primary-strong">{error}</p>}

      {hasNext && (
        <Button variant="outline" onClick={loadMore} disabled={loadingMore} className="w-full">
          {loadingMore ? "불러오는 중…" : "더 보기"}
        </Button>
      )}

      {confirmDialog}
    </div>
  );
}
