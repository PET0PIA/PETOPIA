import { Lock } from "lucide-react";
import { useEffect, useRef, useState } from "react";

/** 부스 배치 편집 화면에서 다루는 슬롯 하나의 로컬 편집 상태. 저장 전까지는 서버와 동기화되지 않는다. */
export interface DraftSlot {
  /** 화면 내 안정적인 key. 기존 슬롯은 `existing-{boothSlotId}`, 새로 추가한 슬롯은 `new-{...}`. */
  key: string;
  boothSlotId: number | null;
  slotNumber: string;
  /** 0~1 사이의 상대 좌표·크기(부스슬롯 일괄저장 API와 동일한 단위). */
  posX: number;
  posY: number;
  width: number;
  height: number;
  price: number;
  memo: string;
  /** null이 아니면 참가 신청이 걸려 위치·크기·번호·가격 변경 및 삭제가 제한된 슬롯. */
  lockedAt: string | null;
}

export const MIN_SLOT_SIZE = 0.04;

export function clamp(value: number, min: number, max: number) {
  if (max < min) return min;
  return Math.min(Math.max(value, min), max);
}

function snapToStep(value: number, step: number) {
  if (step <= 0) return value;
  return Math.round(value / step) * step;
}

interface BoothCanvasProps {
  slots: DraftSlot[];
  selectedKey: string | null;
  backgroundImageUrl?: string | null;
  /** 격자를 표시할지 여부. 도면 이미지가 있어도 정렬 확인용으로 그 위에 겹쳐 그린다. */
  showGrid?: boolean;
  /** 격자 한 칸의 크기(%). 값이 작을수록 촘촘하다. */
  gridSize?: number;
  /** 확대 배율. 1(기본값)이면 도면 전체가 빈 공간 없이 뷰포트에 꽉 차고, 1보다 크면 확대된 만큼 드래그·스크롤해서 봐야 한다. */
  zoom?: number;
  onSelect: (key: string) => void;
  onGeometryChange: (key: string, patch: Partial<Pick<DraftSlot, "posX" | "posY" | "width" | "height">>) => void;
}

/** 도면 이미지가 없을 때(또는 아직 안 불러왔을 때) 쓰는 기본 비율. */
const DEFAULT_ASPECT_RATIO = 16 / 10;

