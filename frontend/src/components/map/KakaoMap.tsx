import { useEffect, useRef, useState } from "react";
import { isKakaoMapConfigured, loadKakaoMaps } from "../../maps/kakao";

interface KakaoMapProps {
  latitude: number;
  longitude: number;
  /** 스크린리더용 라벨(장소명). 지도 자체에는 텍스트를 그리지 않는다. */
  label?: string | null;
}

/**
 * 좌표 하나를 중심 마커로 찍는 정적 지도. VITE_KAKAO_JS_KEY가 없거나 SDK 로드가 실패하면
 * 그냥 아무것도 그리지 않는다 - 지도는 상세 페이지의 부가 정보라 실패해도 페이지 전체를
 * 막을 이유가 없다(FairParticipatingBooths가 조회 실패 시 섹션을 숨기는 것과 같은 정책).
 */
export function KakaoMap({ latitude, longitude, label }: KakaoMapProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  // 지연 초기화라 effect 밖(렌더 시점)에 한 번만 평가된다 - effect 안에서 동기적으로
  // setState를 호출하면 안 되므로(react-hooks/set-state-in-effect), 키 미설정 여부는
  // 초기 state 자체에 반영한다.
  const [failed, setFailed] = useState(() => !isKakaoMapConfigured());

  useEffect(() => {
    if (!isKakaoMapConfigured()) return;
    let alive = true;
    loadKakaoMaps()
      .then((kakao) => {
        if (!alive || !containerRef.current) return;
        const center = new kakao.maps.LatLng(latitude, longitude);
        const map = new kakao.maps.Map(containerRef.current, { center, level: 3 });
        new kakao.maps.Marker({ position: center, map });
      })
      .catch(() => {
        if (alive) setFailed(true);
      });
    return () => {
      alive = false;
    };
  }, [latitude, longitude]);

  if (failed) return null;

  return (
    <div
      ref={containerRef}
      role="img"
      aria-label={label ? `${label} 위치 지도` : "행사 장소 지도"}
      className="h-56 w-1/2 overflow-hidden rounded-card border border-line bg-surface-alt"
    />
  );
}
