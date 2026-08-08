import { AlertCircle, CalendarClock, Send } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { Table } from "../../components/ui/Table";
import { ApiError } from "../../api/client";
import { getRecruitNotice, type BoothSlotStatus, type RecruitNotice } from "../../api/recruitNotice";

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

export function RecruitNoticeDetailPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const [notice, setNotice] = useState<RecruitNotice | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!fairId) return;
    let ignore = false;

    getRecruitNotice(Number(fairId))
      .then((data) => { if (!ignore) setNotice(data); })
      .catch((error) => {
        if (ignore) return;
        setLoadError(error instanceof ApiError ? error.message : "모집 공고를 불러오지 못했어요.");
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId]);

  if (loading) {
    return <PageContainer className="py-10"><p className="text-sm text-muted">불러오는 중...</p></PageContainer>;
  }

  if (loadError || !notice) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="모집 공고를 찾을 수 없어요" description={loadError ?? undefined} />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="참가업체 모집"
        title={notice.title}
        description={notice.closed ? "모집이 마감된 공고예요." : "아래 부스 슬롯 현황을 확인하고 신청해 주세요."}
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
        <h2 className="mb-3 text-lg font-extrabold">부스 슬롯 현황</h2>
        {notice.boothSlots.length === 0 ? (
          <EmptyState title="등록된 부스 슬롯이 없어요" />
        ) : (
          <Table>
            <thead>
              <tr className="border-b border-line text-xs font-bold text-muted">
                <th className="px-4 py-3">슬롯 번호</th>
                <th className="px-4 py-3">가격</th>
                <th className="px-4 py-3">상태</th>
                <th className="px-4 py-3">확정 업체</th>
              </tr>
            </thead>
            <tbody>
              {notice.boothSlots.map((slot) => (
                <tr key={slot.boothSlotsId} className="border-b border-line last:border-0">
                  <td className="px-4 py-3 font-bold">{slot.slotNumber}</td>
                  <td className="px-4 py-3">{slot.price.toLocaleString()}원</td>
                  <td className="px-4 py-3"><Badge tone={slotStatusTones[slot.status]}>{slotStatusLabels[slot.status]}</Badge></td>
                  <td className="px-4 py-3 text-muted">{slot.businessName ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </Table>
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