export function BoothCanvas({ slots, selectedKey, backgroundImageUrl, showGrid = true, gridSize = 5, zoom = 1, onSelect, onGeometryChange }: BoothCanvasProps) {
  const outerRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  // zoom=1(기본 배율)일 때의 캔버스 너비. 확대 배율을 픽셀 크기로 환산할 때만 쓴다.
  const [naturalWidth, setNaturalWidth] = useState(0);
  // 도면 이미지의 실제 가로:세로 비율. 이미지를 새로 불러올 때만 갱신한다(setState는 항상
  // 비동기 onload 콜백 안에서만 호출해, effect 본문에서 곧바로 setState하지 않게 한다).
  const [loadedImage, setLoadedImage] = useState<{ url: string; ratio: number } | null>(null);
  const [isPanning, setIsPanning] = useState(false);
  const isZoomed = zoom !== 1;

  useEffect(() => {
    const el = outerRef.current;
    if (!el) return;
    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width;
      if (width) setNaturalWidth(width);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!backgroundImageUrl) return;
    let ignore = false;
    const image = new Image();
    image.onload = () => {
      if (ignore || image.naturalWidth <= 0 || image.naturalHeight <= 0) return;
      setLoadedImage({ url: backgroundImageUrl, ratio: image.naturalWidth / image.naturalHeight });
    };
    image.src = backgroundImageUrl;
    return () => { ignore = true; };
  }, [backgroundImageUrl]);

  // 도면 이미지가 있고 그 이미지의 비율을 이미 읽었으면 캔버스를 그 비율에 맞춰서
  // background-size: cover로 인한 잘림을 없앤다(비율이 항상 일치하니 크롭이 생기지 않는다).
  // 이미지가 바뀌었는데 아직 새 비율을 못 읽었으면(url 불일치) 기본 비율로 되돌아간다.
  const aspectRatio = backgroundImageUrl && loadedImage?.url === backgroundImageUrl ? loadedImage.ratio : DEFAULT_ASPECT_RATIO;
  const naturalHeight = naturalWidth / aspectRatio;
  const canvasWidthPx = naturalWidth * zoom;
  const canvasHeightPx = naturalHeight * zoom;

  /** 확대 상태에서 슬롯이 아닌 빈 캔버스를 드래그하면 스크롤 위치를 옮긴다(패닝). 기존 스크롤바는 그대로 남겨둔다. */
  function beginPan(event: React.PointerEvent) {
    if (!isZoomed) return;
    if (event.target !== event.currentTarget) return;
    const outer = outerRef.current;
    if (!outer) return;
    event.preventDefault();

    const startX = event.clientX;
    const startY = event.clientY;
    const startScrollLeft = outer.scrollLeft;
    const startScrollTop = outer.scrollTop;
    setIsPanning(true);

    function handleMove(moveEvent: PointerEvent) {
      outer!.scrollLeft = startScrollLeft - (moveEvent.clientX - startX);
      outer!.scrollTop = startScrollTop - (moveEvent.clientY - startY);
    }
    function handleUp() {
      setIsPanning(false);
      window.removeEventListener("pointermove", handleMove);
      window.removeEventListener("pointerup", handleUp);
    }
    window.addEventListener("pointermove", handleMove);
    window.addEventListener("pointerup", handleUp);
  }

  function beginMove(event: React.PointerEvent, slot: DraftSlot) {
    onSelect(slot.key);
    if (slot.lockedAt) return;
    event.preventDefault();

    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const startX = event.clientX;
    const startY = event.clientY;
    const { posX: startPosX, posY: startPosY, width, height } = slot;

    function handleMove(moveEvent: PointerEvent) {
      const dxFrac = (moveEvent.clientX - startX) / rect!.width;
      const dyFrac = (moveEvent.clientY - startY) / rect!.height;
      const maxPosX = Math.max(0, 1 - width);
      const maxPosY = Math.max(0, 1 - height);
      let nextPosX = clamp(startPosX + dxFrac, 0, maxPosX);
      let nextPosY = clamp(startPosY + dyFrac, 0, maxPosY);
      // 격자가 켜져 있으면 칸에 자동으로 맞춘다. Alt를 누른 채 드래그하면 스냅 없이 자유롭게 움직인다.
      if (showGrid && !moveEvent.altKey) {
        const step = gridSize / 100;
        nextPosX = clamp(snapToStep(nextPosX, step), 0, maxPosX);
        nextPosY = clamp(snapToStep(nextPosY, step), 0, maxPosY);
      }
      onGeometryChange(slot.key, { posX: nextPosX, posY: nextPosY });
    }
    function handleUp() {
      window.removeEventListener("pointermove", handleMove);
      window.removeEventListener("pointerup", handleUp);
    }
    window.addEventListener("pointermove", handleMove);
    window.addEventListener("pointerup", handleUp);
  }

  function beginResize(event: React.PointerEvent, slot: DraftSlot) {
    if (slot.lockedAt) return;
    event.preventDefault();
    event.stopPropagation();
    onSelect(slot.key);

    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const startX = event.clientX;
    const startY = event.clientY;
    const { posX, posY, width: startWidth, height: startHeight } = slot;

    function handleMove(moveEvent: PointerEvent) {
      const dxFrac = (moveEvent.clientX - startX) / rect!.width;
      const dyFrac = (moveEvent.clientY - startY) / rect!.height;
      const maxWidth = Math.max(MIN_SLOT_SIZE, 1 - posX);
      const maxHeight = Math.max(MIN_SLOT_SIZE, 1 - posY);
      let nextWidth = clamp(startWidth + dxFrac, MIN_SLOT_SIZE, maxWidth);
      let nextHeight = clamp(startHeight + dyFrac, MIN_SLOT_SIZE, maxHeight);
      if (showGrid && !moveEvent.altKey) {
        const step = gridSize / 100;
        nextWidth = clamp(snapToStep(nextWidth, step), MIN_SLOT_SIZE, maxWidth);
        nextHeight = clamp(snapToStep(nextHeight, step), MIN_SLOT_SIZE, maxHeight);
      }
      onGeometryChange(slot.key, { width: nextWidth, height: nextHeight });
    }
    function handleUp() {
      window.removeEventListener("pointermove", handleMove);
      window.removeEventListener("pointerup", handleUp);
    }
    window.addEventListener("pointermove", handleMove);
    window.addEventListener("pointerup", handleUp);
  }

  return (
    <div
      ref={outerRef}
      className="w-full overflow-auto rounded-card border border-line bg-page"
      style={isZoomed ? { height: naturalHeight || undefined } : { aspectRatio }}
    >
      <div
        ref={containerRef}
        onPointerDown={beginPan}
        className={`relative select-none overflow-hidden bg-page ${isZoomed ? (isPanning ? "cursor-grabbing" : "cursor-grab") : ""}`}
        style={{
          width: isZoomed ? canvasWidthPx || "100%" : "100%",
          height: isZoomed ? canvasHeightPx || "100%" : "100%",
          backgroundImage: backgroundImageUrl ? `url(${backgroundImageUrl})` : undefined,
          backgroundSize: backgroundImageUrl ? "cover" : undefined,
        }}
      >
        {showGrid && (
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0"
            style={{
              backgroundImage:
                "linear-gradient(to right, rgba(48, 44, 40, 0.18) 1px, transparent 1px), linear-gradient(to bottom, rgba(48, 44, 40, 0.18) 1px, transparent 1px)",
              backgroundSize: `${gridSize}% ${gridSize}%`,
            }}
          />
        )}
        {slots.map((slot) => {
          const isSelected = slot.key === selectedKey;
          const isLocked = slot.lockedAt !== null;
          return (
            <div
              key={slot.key}
              role="button"
              tabIndex={0}
              aria-label={`부스 슬롯 ${slot.slotNumber || "(번호 미입력)"}`}
              onPointerDown={(event) => beginMove(event, slot)}
              onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") onSelect(slot.key); }}
              className={`absolute flex flex-col items-center justify-center overflow-hidden rounded-md border-2 px-1 text-center text-[11px] font-bold transition-colors ${
                isLocked
                  ? "cursor-not-allowed border-muted/60 bg-muted/15 text-muted"
                  : "cursor-grab border-primary-strong/60 bg-primary-soft text-primary-strong active:cursor-grabbing"
              } ${isSelected ? "ring-2 ring-primary ring-offset-1 ring-offset-page" : ""}`}
              style={{
                left: `${slot.posX * 100}%`,
                top: `${slot.posY * 100}%`,
                width: `${slot.width * 100}%`,
                height: `${slot.height * 100}%`,
              }}
            >
              {isLocked && <Lock size={11} className="mb-0.5" />}
              <span className="truncate">{slot.slotNumber || "번호 없음"}</span>
              {!isLocked && (
                <div
                  role="presentation"
                  onPointerDown={(event) => beginResize(event, slot)}
                  className="absolute bottom-0 right-0 size-3 cursor-nwse-resize rounded-tl bg-primary-strong/70"
                />
              )}
            </div>
          );
        })}
        {slots.length === 0 && (
          <div className="absolute inset-0 grid place-items-center text-sm text-muted">
            아직 배치된 부스 슬롯이 없어요. 오른쪽에서 슬롯을 추가해 보세요.
          </div>
        )}
      </div>
    </div>
  );
}
