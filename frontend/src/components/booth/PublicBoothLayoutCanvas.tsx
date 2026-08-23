import { ImageOff, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { getBooth, getConfirmedBooths, type BoothResponse, type ConfirmedBoothResponse } from "../../api/booth";

interface HallGroup {
  hallId: number;
  hallName: string;
  floorPlanImageUrl: string | null;
  slots: ConfirmedBoothResponse[];
}

interface BoothSummary {
  businessName: string;
  imageUrl: string | null;
  slotNumbers: string[];
}

/** 도면 이미지가 없을 때(또는 아직 안 불러왔을 때) 쓰는 기본 비율. BoothCanvas.tsx와 동일. */
const DEFAULT_ASPECT_RATIO = 16 / 10;

/** 정보 패널 크기 추정치(w-64=256px, 내용 포함 최대 높이). 클릭 지점이 화면 끝에 가까울 때
 * 패널이 뷰포트 밖으로 잘리지 않게 clamp하는 데만 쓴다 - 정확한 높이 측정 대신 여유 있게 잡는다. */
const PANEL_WIDTH = 256;
const PANEL_MAX_HEIGHT = 200;
const PANEL_MARGIN = 16;

interface PanelPosition {
  top: number;
  left: number;
}

/** 클릭 좌표(뷰포트 기준) 살짝 아래에 패널을 띄우되, 화면 밖으로 나가지 않게 보정한다. */
function clampPanelPosition(clientX: number, clientY: number): PanelPosition {
  const maxLeft = window.innerWidth - PANEL_WIDTH - PANEL_MARGIN;
  const maxTop = window.innerHeight - PANEL_MAX_HEIGHT - PANEL_MARGIN;
  return {
    left: Math.min(Math.max(clientX, PANEL_MARGIN), Math.max(maxLeft, PANEL_MARGIN)),
    top: Math.min(Math.max(clientY + 8, PANEL_MARGIN), Math.max(maxTop, PANEL_MARGIN)),
  };
}

function groupByHall(rows: ConfirmedBoothResponse[]): HallGroup[] {
  const map = new Map<number, HallGroup>();
  for (const row of rows) {
    let group = map.get(row.hallId);
    if (!group) {
      group = { hallId: row.hallId, hallName: row.hallName, floorPlanImageUrl: row.floorPlanImageUrl, slots: [] };
      map.set(row.hallId, group);
    }
    group.slots.push(row);
  }
  return [...map.values()];
}

// 부스 하나가 슬롯을 여러 개 쓸 수 있어 boothId 기준으로 업체명·이미지·슬롯번호 목록을 묶는다.
function buildBoothSummaries(halls: HallGroup[]): Map<number, BoothSummary> {
  const map = new Map<number, BoothSummary>();
  for (const hall of halls) {
    for (const slot of hall.slots) {
      const existing = map.get(slot.boothId);
      if (existing) {
        existing.slotNumbers.push(slot.slotNumber);
      } else {
        map.set(slot.boothId, { businessName: slot.businessName, imageUrl: slot.imageUrl, slotNumbers: [slot.slotNumber] });
      }
    }
  }
  return map;
}

interface PublicBoothLayoutCanvasProps {
  fairId: number;
}

/**
 * 일반 관람객용 부스 배치도(읽기 전용). 관리자용 BoothCanvas(fair-admin)와 달리 드래그·
 * 리사이즈가 없고, 이미 공개(permitAll) API인 GET /api/fairs/{fairId}/confirmed-booths
 * (Booth 도메인, kimchaerin9670 파트)만 그대로 사용한다 - 백엔드 변경이 필요 없다
 * (petopia-booth-public-view-idea 스킬 참고).
 *
 * 부스를 클릭하면 클릭한 위치 살짝 아래에 업체명·부스번호·한줄소개 패널이 뜬다(화면 끝
 * 근처 클릭 시 잘리지 않게 clampPanelPosition으로 보정). 한줄소개(intro)는
 * confirmed-booths 응답에 없어서, 클릭 시점에 GET /api/booths/{boothId}(역시 이미 공개
 * API)를 한 번 더 불러온다 - 미리 전부 불러오지 않아 N+1 부담이 없고, 같은 부스를 다시
 * 클릭하면 캐시된 값을 재사용한다. 도면(캔버스)의 빈 공간을 클릭하면 패널이 사라지고,
 * 다른 부스를 클릭하면 패널 내용만 교체된다(한 번에 하나만 표시).
 *
 * 도면 비율은 BoothCanvas.tsx(관리자 편집용)와 동일하게, 실제 도면 이미지를 로드해서
 * 그 비율에 캔버스를 맞춘다(2026-08-20) - 고정 16:10 + cover였을 때 실제 이미지 비율이
 * 다르면 잘리거나 슬롯 좌표가 편집 화면과 어긋나 보이던 문제가 있었다. 홀이 여러 개 한
 * 화면에 나오므로(halls.map), 홀마다 도면이 다를 수 있어 hallId를 키로 비율을 따로 캐시한다.
 */
export function PublicBoothLayoutCanvas({ fairId }: PublicBoothLayoutCanvasProps) {
  const [halls, setHalls] = useState<HallGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedBoothId, setSelectedBoothId] = useState<number | null>(null);
  const [panelPosition, setPanelPosition] = useState<PanelPosition | null>(null);
  const [detailsCache, setDetailsCache] = useState<Record<number, BoothResponse>>({});
  const [loadingDetailId, setLoadingDetailId] = useState<number | null>(null);

  // 홀별 도면 이미지의 실제 가로:세로 비율.
  const [loadedRatios, setLoadedRatios] = useState<Record<number, number>>({});
  const loadedRatiosRef = useRef<Record<number, number>>({});
  const pendingHallIdsRef = useRef<Set<number>>(new Set());

  // fairId가 바뀌면 렌더링 중에 즉시 이전 행사의 잔여 상태(에러 메시지, 배치도, 선택된
  // 부스, 소개 캐시)를 지운다 - useEffect 안에서 리셋하면 effect가 도는 한 프레임 동안
  // 이전 행사의 배치도·에러가 그대로 보이는 문제가 있어, prop 변경에 따른 state 리셋은
  // 렌더 단계에서 처리한다(React 공식 문서가 권장하는 방식).
  const [loadedFairId, setLoadedFairId] = useState<number | null>(null);
  if (loadedFairId !== fairId) {
    setLoadedFairId(fairId);
    setHalls([]);
    setError(null);
    setSelectedBoothId(null);
    setPanelPosition(null);
    setLoading(true);
    setDetailsCache({});
    setLoadingDetailId(null);
    setLoadedRatios({});
    loadedRatiosRef.current = {};
    pendingHallIdsRef.current = new Set();
  }

  useEffect(() => {
    let alive = true;
    getConfirmedBooths(fairId)
      .then((rows) => {
        if (alive) setHalls(groupByHall(rows));
      })
      .catch((err: unknown) => {
        if (alive) setError(err instanceof Error ? err.message : "부스 배치도를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [fairId]);

  // 도면 이미지가 로드되면 실제 비율을 읽어서 캐시한다(홀마다 하나씩). loadedRatios(state)를
  // 의존성에 넣으면 이미지 하나 끝날 때마다 이펙트가 다시 돌아 다른 홀의 로딩 중인 이미지까지
  // 취소되고 재시작되는 문제가 있어(코드래빗 리뷰), ref로 이미 처리한/처리 중인 홀을 추적해서
  // halls가 바뀔 때만 새 홀 것만 요청한다.
  useEffect(() => {
    for (const hall of halls) {
      if (!hall.floorPlanImageUrl) continue;
      if (loadedRatiosRef.current[hall.hallId] !== undefined) continue;
      if (pendingHallIdsRef.current.has(hall.hallId)) continue;

      pendingHallIdsRef.current.add(hall.hallId);
      const image = new Image();
      image.onload = () => {
        pendingHallIdsRef.current.delete(hall.hallId);
        if (image.naturalWidth <= 0 || image.naturalHeight <= 0) return;
        const ratio = image.naturalWidth / image.naturalHeight;
        loadedRatiosRef.current = { ...loadedRatiosRef.current, [hall.hallId]: ratio };
        setLoadedRatios((prev) => ({ ...prev, [hall.hallId]: ratio }));
      };
      image.onerror = () => {
        pendingHallIdsRef.current.delete(hall.hallId);
      };
      image.src = hall.floorPlanImageUrl;
    }
  }, [halls]);

  const boothSummaries = useMemo(() => buildBoothSummaries(halls), [halls]);

  function selectBooth(boothId: number, clientX: number, clientY: number) {
    setSelectedBoothId(boothId);
    setPanelPosition(clampPanelPosition(clientX, clientY));
    if (detailsCache[boothId]) return; // 이미 불러온 적 있으면 재사용
    setLoadingDetailId(boothId);
    getBooth(boothId)
      .then((detail) => {
        setDetailsCache((prev) => ({ ...prev, [boothId]: detail }));
      })
      .catch(() => {
        /* 한줄소개 로딩 실패는 조용히 무시한다 - 패널의 기본 정보(업체명·부스번호)는 이미 있다 */
      })
      .finally(() => {
        setLoadingDetailId((prev) => (prev === boothId ? null : prev));
      });
  }

  if (loading) {
    return <div className="grid min-h-40 place-items-center text-sm text-muted">배치도를 불러오는 중이에요...</div>;
  }

  if (error) {
    return <div className="grid min-h-40 place-items-center text-sm text-muted">{error}</div>;
  }

  if (halls.length === 0) {
    return <div className="grid min-h-40 place-items-center text-sm text-muted">아직 공개된 부스 배치가 없어요.</div>;
  }

  const selectedSummary = selectedBoothId != null ? boothSummaries.get(selectedBoothId) : undefined;
  const selectedDetail = selectedBoothId != null ? detailsCache[selectedBoothId] : undefined;

  return (
    <div className="relative flex flex-col gap-6">
      {halls.map((hall) => (
        <div key={hall.hallId}>
          <h3 className="mb-2 text-sm font-bold text-ink">{hall.hallName}</h3>
          <div
            role="presentation"
            onClick={() => {
              setSelectedBoothId(null);
              setPanelPosition(null);
            }}
            className="relative w-full overflow-hidden rounded-card border border-line bg-page"
            style={{
              aspectRatio: loadedRatios[hall.hallId] ?? DEFAULT_ASPECT_RATIO,
              ...(hall.floorPlanImageUrl ? { backgroundImage: `url(${hall.floorPlanImageUrl})`, backgroundSize: "cover" } : {}),
            }}
          >
            {hall.slots.map((slot) => {
              // 배치 좌표가 없는 슬롯은 건너뛴다 - Number(null)이 0이 되어 좌상단에
              // 크기 0짜리 버튼으로 렌더링되는 걸 막는다.
              if (slot.posX == null || slot.posY == null || slot.width == null || slot.height == null) {
                return null;
              }
              const isSelected = selectedBoothId === slot.boothId;
              return (
                <button
                  key={`${slot.boothId}-${slot.slotNumber}`}
                  type="button"
                  aria-label={`${slot.businessName} 부스(${slot.slotNumber})`}
                  onClick={(event) => {
                    event.stopPropagation();
                    selectBooth(slot.boothId, event.clientX, event.clientY);
                  }}
                  className={`absolute flex items-center justify-center overflow-hidden rounded-md border-2 px-1 text-center text-[11px] font-bold transition-colors ${
                    isSelected
                      ? "border-primary bg-primary-soft text-primary-strong ring-2 ring-primary ring-offset-1 ring-offset-page"
                      : "border-primary-strong/60 bg-primary-soft/70 text-primary-strong hover:bg-primary-soft"
                  }`}
                  style={{
                    left: `${Number(slot.posX) * 100}%`,
                    top: `${Number(slot.posY) * 100}%`,
                    width: `${Number(slot.width) * 100}%`,
                    height: `${Number(slot.height) * 100}%`,
                  }}
                >
                  <span className="truncate">{slot.slotNumber}</span>
                </button>
              );
            })}
          </div>
        </div>
      ))}

      {selectedBoothId != null && selectedSummary && panelPosition && (
        // 뷰포트 기준(fixed)으로 띄운다 - 홀이 여러 개일 때 absolute였다면 부모(컴포넌트
        // 루트) 기준이라 마지막 홀 아래로 밀려나 스크롤해야 보였다. 위치는 클릭한 지점
        // 살짝 아래(clampPanelPosition)로, 화면 끝 근처 클릭 시 잘리지 않게 보정한다.
        <div
          className="fixed z-10 w-64 max-w-[calc(100%-2rem)] rounded-card border border-line bg-card p-4 shadow-lg"
          style={{ top: panelPosition.top, left: panelPosition.left }}
        >
          <button
            type="button"
            onClick={() => {
              setSelectedBoothId(null);
              setPanelPosition(null);
            }}
            aria-label="닫기"
            className="absolute right-2 top-2 grid size-7 place-items-center rounded-full text-muted hover:bg-page hover:text-ink"
          >
            <X size={14} />
          </button>
          <div className="flex items-center gap-3 pr-6">
            <div className="grid size-11 shrink-0 place-items-center overflow-hidden rounded-full bg-surface-alt">
              {selectedSummary.imageUrl ? (
                <img src={selectedSummary.imageUrl} alt="" className="size-full object-cover" />
              ) : (
                <ImageOff size={16} className="text-muted" aria-hidden="true" />
              )}
            </div>
            <div className="min-w-0">
              <a
                href={`/booths/${selectedBoothId}`}
                target="_blank"
                rel="noopener noreferrer"
                className="block truncate text-sm font-bold text-ink hover:underline"
              >
                {selectedSummary.businessName}
              </a>
              <p className="truncate text-xs text-muted">부스 {selectedSummary.slotNumbers.join(", ")}</p>
            </div>
          </div>
          <p className="mt-2 text-xs leading-relaxed text-muted">
            {loadingDetailId === selectedBoothId ? "소개 불러오는 중..." : (selectedDetail?.intro ?? "등록된 한줄소개가 없어요.")}
          </p>
        </div>
      )}
    </div>
  );
}
