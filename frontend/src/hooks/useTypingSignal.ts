import { useCallback, useEffect, useRef } from "react";
import { sendTypingHeartbeat, stopTypingSignal } from "../api/adminChat";

/** 하트비트 주기. 서버 TTL(6초)의 절반이라 한 번 유실돼도 표시가 끊기지 않는다. */
const HEARTBEAT_INTERVAL_MS = 3000;

/** 이 시간 동안 키 입력이 없으면 "쓰다 말았다"로 보고 표시를 끈다. */
const IDLE_STOP_MS = 5000;

/**
 * 상담사 입력 중 신호.
 *
 * 중단 신호를 확실히 보내는 것이 이 훅의 핵심이다. 빠뜨리면 고객 화면에 "입력 중"이 남는데,
 * 그건 곧 답변이 온다는 약속처럼 읽혀서 오히려 없느니만 못하다. 그래서 세 겹으로 막는다.
 *   1) 입력이 멈추면(5초) 중단
 *   2) 대화를 바꾸거나 화면을 떠나면 중단(cleanup)
 *   3) 이 둘이 다 실패해도 서버 TTL(6초)이 끈다
 */
export function useTypingSignal(conversationId: number | null) {
  const lastSentAtRef = useRef(0);
  const idleTimerRef = useRef<number | undefined>(undefined);
  const activeRef = useRef(false);

  const stop = useCallback(() => {
    if (idleTimerRef.current) window.clearTimeout(idleTimerRef.current);
    idleTimerRef.current = undefined;
    if (activeRef.current && conversationId != null) {
      activeRef.current = false;
      stopTypingSignal(conversationId);
    }
  }, [conversationId]);

  /** 키 입력마다 호출한다. 실제 전송은 3초에 한 번으로 조인다. */
  const onTyping = useCallback(() => {
    if (conversationId == null) return;

    const now = Date.now();
    if (now - lastSentAtRef.current >= HEARTBEAT_INTERVAL_MS) {
      lastSentAtRef.current = now;
      activeRef.current = true;
      sendTypingHeartbeat(conversationId);
    }

    if (idleTimerRef.current) window.clearTimeout(idleTimerRef.current);
    idleTimerRef.current = window.setTimeout(stop, IDLE_STOP_MS);
  }, [conversationId, stop]);

  // 대화를 바꾸거나 화면을 떠날 때 반드시 끈다. 이게 없으면 다른 대화로 넘어간 뒤에도
  // 이전 고객 화면에 "입력 중"이 남는다.
  useEffect(() => stop, [stop]);

  return { onTyping, stopTyping: stop };
}
