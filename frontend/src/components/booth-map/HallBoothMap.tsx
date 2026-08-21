import { useEffect, useState } from "react";
import { Lock } from "lucide-react";

export type HallBoothMapTone = "leaf" | "sun" | "neutral";

const toneClass: Record<HallBoothMapTone, string> = {
  leaf: "border-leaf/60 bg-leaf-soft text-ink",
  sun: "border-sun/60 bg-sun-soft text-ink",
  neutral: "border-muted/60 bg-muted/15 text-muted",
};

/** 도면 이미지가 없을 때(또는 아직 안 불러왔을 때) 쓰는 기본 비율. BoothCanvas.tsx와 동일. */
const DEFAULT_ASPECT_RATIO = 16 / 10;

export interface HallBoothMapSlot {
  boothSlotsId: number;
  slotNumber: string;
  posX: number;
  posY: number;
  width: number;
  height: number;
  /** 슬롯 박스 안에 보여줄 보조 텍스트 (가격, 확정 업체명 등 페이지마다 다름) */
  caption?: string;
  /** 색상 톤. 호출부가 상태(선택 가능/대기/확정 등)에 맞게 직접 지정한다 */
  tone: HallBoothMapTone;
  /** true면 클릭을 막는다(색상과는 별개 - 예: 확정 대기/완료 슬롯은 신청서 제출 페이지에서 선택 불가) */
  locked?: boolean;
  /** true면 선택된 상태(테두리 강조)로 그린다 */
  selected?: boolean;
}

interface HallBoothMapProps {
  hallName: string;
  backgroundImageUrl: string | null;
  slots: HallBoothMapSlot[];
  /** 넘기면 슬롯이 클릭 가능해진다(신청서 제출 페이지의 선택, 확정부스안내판의 부스 상세 이동 등) */
  onSlotClick?: (boothSlotId: number) => void;
}

/**
 * 홀 하나의 배치도를 그리는 공용 읽기 전용 컴포넌트. 모집공고 상세/확정부스안내판/신청서
 * 제출 페이지가 다 같이 쓴다. fair-admin의 BoothCanvas(관리자 편집용)와 같은 0~1 비율
 * 좌표를 쓰므로, 캔버스 비율도 BoothCanvas와 똑같이 실제 도면 이미지 비율에 맞춰야
 * 슬롯 위치가 편집 화면이랑 어긋나지 않는다(2026-08-20, 도면 담당자가 BoothCanvas에
 * 적용한 것과 동일한 방식).
 */
export function HallBoothMap({ hallName, backgroundImageUrl, slots, onSlotClick }: HallBoothMapProps) {
  // 도면 이미지의 실제 가로:세로 비율. 이미지를 새로 불러올 때만 갱신한다.
  const [loadedImage, setLoadedImage] = useState<{ url: string; ratio: number } | null>(null);

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

  const aspectRatio = backgroundImageUrl && loadedImage?.url === backgroundImageUrl ? loadedImage.ratio : DEFAULT_ASPECT_RATIO;

  return (
    <div>
      <h3 className="mb-2 text-sm font-bold text-ink">{hallName}</h3>
      <div
        className="relative w-full overflow-hidden rounded-card border border-line bg-page"
        style={{
          aspectRatio,
          ...(backgroundImageUrl ? { backgroundImage: `url(${backgroundImageUrl})`, backgroundSize: "cover" } : {}),
        }}
      >
        {!backgroundImageUrl && (
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0"
            style={{
              backgroundImage:
                "linear-gradient(to right, rgba(48, 44, 40, 0.18) 1px, transparent 1px), linear-gradient(to bottom, rgba(48, 44, 40, 0.18) 1px, transparent 1px)",
              backgroundSize: "5% 5%",
            }}
          />
        )}
        {slots.map((slot) => {
          const clickable = Boolean(onSlotClick) && !slot.locked;
          const Tag = clickable ? "button" : "div";
          return (
            <Tag
              key={slot.boothSlotsId}
              type={clickable ? "button" : undefined}
              disabled={clickable ? false : undefined}
              onClick={clickable ? () => onSlotClick?.(slot.boothSlotsId) : undefined}
              aria-label={`부스 슬롯 ${slot.slotNumber}${slot.caption ? `, ${slot.caption}` : ""}`}
              className={`absolute flex flex-col items-center justify-center overflow-hidden rounded-md border-2 px-1 text-center font-bold transition-colors [container-type:size] ${toneClass[slot.tone]} ${
                slot.locked ? "cursor-not-allowed" : clickable ? "cursor-pointer hover:opacity-80" : ""
              } ${slot.selected ? "ring-2 ring-primary ring-offset-1 ring-offset-page" : ""}`}
              style={{
                left: `${slot.posX * 100}%`,
                top: `${slot.posY * 100}%`,
                width: `${slot.width * 100}%`,
                height: `${slot.height * 100}%`,
              }}
            >
              {slot.locked && <Lock size={11} className="mb-0.5" />}
              <span className="truncate leading-tight [font-size:clamp(7px,26cqmin,11px)]">{slot.slotNumber}</span>
              {slot.caption && (
                <span className="w-full whitespace-pre-line break-words text-center font-normal leading-tight opacity-80 [font-size:clamp(6px,18cqmin,10px)]">
                  {slot.caption}
                </span>
              )}
            </Tag>
          );
        })}
      </div>
    </div>
  );
}