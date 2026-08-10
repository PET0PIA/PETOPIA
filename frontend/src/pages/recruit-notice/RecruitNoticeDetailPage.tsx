import { AlertCircle, CalendarClock, Send } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { HallBoothMap } from "../../components/booth-map/HallBoothMap";
import { ApiError } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import { Pencil } from "lucide-react";
import { getRecruitNotice, type BoothSlotStatus, type RecruitNotice, type RecruitNoticeBoothSlot } from "../../api/recruitNotice";

const slotStatusLabels: Record<BoothSlotStatus, string> = {
  AVAILABLE: "선택 가능",
  PENDING: "확정 대기",
  CONFIRMED: "확정 완료",
};

const slotStatusTones: Record<BoothSlotStatus, "leaf" | "sun" | "neutral"> = {
  AVAILABLE: "leaf",
  PENDING: "sun",
  CONFIRMED: "neutral",
};

function formatDeadline(value: string) {
  return new Date(value).toLocaleString("ko-KR", { year: "numeric", month: "long", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

// 슬롯이 속한 booth_slots.locked_at 여부가 아니라, 우리 도메인이 계산한 status로 배치도 잠금 표시를 결정한다.
function toSlotCaption(slot: RecruitNoticeBoothSlot): string {
  if (slot.status === "CONFIRMED" && slot.businessName) return slot.businessName;
  return slotStatusLabels[slot.status];
}

// 홀별로 그룹핑한다 - posX/posY가 홀마다 다른 도면 기준 좌표라 섞어서 그리면 안 된다.
function groupByHall(slots: RecruitNoticeBoothSlot[]) {
  const groups = new Map<number, { hallName: string; floorPlanImageUrl: string | null; slots: RecruitNoticeBoothSlot[] }>();
  for (const slot of slots) {
    const group = groups.get(slot.hallId);
    if (group) {
      group.slots.push(slot);
    } else {
      groups.set(slot.hallId, { hallName: slot.hallName, floorPlanImageUrl: slot.floorPlanImageUrl, slots: [slot] });
    }
  }
  return [...groups.entries()].map(([hallId, group]) => ({ hallId, ...group }));
}

export function RecruitNoticeDetailPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const { user } = useAuth();
  const [notice, setNotice] = useState<RecruitNotice | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    if (!fairId) return;
    let ignore = false;

    setNotFound(false);
    setLoadError(null);
    getRecruitNotice(Number(fairId))
      .then((data) => { if (!ignore) setNotice(data); })
      .catch((error) => {
        if (ignore) return;
        if (error instanceof ApiError && error.status === 404) {
          setNotFound(true);
          return;
        }
        setLoadError(error instanceof ApiError ? error.message : "모집 공고를 불러오지 못했어요.");
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId]);

  if (loading) {
    return <PageContainer className="py-10"><p className="text-sm text-muted">불러오는 중...</p></PageContainer>;
  }

  if (notFound) {
    const canCreate = user?.role === "EVENT_ADMIN" || user?.role === "SUPER_ADMIN";
    return (
      <PageContainer className="py-10">
        <EmptyState
          title="아직 등록된 모집 공고가 없어요"
          description={canCreate ? "담당 행사라면 아래에서 공고를 작성할 수 있어요." : "모집 공고가 등록되면 이곳에서 확인할 수 있어요."}
          actionTo={canCreate ? `/fair-admin/recruit-notice/${fairId}` : undefined}
          actionLabel="모집 공고 작성하기"
        />
      </PageContainer>
    );
  }

  if (loadError || !notice) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="모집 공고를 찾을 수 없어요" description={loadError ?? undefined} />
      </PageContainer>
    );
  }

  const hallGroups = groupByHall(notice.boothSlots);

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="참가업체 모집"
        title={notice.title}
        description={notice.closed ? "모집이 마감된 공고예요." : "아래 부스 슬롯 현황을 확인하고 신청해 주세요."}
        action={
          (user?.role === "EVENT_ADMIN" || user?.role === "SUPER_ADMIN") ? (
            <Link
              to={`/fair-admin/recruit-notice/${notice.fairId}`}
              className="inline-flex min-h-11 items-center gap-2 rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page"
            >
              <Pencil size={16} />
              수정하기
            </Link>
          ) : undefined
        }
      />

      {notice.imageUrl && (
        <div className="surface mb-6 overflow-hidden">
          <img src={notice.imageUrl} alt={`${notice.title} 공고 이미지`} className="max-h-80 w-full object-cover" />
        </div>
      )}

      <Card className="mb-6 space-y-4 p-6">
        <div className="flex items-center gap-2 text-sm text-muted">
          <CalendarClock size={16} />
          <span>모집 마감: {formatDeadline(notice.recruitDeadline)}</span>
          {notice.closed && <Badge tone="neutral">마감</Badge>}
        </div>
        <p className="whitespace-pre-wrap text-sm leading-6 text-ink">{notice.content}</p>
      </Card>

      <section className="mb-8">
        <SectionHeader title="부스 슬롯 현황" />
        {hallGroups.length === 0 ? (
          <EmptyState title="등록된 부스 슬롯이 없어요" />
        ) : (
          <>
            <div className="space-y-6">
              {hallGroups.map((group) => (
                <HallBoothMap
                  key={group.hallId}
                  hallName={group.hallName}
                  backgroundImageUrl={group.floorPlanImageUrl}
                  slots={group.slots.map((slot) => ({
                    boothSlotsId: slot.boothSlotsId,
                    slotNumber: slot.slotNumber,
                    posX: slot.posX,
                    posY: slot.posY,
                    width: slot.width,
                    height: slot.height,
                    caption: toSlotCaption(slot),
                    tone: slotStatusTones[slot.status],
                    locked: slot.status !== "AVAILABLE",
                  }))}
                />
              ))}
            </div>
            <div className="mt-3 flex flex-wrap gap-4 text-xs text-muted">
              <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm bg-leaf-soft" /> 선택 가능</span>
              <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm bg-sun-soft" /> 확정 대기</span>
              <span className="inline-flex items-center gap-1.5"><span className="size-3 rounded-sm bg-muted/15" /> 확정 완료</span>
            </div>
          </>
        )}
      </section>

      {!notice.closed && (
        <div className="surface flex flex-col items-center gap-3 p-6 text-center sm:flex-row sm:justify-between sm:text-left">
          <div className="flex items-start gap-3">
            <AlertCircle size={18} className="mt-0.5 shrink-0 text-muted" />
            <p className="text-sm text-muted">신청 전, 사업자 등록이 먼저 완료돼 있어야 해요.</p>
          </div>
          <Link
            to={`/fairs/${notice.fairId}/apply`}
            className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-button bg-primary-strong px-5 text-sm font-bold text-white hover:opacity-90 sm:w-auto"
          >
            <Send size={16} />
            신청하기
          </Link>
        </div>
      )}
    </PageContainer>
  );
}