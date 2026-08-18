/// <reference types="vite/client" />

// Vite가 노출하는 환경변수 중 우리가 쓰는 것만 타입으로 못박는다.
// 선언이 없으면 import.meta.env.VITE_* 가 any로 새어나온다.
interface ImportMetaEnv {
  /** 토스페이먼츠 결제창용 "API 개별 연동 키". 공개값이라 번들에 실려도 된다. */
  readonly VITE_TOSS_CLIENT_KEY?: string;
  /** 카카오맵 JS SDK 키. 도메인 화이트리스트로 보호되는 공개값이다. */
  readonly VITE_KAKAO_JS_KEY?: string;
}
