/// <reference types="vite/client" />

// Vite가 노출하는 환경변수 중 우리가 쓰는 것만 타입으로 못박는다.
// 선언이 없으면 import.meta.env.VITE_* 가 any로 새어나온다.
interface ImportMetaEnv {
  /** 토스페이먼츠 결제창용 "API 개별 연동 키". 공개값이라 번들에 실려도 된다. */
  readonly VITE_TOSS_CLIENT_KEY?: string;
}
