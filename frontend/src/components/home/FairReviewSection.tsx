import { Star } from "lucide-react";
import { fairReviews } from "../../mocks/home";
import { SectionHeader } from "../common/SectionHeader";

export function FairReviewSection() {
  return (
    <section>
      <SectionHeader title="행사 리뷰" description="다녀온 반려인들의 생생한 후기" centered />
      <div className="grid gap-5 md:grid-cols-3">
        {fairReviews.map((review) => (
          <article key={review.id} className="flex flex-col overflow-hidden rounded-card bg-card ring-1 ring-line">
            <div className="aspect-[4/3] overflow-hidden bg-page">
              <img src={review.image} alt={`${review.fairName} 후기 사진`} className="size-full object-cover" />
            </div>
            <div className="flex flex-1 flex-col p-5">
              <div className="flex items-center justify-between gap-2">
                <p className="truncate text-sm font-extrabold text-ink">{review.author}</p>
                <span className="flex shrink-0 items-center gap-1 text-sm font-extrabold text-ink" aria-label={`별점 ${review.rating.toFixed(1)}점`}>
                  <Star size={14} className="fill-ink text-ink" />
                  {review.rating.toFixed(1)}
                </span>
              </div>
              <h3 className="mt-3 font-extrabold text-ink">{review.title}</h3>
              <p className="mt-2 line-clamp-3 flex-1 text-sm leading-6 text-muted">{review.content}</p>
              <p className="mt-4 border-t border-line pt-3 text-xs font-bold text-muted">{review.fairName}</p>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
