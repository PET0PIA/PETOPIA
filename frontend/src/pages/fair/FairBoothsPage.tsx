import { ChevronLeft, ChevronRight, ImageOff, Star } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { ApiError } from "../../api/client";
import { Badge } from "../../components/ui/Badge";
import {
  addBoothFavorite,
  type BoothTargetAnimal,
  getConfirmedBooths,
  getMyFavoriteBooths,
  removeBoothFavorite,
  type ConfirmedBoothResponse,
} from "../../api/booth";
import { useAuth } from "../../contexts/AuthContext";

const targetAnimalLabels: Record<BoothTargetAnimal, string> = {
  DOG: "강아지",
  CAT: "고양이",
  ETC: "기타",
};

// 부스 하나가 슬롯을 여러 개 쓰면 여러 행으로 내려오므로, boothId 기준으로 묶어서
// 슬롯 번호를 전부 모은다(하나만 보여주면 나머지 슬롯 정보가 사라짐).
interface BoothGroup {
  boothId: number;
  businessName: string;
  imageUrl: string | null;
  category: string | null;
  targetAnimal: BoothTargetAnimal | null;
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
        <EmptyState title="잘못된 행사 주소예요." description="행사 주소가 올바르지 않아요." actionTo="/fairs/upcoming" actionLabel="행사 목록으로" />
      </PageContainer>
    );
  }

  return <FairBoothsContent key={id} fairId={id} />;
}

function FairBoothsContent({ fairId }: { fairId: number }) {
  const navigate = useNavigate();
  const { status } = useAuth();
  const loggedIn = status === "authenticated";

  const [booths, setBooths] = useState<ConfirmedBoothResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [favoriteIds, setFavoriteIds] = useState<Set<number>>(new Set());
  // 요청이 진행 중인 부스. 응답이 올 때까지 그 버튼을 잠가 add/remove 순서가 뒤집히지 않게 한다.
  const [pendingFavoriteIds, setPendingFavoriteIds] = useState<Set<number>>(new Set());
  // 초기 즐겨찾기 목록 로딩이 끝났는지. 로딩 중에 별을 누르면 나중에 도착하는 초기 목록 응답이
  // 그 사이의 낙관적 갱신을 통째로 덮어써버릴 수 있어(코드래빗 리뷰), 로딩이 끝나기 전까진 토글을 막는다.
  const [favoritesLoaded, setFavoritesLoaded] = useState(false);

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

  // 내 즐겨찾기(로그인 시에만) → boothId Set으로 별표 상태 교차.
  useEffect(() => {
    if (!loggedIn) {
      setFavoritesLoaded(true); // 비로그인은 애초에 기다릴 목록이 없다
      return;
    }
    let alive = true;
    getMyFavoriteBooths()
      .then((favorites) => {
        if (alive) setFavoriteIds(new Set(favorites.map((favorite) => favorite.boothId)));
      })
      .catch(() => {
        /* 별표만 안 채워질 뿐이라 무시 */
      })
      .finally(() => {
        if (alive) setFavoritesLoaded(true);
      });
    return () => {
      alive = false;
      setFavoriteIds(new Set());
      setFavoritesLoaded(false);
    };
  }, [fairId, loggedIn]);

  function toggleFavorite(boothId: number) {
    if (!loggedIn) {
      navigate("/login");
      return;
    }
    if (!favoritesLoaded) return; // 초기 목록 로딩 중엔 토글 자체를 막는다(버튼도 비활성화되지만 이중 방어)
    if (pendingFavoriteIds.has(boothId)) return;
    const wasFavorite = favoriteIds.has(boothId);
    // 낙관적 갱신: 먼저 UI를 바꾸고, 실패하면 되돌린다.
    setFavoriteIds((prev) => {
      const next = new Set(prev);
      if (wasFavorite) next.delete(boothId);
      else next.add(boothId);
      return next;
    });
    setPendingFavoriteIds((prev) => new Set(prev).add(boothId));
    const request = wasFavorite ? removeBoothFavorite(boothId) : addBoothFavorite(boothId);
    request
      .catch(() => {
        setFavoriteIds((prev) => {
          const next = new Set(prev);
          if (wasFavorite) next.add(boothId);
          else next.delete(boothId);
          return next;
        });
      })
      .finally(() => {
        setPendingFavoriteIds((prev) => {
          const next = new Set(prev);
          next.delete(boothId);
          return next;
        });
      });
  }

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
        category: booth.category,
        targetAnimal: booth.targetAnimal,
        hallName: booth.hallName,
        slotNumbers: [booth.slotNumber],
      });
    }
  }

  return (
    <PageContainer className="py-7 sm:py-10">
      <Link to={`/fairs/${fairId}`} className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink">
        <ChevronLeft size={16} />
        행사 상세로
      </Link>

      <div className="mt-4">
        <PageHeader
          eyebrow="참가업체"
          title="확정 참가 부스"
        />
      </div>

      {loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!loading && error && <EmptyState title="부스 목록을 불러오지 못했어요." description={error} />}

      {!loading && !error && boothGroups.length === 0 && (
        <EmptyState title="아직 확정된 부스가 없어요." description="참가 신청이 확정되면 이곳에 표시돼요." />
      )}

      {!loading && !error && boothGroups.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
          {boothGroups.map((group) => {
            const favorite = favoriteIds.has(group.boothId);
            const pending = pendingFavoriteIds.has(group.boothId);
            return (
              <li key={group.boothId} className="relative">
                <Link
                  to={`/booths/${group.boothId}`}
                  className="surface flex items-center gap-3 p-5 pr-9 transition hover:bg-page"
                >
                  <div className="grid size-12 shrink-0 place-items-center overflow-hidden rounded-full bg-primary-soft text-primary-strong">
                    {group.imageUrl ? (
                      <img src={group.imageUrl} alt="" className="size-full object-cover" />
                    ) : (
                      <ImageOff size={18} />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-bold text-ink">{group.businessName}</p>
                    <p className="truncate text-xs text-muted">
                      {group.hallName} · {group.slotNumbers.join(", ")}
                    </p>
                    {(group.category || group.targetAnimal) && (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {group.category && <Badge tone="primary">{group.category}</Badge>}
                        {group.targetAnimal && <Badge tone="sun">{targetAnimalLabels[group.targetAnimal]}</Badge>}
                      </div>
                    )}
                  </div>
                  <ChevronRight size={16} className="shrink-0 self-center text-muted" aria-hidden="true" />
                </Link>
                <button
                  type="button"
                  onClick={() => toggleFavorite(group.boothId)}
                  disabled={pending || (loggedIn && !favoritesLoaded)}
                  aria-label={favorite ? `${group.businessName} 즐겨찾기 해제` : `${group.businessName} 즐겨찾기 추가`}
                  aria-pressed={favorite}
                  className="absolute right-1.5 top-1.5 grid size-8 place-items-center rounded-full text-muted hover:bg-page disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <Star size={16} fill={favorite ? "currentColor" : "none"} className={favorite ? "text-sun" : "text-muted"} aria-hidden="true" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </PageContainer>
  );
}
