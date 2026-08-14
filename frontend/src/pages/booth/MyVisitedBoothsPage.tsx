import { ImageIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { ApiError } from "../../api/client";
import {
  getMyVisitedFairs,
  getMyVisitedBooths,
  type VisitedFairResponse,
  type BoothVisitResponse,
} from "../../api/booth";
import { useAuth } from "../../contexts/AuthContext";

export function MyVisitedBoothsPage() {
  const { user } = useAuth();

  const [fairs, setFairs] = useState<VisitedFairResponse[]>([]);
  const [fairsLoading, setFairsLoading] = useState(true);
  const [fairsError, setFairsError] = useState<string | null>(null);

  const [selectedFairId, setSelectedFairId] = useState<number | null>(null);

  const [booths, setBooths] = useState<BoothVisitResponse[]>([]);
  const [boothsLoading, setBoothsLoading] = useState(false);
  const [boothsError, setBoothsError] = useState<string | null>(null);

  // 1단계: 내가 방문한 행사 목록
  useEffect(() => {
    if (!user) {
      setFairs([]);
      setFairsError(null);
      setFairsLoading(false);
      return;
    }
    let ignore = false;

    setFairsLoading(true);
    getMyVisitedFairs()
      .then((data) => {
        if (ignore) return;
        setFairs(data);
        setSelectedFairId((prev) => prev ?? data[0]?.fairId ?? null);
      })
      .catch((error) => {
        if (ignore) return;
        setFairsError(error instanceof ApiError ? error.message : "방문한 행사 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (!ignore) setFairsLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [user]);

  // 2단계: 선택한 행사에서 방문한 부스 목록 (행사 바뀔 때마다 그 행사분만 새로 조회)
  useEffect(() => {
    if (selectedFairId === null) {
      setBooths([]);
      return;
    }
    let ignore = false;

    setBoothsLoading(true);
    setBoothsError(null);
    getMyVisitedBooths(selectedFairId)
      .then((data) => {
        if (!ignore) setBooths(data);
      })
      .catch((error) => {
        if (ignore) return;
        setBoothsError(error instanceof ApiError ? error.message : "방문한 부스 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (!ignore) setBoothsLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [selectedFairId]);

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="부스" title="내가 방문한 부스" description="행사를 선택하면 그 행사에서 방문한 부스를 볼 수 있어요." />

      {fairsLoading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!fairsLoading && fairsError && <EmptyState title="목록을 불러올 수 없어요" description={fairsError} />}

      {!fairsLoading && !user && (
        <EmptyState title="로그인이 필요해요" description="로그인 후 방문한 부스를 확인할 수 있어요." actionTo="/login" actionLabel="로그인하러 가기" />
      )}

      {!fairsLoading && !fairsError && user && fairs.length === 0 && (
        <EmptyState title="아직 방문한 부스가 없어요" description="행사 부스에서 QR을 스캔하면 방문 기록이 남아요." actionTo="/" actionLabel="홈으로" />
      )}

      {!fairsLoading && !fairsError && user && fairs.length > 0 && (
        <>
          <select
            value={selectedFairId ?? ""}
            onChange={(event) => setSelectedFairId(Number(event.target.value))}
            className="mb-6 min-h-11 rounded-button border border-line bg-card px-4 text-sm font-bold"
          >
            {fairs.map((fair) => (
              <option key={fair.fairId} value={fair.fairId}>
                {fair.fairName}
              </option>
            ))}
          </select>

          {boothsLoading && <p className="text-sm text-muted">불러오는 중...</p>}

          {!boothsLoading && boothsError && <EmptyState title="목록을 불러올 수 없어요" description={boothsError} />}

          {!boothsLoading && !boothsError && booths.length === 0 && (
            <EmptyState title="이 행사에서 방문한 부스가 없어요" description="다른 행사를 선택해 보세요." />
          )}

          {!boothsLoading && !boothsError && booths.length > 0 && (
            <div className="grid gap-4 sm:grid-cols-2">
              {booths.map((booth) => (
                <Link
                  key={booth.boothId}
                  to={`/booths/${booth.boothId}`}
                  className="surface flex items-center gap-3 p-5 hover:opacity-80"
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
                    <p className="truncate text-xs text-muted">
                      첫 방문 {new Date(booth.firstVisitedAt).toLocaleString("ko-KR")}
                    </p>
                    {booth.visitCount > 1 && (
                      <div className="mt-1">
                        <Badge tone="primary">{booth.visitCount}회 방문</Badge>
                      </div>
                    )}
                  </div>
                </Link>
              ))}
            </div>
          )}
        </>
      )}
    </PageContainer>
  );
}