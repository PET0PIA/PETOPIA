import { ImageIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { ApiError } from "../../api/client";
import { getMyBooths, type BoothFavoriteResponse, type BoothTargetAnimal } from "../../api/booth";
import { useAuth } from "../../contexts/AuthContext";

const targetAnimalLabels: Record<BoothTargetAnimal, string> = {
  DOG: "강아지",
  CAT: "고양이",
  ETC: "기타",
};

export function MyBoothsPage() {
  const { user } = useAuth();
  const [booths, setBooths] = useState<BoothFavoriteResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) {
      setBooths([]);
      setLoadError(null);
      setLoading(false);
      return;
    }
    let ignore = false;

    setLoading(true);
    getMyBooths()
      .then((data) => {
        if (!ignore) setBooths(data);
      })
      .catch((error) => {
        if (ignore) return;
        setLoadError(error instanceof ApiError ? error.message : "부스 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [user]);

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="부스" title="내 부스 관리" />

      {loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!loading && loadError && <EmptyState title="목록을 불러올 수 없어요" description={loadError} />}

      {!loading && !user && (
        <EmptyState title="로그인이 필요해요" description="로그인 후 내 부스 목록을 확인할 수 있어요." actionTo="/login" actionLabel="로그인하러 가기" />
      )}

      {!loading && !loadError && user && booths.length === 0 && (
        <EmptyState title="관리할 부스가 없어요" description="참가 신청이 확정되면 이곳에 부스가 표시돼요." />
      )}

      {!loading && !loadError && booths.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2">
          {booths.map((booth) => (
            <Link
              key={booth.boothId}
              to={`/booths/${booth.boothId}/edit`}
              className="surface flex items-center gap-3 p-5 transition hover:bg-page"
            >
              <div className="grid size-12 shrink-0 place-items-center overflow-hidden rounded-full bg-primary-soft text-primary-strong">
                {booth.imageUrl ? (
                  <img src={booth.imageUrl} alt="" className="size-full object-cover" />
                ) : (
                  <ImageIcon size={18} />
                )}
              </div>
              <div className="min-w-0">
                <p className="truncate font-bold text-ink">{booth.name}</p>
                <p className="truncate text-xs text-muted">{booth.fairName}</p>
                <div className="mt-1 flex flex-wrap gap-1">
                  {booth.category && <Badge tone="primary">{booth.category}</Badge>}
                  {booth.targetAnimal && <Badge tone="sun">{targetAnimalLabels[booth.targetAnimal]}</Badge>}
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </PageContainer>
  );
}