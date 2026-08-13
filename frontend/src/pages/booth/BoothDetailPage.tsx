import { AlertCircle, ChevronLeft, Heart, ImageIcon, Settings } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getBooth, addBoothFavorite, removeBoothFavorite, type BoothResponse, type BoothItemType, type BoothTargetAnimal } from "../../api/booth";
import { useAuth } from "../../contexts/AuthContext";

const targetAnimalLabels: Record<BoothTargetAnimal, string> = {
  DOG: "강아지",
  CAT: "고양이",
  ETC: "기타",
};

const itemTypeLabels: Record<BoothItemType, string> = {
  PRODUCT: "판매상품",
  EVENT: "이벤트",
  SAMPLE: "체험·샘플",
};

function BackLink() {
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => navigate(-1)}
      className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink"
    >
      <ChevronLeft size={16} />
      목록으로
    </button>
  );
}

export function BoothDetailPage() {
  const { boothId } = useParams<{ boothId: string }>();
  const id = Number(boothId);
  const idValid = Number.isInteger(id) && id > 0;

  if (!idValid) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="잘못된 부스 주소예요."
            description="부스 주소가 올바르지 않아요."
            actionTo="/"
            actionLabel="홈으로"
          />
        </div>
      </div>
    );
  }

  // id별로 key를 줘서, 다른 부스 상세로 이동할 때 컴포넌트가 완전히 새로 마운트되게 한다
  // (ApplicationDetailPage와 동일한 이유).
  return <BoothDetailContent key={id} id={id} />;
}

function BoothDetailContent({ id }: { id: number }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [booth, setBooth] = useState<BoothResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [favorited, setFavorited] = useState(false);
  const [favoriteBusy, setFavoriteBusy] = useState(false);
  const [favoriteError, setFavoriteError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getBooth(id)
      .then((res) => {
        if (!alive) return;
        setBooth(res);
        setFavorited(res.favorited);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "부스를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [id]);

  async function handleToggleFavorite() {
    if (!user) {
      navigate("/login", { state: { from: location.pathname } });
      return;
    }
    if (favoriteBusy) return;

    const previous = favorited;
    setFavorited(!previous);
    setFavoriteError(null);
    setFavoriteBusy(true);
    try {
      if (previous) {
        await removeBoothFavorite(id);
      } else {
        await addBoothFavorite(id);
      }
    } catch (err) {
      setFavorited(previous);
      setFavoriteError(err instanceof ApiError ? err.message : "즐겨찾기 처리에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setFavoriteBusy(false);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <p className="py-16 text-center text-sm text-muted">부스를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (loadError || !booth) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="부스를 찾을 수 없어요."
            description={loadError ?? "부스 정보를 불러올 수 없어요."}
            actionTo="/"
            actionLabel="홈으로"
          />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <BackLink />

      <div className="mt-4 mb-6 flex items-start justify-between gap-4">
        <div className="flex items-start gap-4">
          <div className="grid size-20 shrink-0 place-items-center overflow-hidden rounded-button border border-line bg-page text-muted">
            {booth.imageUrl ? (
              <img src={booth.imageUrl} alt="" className="size-full object-cover" />
            ) : (
              <ImageIcon size={24} />
            )}
          </div>
          <div>
            <div className="mb-2 flex flex-wrap items-center gap-2">
              {booth.category && <Badge tone="primary">{booth.category}</Badge>}
              {booth.targetAnimal && <Badge tone="sun">{targetAnimalLabels[booth.targetAnimal]}</Badge>}
            </div>
            <h1 className="text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">{booth.name}</h1>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          {user && (
            <Link
              to={`/booths/${id}/edit`}
              className="inline-flex min-h-11 items-center gap-2 rounded-button border border-line bg-card px-4 text-sm font-bold text-ink hover:bg-page"
            >
              <Settings size={16} />
              부스 관리
            </Link>
          )}
          <Button
            type="button"
            variant={favorited ? "primary" : "outline"}
            onClick={handleToggleFavorite}
            disabled={favoriteBusy}
          >
            <Heart size={16} fill={favorited ? "currentColor" : "none"} />
            {favorited ? "즐겨찾기됨" : "즐겨찾기"}
          </Button>
        </div>
      </div>

      {favoriteError && (
        <div className="mb-4 flex items-start gap-2 text-sm text-primary-strong">
          <AlertCircle size={16} className="mt-0.5 shrink-0" />
          <p>{favoriteError}</p>
        </div>
      )}

      <div className="space-y-6">
        {booth.intro && (
          <Card className="space-y-2 p-6">
            <h3 className="text-sm font-extrabold text-muted">소개</h3>
            <p className="text-sm leading-6 text-ink">{booth.intro}</p>
          </Card>
        )}

        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">판매상품·이벤트</h3>
          {booth.items.length === 0 ? (
            <p className="text-sm text-muted">등록된 상품·이벤트가 없어요.</p>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              {booth.items.map((item) => (
                <div key={item.boothItemId} className="flex items-center gap-3 rounded-button border border-line p-3">
                  <div className="grid size-12 shrink-0 place-items-center overflow-hidden rounded-button bg-page text-muted">
                    {item.imageUrl ? (
                      <img src={item.imageUrl} alt="" className="size-full object-cover" />
                    ) : (
                      <ImageIcon size={18} />
                    )}
                  </div>
                  <div className="min-w-0">
                    <div className="mb-1 flex items-center gap-2">
                      <p className="truncate font-bold text-ink">{item.name}</p>
                      <Badge tone="neutral">{itemTypeLabels[item.type]}</Badge>
                    </div>
                    {item.note && <p className="truncate text-xs text-muted">{item.note}</p>}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}