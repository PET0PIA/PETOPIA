import { ChevronRight, ImageOff } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Badge } from "../../components/ui/Badge";
import type { FairPublicListItem } from "../../api/fair";
import { fairCategoryLabels, formatFairPeriodDow } from "./fairCard";

/**
 * 카드 하단 CTA 버튼. to가 있으면 링크(활성 검정), 없으면 비활성(오픈 예정·종료 등).
 *
 * 비활성 버튼을 회색으로 "채우지" 않고 흰 배경 + 테두리로 그리는 이유: 채움색으로 쓰던
 * surface-alt는 홈의 섹션 배경 띠와 같은 색이어서, 그 띠 위에 카드가 놓이면 버튼이 배경에
 * 묻혀 글자만 떠 있는 것처럼 보였다. 테두리로 형태를 잡으면 흰 배경에서도, 회색 띠
 * 위에서도 버튼으로 보인다.
 */
export interface FairCardAction {
  to?: string;
  label: string;
}

interface FairPublicCardProps {
  fair: FairPublicListItem;
  /** 운영이 끝난 행사. 흑백+톤다운. (fair엔 없는 값이라 목록에서 넘겨준다) */
  ended?: boolean;
  /** 포스터·이름 클릭 시 이동 경로(행사 상세 등). 없으면 클릭 불가. 페이지마다 목적지가 달라 밖에서 정한다. */
  to?: string;
  /** 하단 CTA 버튼(예매하기·신청하기·부스 보기). 없으면 버튼을 그리지 않는다. */
  action?: FairCardAction;
  /** 표시 형태. "card"(기본)=세로 포스터 카드, "list"=가로 리스트 행. */
  layout?: "card" | "list";
  /**
   * 포스터 오른쪽 위에 얹을 작은 배지(홈의 "D-12" 같은 것). 왼쪽 위는 카테고리 배지가
   * 이미 쓰고 있어서 겹치지 않게 오른쪽에 둔다. layout="card"에서만 그린다 -
   * 리스트 행의 포스터는 80x96px라 배지를 얹으면 포스터가 가려진다.
   */
  posterBadge?: ReactNode;
}

/** 공개 행사 카드(세로 포스터 + 이름·기간·장소 + 하단 CTA). 목록·참가신청·업체목록에서 공유한다. */
export function FairPublicCard({ fair, ended = false, to, action, layout = "card", posterBadge }: FairPublicCardProps) {
  const poster = fair.posterImageUrl ? (
    <img
      src={fair.posterImageUrl}
      alt={`${fair.name} 포스터`}
      className={`size-full object-cover transition-transform duration-300 ${to ? "group-hover:scale-105" : ""} ${ended ? "grayscale" : ""}`}
    />
  ) : (
    <div className="grid size-full place-items-center text-muted">
      <ImageOff size={28} aria-hidden="true" />
    </div>
  );

  // 세로형 포스터(4:5). 원본 비율이 달라도 잘라서 채운다. 카테고리 배지를 좌상단에 얹는다.
  const posterBox = (
    <div className="relative aspect-[4/5] overflow-hidden rounded-card bg-surface-alt">
      {poster}
      {fair.category && (
        <div className="absolute left-3 top-3">
          <Badge tone="ink">{fairCategoryLabels[fair.category] ?? fair.category}</Badge>
        </div>
      )}
      {posterBadge && <div className="absolute right-3 top-3">{posterBadge}</div>}
    </div>
  );

  // 리스트형: 작은 포스터(가로) + 이름·기간·장소 + 오른쪽 CTA. 한 행에 하나씩 쌓는다.
  if (layout === "list") {
    return (
      <article
        className={`flex items-center gap-4 rounded-card border border-line bg-card p-3 transition-colors hover:border-muted ${ended ? "opacity-60" : ""}`}
      >
        {to ? (
          <Link
            to={to}
            aria-label={`${fair.name} 자세히 보기`}
            className="group relative block h-24 w-20 shrink-0 overflow-hidden rounded-card bg-surface-alt"
          >
            {poster}
          </Link>
        ) : (
          <div className="relative block h-24 w-20 shrink-0 overflow-hidden rounded-card bg-surface-alt">{poster}</div>
        )}

        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex items-center gap-2">
            <h3 className="truncate text-base font-extrabold leading-snug">
              {to ? (
                <Link to={to} className="hover:underline">
                  {fair.name}
                </Link>
              ) : (
                fair.name
              )}
            </h3>
            {fair.category && (
              <Badge tone="ink" className="shrink-0">
                {fairCategoryLabels[fair.category] ?? fair.category}
              </Badge>
            )}
          </div>
          <p className="truncate text-sm text-muted">{formatFairPeriodDow(fair.operationStartDate, fair.operationEndDate)}</p>
          <p className="truncate text-sm text-muted">{fair.placeName ?? "장소 미정"}</p>
        </div>

        {action &&
          (action.to ? (
            <Link
              to={action.to}
              className="inline-flex min-h-11 shrink-0 items-center justify-center gap-1 rounded-pill border border-transparent bg-primary-strong px-4 text-sm font-bold text-white transition hover:opacity-90"
            >
              {action.label}
              <ChevronRight size={16} aria-hidden="true" />
            </Link>
          ) : (
            <span
              className="inline-flex min-h-11 shrink-0 cursor-not-allowed items-center justify-center rounded-pill border border-line bg-card px-4 text-sm font-bold text-muted"
              aria-disabled="true"
            >
              {action.label}
            </span>
          ))}
      </article>
    );
  }

  return (
    <article className={`flex flex-col gap-3 ${ended ? "opacity-60" : ""}`}>
      {to ? (
        <Link to={to} className="group block" aria-label={`${fair.name} 자세히 보기`}>
          {posterBox}
        </Link>
      ) : (
        posterBox
      )}

      <div className="flex flex-1 flex-col gap-1">
        <h3 className="text-base font-extrabold leading-snug">
          {to ? (
            <Link to={to} className="hover:underline">
              {fair.name}
            </Link>
          ) : (
            fair.name
          )}
        </h3>
        <p className="text-sm text-muted">{formatFairPeriodDow(fair.operationStartDate, fair.operationEndDate)}</p>
        <p className="text-sm text-muted">{fair.placeName ?? "장소 미정"}</p>
      </div>

      {/* CTA: to가 있으면 링크 pill(검정), 없으면(오픈 예정·종료) 비활성 pill(회색). 모든 카드가 같은 자리에 둬 높이를 맞춘다. */}
      {action &&
        (action.to ? (
          <Link
            to={action.to}
            className="inline-flex min-h-11 w-full items-center justify-center gap-1 rounded-pill border border-transparent bg-primary-strong px-4 text-sm font-bold text-white transition hover:opacity-90"
          >
            {action.label}
            <ChevronRight size={16} aria-hidden="true" />
          </Link>
        ) : (
          <span
            className="inline-flex min-h-11 w-full cursor-not-allowed items-center justify-center rounded-pill border border-line bg-card px-4 text-sm font-bold text-muted"
            aria-disabled="true"
          >
            {action.label}
          </span>
        ))}
    </article>
  );
}
