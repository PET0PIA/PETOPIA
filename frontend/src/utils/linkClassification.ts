// 앱 내부 경로: "/"로 시작하고 그 다음 글자가 "/"나 "\"가 아닌 경우만. 브라우저 URL 파서는
// 맨 앞 두 글자가 "/"와 "\"의 어떤 조합이어도(//, /\, \/, \\) 프로토콜 상대 URL(권한부 파싱)로
// 취급해서, "/\evil.com"도 "//evil.com"과 똑같이 외부 사이트로 새어나간다.
export const INTERNAL_PATH_PATTERN = /^\/(?![/\\])/;
// 지원하는 외부 URL: http/https 뿐(대소문자 무관 - "HTTPS://..."도 허용). 그 외(프로토콜 상대,
// javascript: 등 미지원 스킴)는 링크를 만들지 않는다.
export const EXTERNAL_URL_PATTERN = /^https?:\/\//i;

/** to가 SmartLink로 실제 렌더링 가능한 형식(내부 경로 또는 http/https 외부 URL)인지 미리 확인한다. */
export function isSmartLinkable(to: string): boolean {
  return INTERNAL_PATH_PATTERN.test(to) || EXTERNAL_URL_PATTERN.test(to);
}
