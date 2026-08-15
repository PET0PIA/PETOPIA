import { apiClient } from "./client";

/**
 * 대기열(Waiting Room) API.
 *
 * 오픈 직후 유입이 몰리는 행사에만 켜지는 기능이라, 평소에는 서버가 status "BYPASSED"를
 * 돌려주고 화면은 대기열을 전혀 인지하지 않는다.
 */

/** 보호 대상 API 호출에 붙이는 대기 토큰 헤더. 백엔드 WaitingRoomInterceptor가 읽는다. */
export const WAITING_TOKEN_HEADER = "X-Waiting-Token";

/** 대기열을 통과하지 못했을 때 서버가 주는 에러 코드(HTTP 429). */
export const WAITING_ROOM_REQUIRED_CODE = "R022";

export type WaitingStatus = "WAITING" | "ADMITTED" | "BYPASSED";

export interface WaitingTicket {
  token: string | null;
  status: WaitingStatus;
  /** 내 대기 순번(1부터). 통과·미적용이면 0. */
  position: number;
  /** 내 앞에 남은 인원. 통과·미적용이면 0. */
  ahead: number;
  /** 예상 대기 시간(초). 승급 이력이 없어 추정 불가면 null. */
  estimatedWaitSeconds: number | null;
  /** 통과한 슬롯의 만료 시각. 대기 중이면 null. */
  expiresAt: string | null;
}

/**
 * 대기 토큰은 sessionStorage에 둔다.
 *
 * localStorage가 아닌 이유: 탭 단위로 줄을 서는 게 맞다. 여러 탭이 같은 토큰을 공유하면
 * 한 탭에서 이탈했을 때 다른 탭의 자리까지 사라진다. 새로고침에는 살아남아야 순번을
 * 잃지 않으므로 메모리 변수도 아니다.
 */
function tokenKey(fairId: number) {
  return `waiting-token:${fairId}`;
}

/**
 * sessionStorage가 막혀 있을 때(시크릿 모드·서드파티 컨텍스트 차단 등) 쓰는 대비책.
 *
 * 이게 없으면 저장이 조용히 실패하고, 예약 요청에 토큰이 안 붙어 R022로 영원히 막힌다 —
 * 대기열을 켠 행사에서는 예약 자체가 불가능해진다. 새로고침에는 못 살아남지만
 * (그건 sessionStorage가 하던 일이다) 최소한 이번 페이지에서는 예약을 끝낼 수 있다.
 */
const memoryTokens = new Map<number, string>();

export function getStoredWaitingToken(fairId: number): string | null {
  try {
    const stored = window.sessionStorage.getItem(tokenKey(fairId));
    if (stored) return stored;
  } catch {
    // 막혀 있어도 대기열 자체는 동작해야 한다. 아래 메모리 값으로 이어간다.
  }
  return memoryTokens.get(fairId) ?? null;
}

export function storeWaitingToken(fairId: number, token: string | null) {
  if (token) memoryTokens.set(fairId, token);
  else memoryTokens.delete(fairId);
  try {
    if (token) window.sessionStorage.setItem(tokenKey(fairId), token);
    else window.sessionStorage.removeItem(tokenKey(fairId));
  } catch {
    /* 저장은 못 했지만 memoryTokens에는 담겼다. 새로고침까지는 못 버틴다. */
  }
}

/** 보호 대상 요청에 붙일 헤더. 토큰이 없으면 빈 객체라 평소 요청 모양이 그대로 유지된다. */
export function waitingTokenHeader(fairId: number): Record<string, string> {
  const token = getStoredWaitingToken(fairId);
  return token ? { [WAITING_TOKEN_HEADER]: token } : {};
}

/** 대기 토큰을 발급받는다. 빈 슬롯이 있으면 발급과 동시에 ADMITTED로 돌아온다. */
export function issueWaitingTicket(fairId: number) {
  return apiClient.post<WaitingTicket>(`/api/v1/fairs/${fairId}/waiting-room/tickets`);
}

/** 순번을 조회한다. 서버는 이 호출에 승급 처리를 함께 얹는다. */
export function getWaitingTicket(fairId: number, token: string) {
  return apiClient.get<WaitingTicket>(
    `/api/v1/fairs/${fairId}/waiting-room/tickets/${encodeURIComponent(token)}`,
  );
}

/** 대기를 포기한다. 슬롯을 즉시 반납해 뒷사람이 TTL만큼 기다리지 않아도 되게 한다. */
export function leaveWaitingRoom(fairId: number, token: string) {
  return apiClient.delete<void>(
    `/api/v1/fairs/${fairId}/waiting-room/tickets/${encodeURIComponent(token)}`,
  );
}

/**
 * 볼일이 끝났을 때 슬롯을 돌려준다.
 *
 * 통과한 슬롯은 결제 대기 시간을 덮으려고 12분간 유지된다. 그래서 예약이 끝난 뒤에도
 * 반납하지 않으면 그 자리가 12분 내내 묶여서, 오픈 러시에서 뒷사람 유입이 그만큼 늦어진다.
 * 부하테스트에서 확인된 실제 손실이다(loadtest/README.md).
 *
 * 반대로 <b>결제로 넘어가는 흐름에서는 절대 부르면 안 된다.</b> 결제 중에 슬롯을 놓으면
 * 결제 API 게이트를 다시 통과하지 못한다. 유료 예약은 결제가 끝난 뒤에 반납한다.
 *
 * 실패는 무시한다. 반납이 안 돼도 TTL로 회수되므로, 이걸로 사용자 흐름을 막을 이유가 없다.
 */
export function releaseWaitingSlot(fairId: number | null | undefined) {
  if (fairId == null) return;
  const token = getStoredWaitingToken(fairId);
  if (!token) return;
  storeWaitingToken(fairId, null);
  void leaveWaitingRoom(fairId, token).catch(() => {});
}

/**
 * 순번에 비례해 폴링 간격을 늘린다.
 *
 * 전원이 1초 간격으로 폴링하면 대기열 자체가 새로운 부하가 된다. 뒤에 있을수록
 * 순번이 바뀌는 속도도 느리므로 자주 볼 이유가 없다.
 */
export function pollIntervalMs(ahead: number): number {
  if (ahead > 1000) return 5000;
  if (ahead > 100) return 2000;
  return 1000;
}
