import { useEffect, useState } from "react";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { ApiError } from "../../api/client";
import { getPublicFairs, type FairPublicListItem } from "../../api/fair";
import { FairPublicCard } from "../fair/FairPublicCard";

/** 참여 부스 신청 진입점. 지금 참가기업 모집중인 행사만 골라 보여주고, 클릭하면 신청서 작성으로 바로 이동한다. */
export function ParticipationNewPage() {
  const [fairs, setFairs] = useState<FairPublicListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    // 모집중인 행사는 아직 안 끝난 행사(UPCOMING)에만 있을 수 있다 - recruiting은
    // 행사 종료 여부도 조건에 포함하므로, PAST를 따로 조회할 필요가 없다.
    getPublicFairs("UPCOMING")
      .then((data) => {
        if (alive) setFairs(data.filter((fair) => fair.recruiting));
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
  }, []);

  return (
    <PageContainer className="py-7 sm:py-10">
      <PageHeader eyebrow="참여 부스 신청" title="참가기업 모집중인 행사" description="행사를 선택하면 참가 신청서 작성으로 이동해요." />

      {loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!loading && error && <EmptyState title="행사 목록을 불러오지 못했어요." description={error} />}

      {!loading && !error && fairs.length === 0 && (
        <EmptyState title="지금 모집중인 행사가 없어요." description="새로운 모집공고가 열리면 이곳에서 확인할 수 있어요." />
      )}

      {!loading && !error && fairs.length > 0 && (
        <div className="grid gap-5 md:grid-cols-3">
          {fairs.map((fair) => (
            <FairPublicCard key={fair.fairId} fair={fair} to={`/fairs/${fair.fairId}/apply`} action={{ to: `/fairs/${fair.fairId}/apply`, label: "신청하기" }} />
          ))}
        </div>
      )}
    </PageContainer>
  );
}