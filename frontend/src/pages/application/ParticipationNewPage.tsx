import { LayoutGrid, List } from "lucide-react";
import { useEffect, useState } from "react";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { ApiError } from "../../api/client";
import { getRecruitingFairs, type FairPublicListItem } from "../../api/fair";
import { FairPublicCard } from "../fair/FairPublicCard";

type ViewMode = "grid" | "list";

/** 참여 부스 신청 진입점. 지금 참가기업 모집중인 행사만 골라 보여주고, 클릭하면 신청서 작성으로 바로 이동한다. */
export function ParticipationNewPage() {
  const [fairs, setFairs] = useState<FairPublicListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<ViewMode>("grid");

  useEffect(() => {
    let alive = true;
    // 모집중인 행사는 "전체공개"(published) 여부와 무관하다 - 담당자가 모집공고+부스슬롯만
    // 준비하면, 아직 일반 소비자에게 전체공개하지 않았어도 여기서 찾아 신청할 수 있어야 한다.
    getRecruitingFairs()
      .then((data) => {
        if (alive) setFairs(data);
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

  const hasFairs = !loading && !error && fairs.length > 0;

  // 카드 보기(격자) ↔ 리스트 보기 전환 토글. 결과가 있을 때만 헤더에 노출한다.
  const viewToggle = (
    <div className="inline-flex rounded-button border border-line bg-card p-1" role="group" aria-label="보기 방식">
      {([
        { mode: "grid" as const, icon: LayoutGrid, label: "카드 보기" },
        { mode: "list" as const, icon: List, label: "리스트 보기" },
      ]).map(({ mode, icon: Icon, label }) => (
        <button
          key={mode}
          type="button"
          onClick={() => setView(mode)}
          aria-pressed={view === mode}
          aria-label={label}
          className={`grid size-9 place-items-center rounded-button transition-colors ${
            view === mode ? "bg-primary-strong text-white" : "text-muted hover:text-ink"
          }`}
        >
          <Icon size={18} aria-hidden="true" />
        </button>
      ))}
    </div>
  );

  return (
    <PageContainer className="py-7 sm:py-10">
      <PageHeader
        eyebrow="참여 부스 신청"
        title="참가기업 모집중인 행사"
        description="행사를 선택하면 참가 신청서 작성으로 이동해요."
        action={hasFairs ? viewToggle : undefined}
      />

      {loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!loading && error && <EmptyState title="행사 목록을 불러오지 못했어요." description={error} />}

      {!loading && !error && fairs.length === 0 && (
        <EmptyState title="지금 모집중인 행사가 없어요." description="새로운 모집공고가 열리면 이곳에서 확인할 수 있어요." />
      )}

      {hasFairs && (
        <div
          className={
            view === "grid"
              ? "grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5"
              : "flex flex-col gap-3"
          }
        >
          {fairs.map((fair) => (
            <FairPublicCard
              key={fair.fairId}
              layout={view === "list" ? "list" : "card"}
              fair={fair}
              to={`/fairs/${fair.fairId}/apply`}
              action={{ to: `/fairs/${fair.fairId}/apply`, label: "신청하기" }}
            />
          ))}
        </div>
      )}
    </PageContainer>
  );
}
