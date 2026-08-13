import { ChevronLeft, ImageOff } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { ApiError } from "../../api/client";
import { getConfirmedBooths, type ConfirmedBoothResponse } from "../../api/booth";

// 부스 하나가 슬롯을 여러 개 쓰면 여러 행으로 내려오므로, boothId 기준으로 묶어서
// 슬롯 번호를 전부 모은다(하나만 보여주면 나머지 슬롯 정보가 사라짐).
interface BoothGroup {
  boothId: number;
  businessName: string;
  imageUrl: string | null;
  hallName: string;
  slotNumbers: string[];
}

export function FairBoothsPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const id = Number(fairId);
  const idValid = Number.isInteger(id) && id > 0;

  if (!idValid) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="잘못된 행사 주소예요." description="행사 주소가 올바르지 않아요." actionTo="/businesses" actionLabel="행사별 참여 기업으로" />
      </PageContainer>
    );
  }

  return <FairBoothsContent key={id} fairId={id} />;
}

function FairBoothsContent({ fairId }: { fairId: number }) {
  const [booths, setBooths] = useState<ConfirmedBoothResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getConfirmedBooths(fairId)
      .then((data) => {
        if (alive) setBooths(data);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "부스 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [fairId]);

  // 해당 부스의 모든 슬롯을 묶어서 보여준다
  const boothGroups: BoothGroup[] = [];
  for (const booth of booths) {
    const existing = boothGroups.find((g) => g.boothId === booth.boothId);
    if (existing) {
      existing.slotNumbers.push(booth.slotNumber);
    } else {
      boothGroups.push({
        boothId: booth.boothId,
        businessName: booth.businessName,
        imageUrl: booth.imageUrl,
        hallName: booth.hallName,
        slotNumbers: [booth.slotNumber],
      });
    }
  }

  return (
    <PageContainer className="py-7 sm:py-10">
      <Link to="/businesses" className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink">
        <ChevronLeft size={16} />
        행사별 참여 기업
      </Link>

      <div className="mt-4">
        <PageHeader eyebrow="참여 기업" title="확정 참가 부스" />
      </div>

      {loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!loading && error && <EmptyState title="부스 목록을 불러오지 못했어요." description={error} />}

      {!loading && !error && boothGroups.length === 0 && (
        <EmptyState title="아직 확정된 부스가 없어요." description="참가 신청이 확정되면 이곳에 표시돼요." />
      )}

      {!loading && !error && boothGroups.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
          {boothGroups.map((group) => (
            <Link
              key={group.boothId}
              to={`/booths/${group.boothId}`}
              className="surface flex items-center gap-3 p-5 transition hover:bg-page"
            >
              <div className="grid size-12 shrink-0 place-items-center overflow-hidden rounded-full bg-primary-soft text-primary-strong">
                {group.imageUrl ? (
                  <img src={group.imageUrl} alt="" className="size-full object-cover" />
                ) : (
                  <ImageOff size={18} />
                )}
              </div>
              <div className="min-w-0">
                <p className="truncate font-bold text-ink">{group.businessName}</p>
                <p className="truncate text-xs text-muted">{group.hallName} · {group.slotNumbers.join(", ")}</p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </PageContainer>
  );
}