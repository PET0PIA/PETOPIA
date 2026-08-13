import { Heart, ImageIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { ApiError } from "../../api/client";
import { getMyFavoriteBooths, removeBoothFavorite, type BoothFavoriteResponse, type BoothTargetAnimal } from "../../api/booth";
import { useAuth } from "../../contexts/AuthContext";

const targetAnimalLabels: Record<BoothTargetAnimal, string> = {
  DOG: "강아지",
  CAT: "고양이",
  ETC: "기타",
};

export function BoothFavoritesPage() {
  const { user } = useAuth();
  const [favorites, setFavorites] = useState<BoothFavoriteResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) {
      setFavorites([]);
      setLoadError(null);
      setLoading(false);
      return;
    }
    let ignore = false;

    setLoading(true);
    getMyFavoriteBooths()
      .then((data) => {
        if (!ignore) setFavorites(data);
      })
      .catch((error) => {
        if (ignore) return;
        setLoadError(error instanceof ApiError ? error.message : "즐겨찾기 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [user]);

  async function handleRemove(boothId: number) {
    const index = favorites.findIndex((f) => f.boothId === boothId);
    if (index === -1) return;

    const removed = favorites[index];
    setActionError(null);
    // 낙관적 업데이트: 목록에서 즉시 제거
    setFavorites((prev) => prev.filter((f) => f.boothId !== boothId));

    try {
      await removeBoothFavorite(boothId);
    } catch (err) {
      // 실패하면 원래 위치로 복원
      setFavorites((prev) => {
        const next = [...prev];
        next.splice(index, 0, removed);
        return next;
      });
      setActionError(err instanceof ApiError ? err.message : "즐겨찾기 해제에 실패했어요. 잠시 후 다시 시도해 주세요.");
    }
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="부스" title="내 즐겨찾기" />

      {loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!loading && loadError && <EmptyState title="목록을 불러올 수 없어요" description={loadError} />}

      {!loading && !user && (
        <EmptyState title="로그인이 필요해요" description="로그인 후 즐겨찾기한 부스를 확인할 수 있어요." actionTo="/login" actionLabel="로그인하러 가기" />
      )}

      {!loading && !loadError && user && favorites.length === 0 && (
        <EmptyState title="즐겨찾기한 부스가 없어요" description="관심 있는 부스를 즐겨찾기해 보세요." actionTo="/" actionLabel="홈으로" />
      )}

      {actionError && <p className="mb-4 text-sm font-bold text-primary-strong">{actionError}</p>}

      {!loading && !loadError && favorites.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2">
          {favorites.map((favorite) => (
            <div key={favorite.boothId} className="surface flex items-center justify-between gap-3 p-5">
              <Link to={`/booths/${favorite.boothId}`} className="flex min-w-0 flex-1 items-center gap-3 hover:opacity-80">
                <div className="grid size-12 shrink-0 place-items-center overflow-hidden rounded-full bg-primary-soft text-primary-strong">
                  {favorite.imageUrl ? (
                    <img src={favorite.imageUrl} alt="" className="size-full object-cover" />
                  ) : (
                    <ImageIcon size={18} />
                  )}
                </div>
                <div className="min-w-0">
                  <p className="truncate font-bold text-ink">{favorite.name}</p>
                  <p className="truncate text-xs text-muted">{favorite.fairName}</p>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {favorite.category && <Badge tone="primary">{favorite.category}</Badge>}
                    {favorite.targetAnimal && <Badge tone="sun">{targetAnimalLabels[favorite.targetAnimal]}</Badge>}
                  </div>
                </div>
              </Link>
              <button
                type="button"
                onClick={() => handleRemove(favorite.boothId)}
                className="grid size-11 shrink-0 place-items-center rounded-full text-primary-strong hover:bg-page"
                aria-label="즐겨찾기 해제"
              >
                <Heart size={18} fill="currentColor" />
              </button>
            </div>
          ))}
        </div>
      )}
    </PageContainer>
  );
}