import { CalendarDays, ImageOff, MapPin } from "lucide-react";
import { Link } from "react-router-dom";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import type { FairPublicListItem } from "../../api/fair";
import { fairCategoryLabels, formatFairPeriod } from "./fairCard";

interface FairPublicCardProps {
  fair: FairPublicListItem;
  /** 클릭 시 이동 경로. 없으면(또는 ended면) 클릭 불가. */
  to?: string;
  /** 운영이 끝난 행사. 흑백+톤다운+"운영 종료" 배지, 클릭 불가. (fair엔 없는 값이라 목록에서 넘겨준다) */
  ended?: boolean;
}

export function FairPublicCard({ fair, to, ended }: FairPublicCardProps) {
  const clickable = !!to && !ended;

  const poster = fair.posterImageUrl ? (
    <img
      src={fair.posterImageUrl}
      alt={`${fair.name} 포스터`}
      className={`size-full object-cover transition-transform duration-300 group-hover:scale-105 ${ended ? "grayscale" : ""}`}
    />
  ) : (
    <div className="grid size-full place-items-center text-muted">
      <ImageOff size={28} aria-hidden="true" />
    </div>
  );

  const body = (
    <>
      <div className="relative h-40 overflow-hidden bg-page">
        {poster}
        {fair.category && (
          <div className="absolute left-4 top-4">
            <Badge tone="primary">{fairCategoryLabels[fair.category] ?? fair.category}</Badge>
          </div>
        )}
      </div>
      <div className="p-5">
        <h3 className="text-lg font-extrabold">{fair.name}</h3>

        {/* 상태 배지 줄: 종료면 "운영 종료"만, 아니면 사전예약중/참가기업 모집중을 있는 대로(둘 다 가능) */}
        {(ended || fair.reservable || fair.recruiting) && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            {ended ? (
              <Badge tone="ink">운영 종료</Badge>
            ) : (
              <>
                {fair.reservable && <Badge tone="leaf">사전예약중</Badge>}
                {fair.recruiting && <Badge tone="sun">참가기업 모집중</Badge>}
              </>
            )}
          </div>
        )}

        <dl className="mt-4 space-y-2 text-sm text-muted">
          <div className="flex gap-2">
            <CalendarDays size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
            <dd>{formatFairPeriod(fair.operationStartDate, fair.operationEndDate)}</dd>
          </div>
          <div className="flex gap-2">
            <MapPin size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
            <dd>{fair.placeName ?? "장소 미정"}</dd>
          </div>
        </dl>
      </div>
    </>
  );

  if (!clickable) {
    return <Card className={`overflow-hidden ${ended ? "opacity-75" : ""}`}>{body}</Card>;
  }
  return (
    <Link to={to} className="surface group block overflow-hidden transition duration-200 hover:-translate-y-1 hover:shadow-md">
      {body}
    </Link>
  );
}
