import { useEffect, useState } from "react";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { ApiError } from "../../api/client";
import { getPublicFairs, type FairPublicListItem, type PublicFairListFilter } from "../../api/fair";
import { FairPublicCard } from "./FairPublicCard";

interface FairPublicListPageProps {
  filter: PublicFairListFilter;
  eyebrow: string;
  title: string;
  description: string;
  emptyTitle: string;
  emptyDescription: string;
  /** 카드를 눌렀을 때 이동할 경로를 만든다. 생략하면 카드는 클릭할 수 없다. */
  linkTo?: (fairId: number) => string;
}

/** 단일 필터 공개 행사 목록. 현재는 /tickets(티켓 예매 진입)가 filter/문구만 바꿔 재사용한다. */
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
