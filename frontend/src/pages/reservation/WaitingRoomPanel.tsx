import { Loader2, Users } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { PageHeader } from "../../components/common/PageHeader";
import {
  getStoredWaitingToken,
  getWaitingTicket,
  issueWaitingTicket,
  leaveWaitingRoom,
  pollIntervalMs,
  storeWaitingToken,
  type WaitingTicket,
} from "../../api/waitingRoom";

interface Props {
  fairId: number;
  fairName: string;
  /** 통과했을 때 호출. 예약 페이지가 중단됐던 요청을 이어서 재시도한다. */
  onAdmitted: () => void;
  /** 대기를 포기했을 때 호출. */
  onCancel: () => void;
}

function formatWait(seconds: number | null): string {
  if (seconds === null) return "잠시 후 안내해 드릴게요";
  if (seconds < 60) return "1분 이내";
  const minutes = Math.ceil(seconds / 60);
  if (minutes < 60) return `약 ${minutes}분`;
  return `약 ${Math.ceil(minutes / 60)}시간`;
}

/**
 * 대기 화면.
 *
 * 예약 요청이 429(R022)로 막혔을 때 나타난다. 토큰을 발급받아 순번을 폴링하다가
 * ADMITTED가 되면 {@link Props.onAdmitted}로 원래 흐름에 돌려보낸다.
 */
export function WaitingRoomPanel({ fairId, fairName, onAdmitted, onCancel }: Props) {
  const [ticket, setTicket] = useState<WaitingTicket | null>(null);
  const [error, setError] = useState<string | null>(null);

  /**
   * 표시용 예상 대기시간은 단조 감소시킨다.
   *
   * 서버 추정치는 최근 승급 속도의 평균이라 오르내릴 수 있는데, 숫자가 늘었다 줄었다 하면
   * 사용자가 새로고침을 반복해서 대기열 자체가 새로운 부하가 된다. 직전 값을 들고 있는
   * 쪽이 화면이므로 여기서 깎는다.
   */
  const shownWaitRef = useRef<number | null>(null);
  const [shownWait, setShownWait] = useState<number | null>(null);

  // onAdmitted가 매 렌더 새 함수여도 폴링 루프가 다시 시작되지 않게 참조로 들고 있는다.
  // 갱신은 렌더 중이 아니라 이펙트에서 한다 - 렌더 중 ref를 건드리면 동시성 렌더링에서
  // 버려진 렌더의 값이 남을 수 있다.
  const admittedRef = useRef(onAdmitted);
  useEffect(() => {
    admittedRef.current = onAdmitted;
  }, [onAdmitted]);

  useEffect(() => {
    let alive = true;
    let timer: number | undefined;

    function apply(next: WaitingTicket) {
      if (!alive) return;
      setTicket(next);
      storeWaitingToken(fairId, next.token);

      if (next.status === "ADMITTED" || next.status === "BYPASSED") {
        admittedRef.current();
        return;
      }

      const raw = next.estimatedWaitSeconds;
      if (raw !== null) {
        const previous = shownWaitRef.current;
        const clamped = previous === null ? raw : Math.min(previous, raw);
        shownWaitRef.current = clamped;
        setShownWait(clamped);
      }

      timer = window.setTimeout(poll, pollIntervalMs(next.ahead));
    }

    /**
     * 발급 응답 전용 처리.
     *
     * 화면을 떠난 뒤 응답이 도착하면 그냥 버릴 수 없다 — 서버에는 이미 내 이름으로 토큰이
     * 만들어져 있어서, 버리고 가면 그 자리(줄 순번이나 활성 슬롯)가 TTL이 끝날 때까지 묶인다.
     */
    function applyIssued(next: WaitingTicket) {
      if (!alive) {
        if (next.token) leaveWaitingRoom(fairId, next.token).catch(() => {});
        return;
      }
      apply(next);
    }

    function fail(err: unknown) {
      if (!alive) return;
      // 폴링이 한 번 실패했다고 대기를 깨지 않는다. 순번을 잃는 것보다 재시도가 낫다.
      console.warn("대기열 조회 실패", err);
      setError("대기 상태를 확인하지 못했어요. 다시 시도하는 중이에요…");
      timer = window.setTimeout(poll, 3000);
    }

    function poll() {
      const token = getStoredWaitingToken(fairId);
      if (!token) {
        issueWaitingTicket(fairId).then(applyIssued).catch(fail);
        return;
      }
      getWaitingTicket(fairId, token)
        .then((next) => {
          setError(null);
          // 서버가 토큰을 모르면(만료·재시작) 새로 줄을 선다.
          if (next.status === "WAITING" && next.position === 0) {
            storeWaitingToken(fairId, null);
            issueWaitingTicket(fairId).then(applyIssued).catch(fail);
            return;
          }
          apply(next);
        })
        .catch(fail);
    }

    poll();
    return () => {
      alive = false;
      if (timer) window.clearTimeout(timer);
    };
  }, [fairId]);

  function handleCancel() {
    const token = getStoredWaitingToken(fairId);
    if (token) {
      // 실패해도 슬롯은 TTL로 회수되므로 결과를 기다리지 않는다.
      leaveWaitingRoom(fairId, token).catch(() => {});
      storeWaitingToken(fairId, null);
    }
    onCancel();
  }

  const ahead = ticket?.ahead ?? 0;

  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader
        eyebrow="대기열"
        title="예매 대기 중이에요"
        description="지금 접속이 몰려 순서대로 입장하고 있어요. 이 화면을 닫지 마세요."
      />

      <Card className="grid place-items-center p-8 text-center">
        {/*
          아이콘 원을 감싸는 회전 링. 순번은 몇 분에 한 칸씩 움직여서 화면이 멈춘 것처럼
          보이는데, 이게 돌고 있으면 아직 진행 중이라는 신호가 된다.
        */}
        <div className="relative mb-4 grid size-14 place-items-center">
          <span
            aria-hidden
            className="absolute inset-0 animate-spin rounded-full border-2 border-leaf-soft border-t-ink"
          />
          <span className="grid size-11 place-items-center rounded-full bg-leaf-soft text-ink">
            <Users size={24} />
          </span>
        </div>
        <p className="text-sm text-muted">{fairName}</p>

        {ticket === null ? (
          <p className="mt-6 flex items-center gap-2 text-sm text-muted">
            <Loader2 aria-hidden size={16} className="animate-spin" />
            대기 순번을 받는 중이에요…
          </p>
        ) : (
          // 스피너는 계속 돌지만 읽어줄 내용은 순번이다. 애니메이션이 아니라 이 블록을 읽게 한다.
          <div role="status">
            <p className="mt-4 text-4xl font-extrabold tabular-nums text-ink">
              {ahead.toLocaleString()}
              <span className="ml-1 text-base font-bold text-muted">명 앞에 있어요</span>
            </p>
            <p className="mt-2 text-sm text-muted">예상 대기 시간 {formatWait(shownWait)}</p>
          </div>
        )}

        {error && (
          <p className="mt-4 flex items-center gap-2 text-sm text-primary-strong">
            <Loader2 aria-hidden size={14} className="animate-spin" />
            {error}
          </p>
        )}

        <p className="mt-6 text-xs leading-5 text-muted">
          새로고침해도 순번은 그대로예요.
          <br />
          다른 탭에서 다시 접속하면 맨 뒤로 가니 이 탭을 유지해 주세요.
        </p>

        <Button variant="ghost" className="mt-5" onClick={handleCancel}>
          대기 취소하기
        </Button>
      </Card>
    </div>
  );
}
