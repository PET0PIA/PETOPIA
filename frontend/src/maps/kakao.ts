/**
 * 카카오맵 JS SDK 로더. 토스 SDK 로더(payments/toss.ts)와 같은 패턴 - Promise를 모듈
 * 스코프에 캐싱해서 여러 컴포넌트가 동시에 마운트돼도 스크립트 태그를 한 번만 추가한다.
 *
 * autoload=false로 스크립트만 받아온 뒤 kakao.maps.load로 초기화 시점을 직접 통제한다 -
 * 스크립트 로드(onload)와 지도 API 내부 리소스 준비 완료 시점이 분리돼 있어서, onload
 * 직후 곧바로 kakao.maps.Map을 쓰면 에러가 날 수 있다.
 */

declare global {
  interface Window {
    kakao: KakaoNamespace;
  }
}

interface KakaoLatLng {
  getLat(): number;
  getLng(): number;
}

interface KakaoMapInstance {
  setCenter(position: KakaoLatLng): void;
}

interface KakaoMarkerInstance {
  setMap(map: KakaoMapInstance | null): void;
}

interface KakaoNamespace {
  maps: {
    load(callback: () => void): void;
    LatLng: new (latitude: number, longitude: number) => KakaoLatLng;
    Map: new (container: HTMLElement, options: { center: KakaoLatLng; level?: number }) => KakaoMapInstance;
    Marker: new (options: { position: KakaoLatLng; map?: KakaoMapInstance }) => KakaoMarkerInstance;
  };
}

export type { KakaoNamespace };

const appkey = import.meta.env.VITE_KAKAO_JS_KEY;

/** 키가 주입됐는지. 화면이 이 값을 보고 지도를 그릴지 결정한다(토스 isTossConfigured와 동일한 목적). */
export function isKakaoMapConfigured(): boolean {
  return Boolean(appkey);
}

let loadPromise: Promise<KakaoNamespace> | null = null;

export function loadKakaoMaps(): Promise<KakaoNamespace> {
  if (!appkey) return Promise.reject(new Error("VITE_KAKAO_JS_KEY가 없어요."));
  if (window.kakao?.maps) return Promise.resolve(window.kakao);
  if (!loadPromise) {
    loadPromise = new Promise<KakaoNamespace>((resolve, reject) => {
      const script = document.createElement("script");
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appkey}&autoload=false`;
      script.async = true;
      script.onload = () => {
        try {
          if (!window.kakao?.maps?.load) {
            reject(new Error("카카오맵 SDK가 올바르게 초기화되지 않았어요."));
            return;
          }
          window.kakao.maps.load(() => resolve(window.kakao));
        } catch (error) {
          reject(error instanceof Error ? error : new Error("카카오맵 SDK 초기화에 실패했어요."));
        }
      };
      script.onerror = () => reject(new Error("카카오맵 SDK 로드에 실패했어요."));
      document.head.appendChild(script);
    }).catch((error: unknown) => {
      // 실패한 Promise를 캐시해두면 일시적 실패가 영구 실패가 된다(getTossPayment와 동일한 이유).
      loadPromise = null;
      throw error;
    });
  }
  return loadPromise;
}
