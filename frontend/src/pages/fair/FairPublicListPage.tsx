import { CalendarDays, ImageOff, MapPin } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getPublicFairs, type FairPublicListItem, type PublicFairListFilter } from "../../api/fair";

const categoryLabels: Record<string, string> = { DOG: "강아지", CAT: "고양이", ETC: "기타" };

// operationEndDate가 없으면(일정 미정) 그대로, 있으면 "시작 ~ 종료"로 붙인다.
// TicketReservationPage#formatPeriod와 동일한 표기 규칙(연도가 같으면 종료일은 월-일만).
function formatPeriod(start: string | null, end: string | null) {
  if (!start) return "일정 미정";
  if (!end || end === start) return start;
  const sameYear = start.slice(0, 4) === end.slice(0, 4);
  return `${start} ~ ${sameYear ? end.slice(5) : end}`;
}

interface FairPublicListPageProps {
  filter: PublicFairListFilter;
  eyebrow: string;
  title: string;
  description: string;
  emptyTitle: string;
  emptyDescription: string;
  /** 카드를 눌렀을 때 이동할 경로를 만든다. 생략하면(지난 행사) 카드는 클릭할 수 없다. */
  linkTo?: (fairId: number) => string;
}

/** 공개 행사 목록 화면 공통 구현. /fairs/upcoming, /fairs/past, /tickets가 filter/문구만 바꿔 재사용한다. */
export function FairPublicListPage({ filter, eyebrow, title, description, emptyTitle, emptyDescription, linkTo }: FairPublicListPageProps) {
  const [fairs, setFairs] = useState<FairPublicListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getPublicFairs(filter)
      .then((res) => {
        if (alive) setFairs(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "행사 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [filter]);

  return (
    <PageContainer className="py-7 sm:py-10">
      <PageHeader eyebrow={eyebrow} title={title} description={description} />

      {loading ? (
        <p className="py-16 text-center text-sm text-muted">행사 목록을 불러오는 중이에요…</p>
      ) : error ? (
        <EmptyState title="행사 목록을 불러오지 못했어요." description={error} />
      ) : fairs.length === 0 ? (
        <EmptyState title={emptyTitle} description={emptyDescription} />
      ) : (
        <div className="grid gap-5 md:grid-cols-3">
          {fairs.map((fair) => (
            <FairPublicCard key={fair.fairId} fair={fair} to={linkTo?.(fair.fairId)} />
          ))}
        </div>
      )}
    </PageContainer>
  );
}

function FairPublicCard({ fair, to }: { fair: FairPublicListItem; to?: string }) {
  const body = (
    <>
      <div className="relative h-40 overflow-hidden bg-page">
        {fair.posterImageUrl ? (
          <img src={fair.posterImageUrl} alt={`${fair.name} 포스터`} className="size-full object-cover" />
        ) : (
          <div className="grid size-full place-items-center text-muted">
            <ImageOff size={28} aria-hidden="true" />
          </div>
        )}
        {fair.category && (
          <div className="absolute left-4 top-4">
            <Badge tone="primary">{categoryLabels[fair.category] ?? fair.category}</Badge>
          </div>
        )}
      </div>
      <div className="p-5">
        <h3 className="text-lg font-extrabold">{fair.name}</h3>
        <dl className="mt-4 space-y-2 text-sm text-muted">
          <div className="flex gap-2">
            <CalendarDays size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
            <dd>{formatPeriod(fair.operationStartDate, fair.operationEndDate)}</dd>
          </div>
          <div className="flex gap-2">
            <MapPin size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
            <dd>{fair.placeName ?? "장소 미정"}</dd>
          </div>
        </dl>
      </div>
    </>
  );

  if (!to) {
    return <Card className="overflow-hidden">{body}</Card>;
  }
  return (
    <Link to={to} className="surface block overflow-hidden transition hover:bg-page">
      {body}
    </Link>
  );
}
