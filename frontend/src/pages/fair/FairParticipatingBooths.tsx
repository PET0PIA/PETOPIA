import { useEffect, useState } from "react";
import { ImageOff, Star } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import {
  addBoothFavorite,
  getConfirmedBooths,
  getMyFavoriteBooths,
  removeBoothFavorite,
  type ConfirmedBoothResponse,
} from "../../api/booth";

interface Company {
  boothId: number;
  businessName: string;
  imageUrl: string | null;
  hallName: string;
}

// confirmed-booths는 부스가 슬롯을 여러 개 쓰면 슬롯당 한 행으로 온다. boothId로 묶어 "참가기업" 하나로 만든다.
function toCompanies(rows: ConfirmedBoothResponse[]): Company[] {
  const byId = new Map<number, Company>();
  for (const row of rows) {
    if (!byId.has(row.boothId)) {
      byId.set(row.boothId, { boothId: row.boothId, businessName: row.businessName, imageUrl: row.imageUrl, hallName: row.hallName });
    }
  }
  return [...byId.values()];
}

/**
 * 행사 상세의 "참가기업" 섹션. confirmed-booths(공개)로 참가기업을, 로그인 시 즐겨찾기 목록으로 별표 상태를 채운다.
 * 즐겨찾기 토글은 낙관적으로 처리한다(백엔드가 add=INSERT IGNORE·remove=0행 무시로 멱등이라 중복 클릭이 안전).
 * 참가기업 조회가 비거나 실패하면 섹션 자체를 숨긴다(상세의 부가 정보라 페이지를 막지 않는다).
 */
export function FairParticipatingBooths({ fairId }: { fairId: number }) {
  const { status } = useAuth();
  const loggedIn = status === "authenticated";
  const navigate = useNavigate();

  const [companies, setCompanies] = useState<Company[]>([]);
  const [favoriteIds, setFavoriteIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);

  // 참가기업(공개)
  useEffect(() => {
    let alive = true;
    getConfirmedBooths(fairId)
      .then((rows) => {
        if (alive) setCompanies(toCompanies(rows));
      })
      .catch(() => {
        /* 부가 정보라 조용히 숨김 */
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [fairId]);

  // 내 즐겨찾기(로그인 시에만) → boothId Set으로 별표 상태 교차
  useEffect(() => {
    if (!loggedIn) return;
    let alive = true;
    getMyFavoriteBooths()
      .then((favorites) => {
        if (alive) setFavoriteIds(new Set(favorites.map((favorite) => favorite.boothId)));
      })
      .catch(() => {
        /* 별표만 안 채워질 뿐이라 무시 */
      });
    return () => {
      alive = false;
    };
  }, [fairId, loggedIn]);

  function toggleFavorite(boothId: number) {
    if (!loggedIn) {
      navigate("/login");
      return;
    }
    const wasFavorite = favoriteIds.has(boothId);
    // 낙관적 갱신: 먼저 UI를 바꾸고, 실패하면 되돌린다.
    setFavoriteIds((prev) => {
      const next = new Set(prev);
      if (wasFavorite) next.delete(boothId);
      else next.add(boothId);
      return next;
    });
    const request = wasFavorite ? removeBoothFavorite(boothId) : addBoothFavorite(boothId);
    request.catch(() => {
      setFavoriteIds((prev) => {
        const next = new Set(prev);
        if (wasFavorite) next.add(boothId);
        else next.delete(boothId);
        return next;
      });
    });
  }

  // 로딩 중이거나 참가기업이 없으면 섹션을 통째로 숨긴다.
  if (loading || companies.length === 0) return null;

  return (
    <section className="mt-10">
      <h2 className="text-lg font-extrabold">
        참가기업 <span className="text-sm font-normal text-muted">{companies.length}</span>
      </h2>
      <ul className="mt-3 grid grid-cols-2 gap-x-4 gap-y-5 sm:grid-cols-3 lg:grid-cols-4">
        {companies.map((company) => {
          const favorite = favoriteIds.has(company.boothId);
          return (
            <li key={company.boothId} className="relative">
              <Link to={`/booths/${company.boothId}`} className="group block">
                <div className="aspect-square overflow-hidden rounded-card bg-surface-alt">
                  {company.imageUrl ? (
                    <img
                      src={company.imageUrl}
                      alt={`${company.businessName} 부스`}
                      className="size-full object-cover transition-transform duration-300 group-hover:scale-105"
                    />
                  ) : (
                    <div className="grid size-full place-items-center text-muted">
                      <ImageOff size={24} aria-hidden="true" />
                    </div>
                  )}
                </div>
                <p className="mt-2 truncate text-sm font-bold">{company.businessName}</p>
                <p className="truncate text-xs text-muted">{company.hallName}</p>
              </Link>
              <button
                type="button"
                onClick={() => toggleFavorite(company.boothId)}
                aria-label={favorite ? `${company.businessName} 즐겨찾기 해제` : `${company.businessName} 즐겨찾기 추가`}
                aria-pressed={favorite}
                className="absolute right-2 top-2 grid size-9 place-items-center rounded-full bg-card/90 shadow-sm transition hover:bg-card"
              >
                <Star size={18} fill={favorite ? "currentColor" : "none"} className={favorite ? "text-sun" : "text-muted"} aria-hidden="true" />
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
