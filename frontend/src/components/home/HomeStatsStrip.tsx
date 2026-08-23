import { useEffect, useState } from "react";
import { getPublicEntryStats, type PublicEntryStats } from "../../api/entryStats";

/** 숫자가 차오르는 시간. 너무 길면 읽으려는 순간에도 숫자가 계속 바뀌어 거슬린다. */
const COUNT_UP_MS = 900;

// 히어로 배너와 같은 방식으로 OS "동작 줄이기" 설정을 존중한다(그 화면은 자동 넘김을 멈춘다).
const prefersReducedMotion = () =>
  typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/**
 * 0에서 value까지 점점 느려지며(easeOutCubic) 올라가는 숫자.
 *
 * 동작 줄이기 설정이면 애니메이션을 시작하지도 않고 최종값을 그대로 반환한다 - state를
 * 최종값으로 덮어쓰는 대신 반환값만 바꾸는 이유는, 렌더 직후 다시 렌더하는 낭비를 피하려는 것.
 */
function useCountUp(value: number): number {
  const reduceMotion = prefersReducedMotion();
  const [display, setDisplay] = useState(0);

  useEffect(() => {
    if (reduceMotion) return;
    let frame = 0;
    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / COUNT_UP_MS);
      setDisplay(Math.round(value * (1 - Math.pow(1 - progress, 3))));
      if (progress < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [value, reduceMotion]);

  return reduceMotion ? value : display;
}

function StatCell({ label, value, unit }: { label: string; value: number; unit: string }) {
  const display = useCountUp(value);
  return (
    <div className="px-2 text-center">
      <p className="text-3xl font-black leading-none text-ink sm:text-4xl">
        {display.toLocaleString("ko-KR")}
        <span className="ml-0.5 align-baseline text-base font-extrabold sm:text-lg">{unit}</span>
      </p>
      {/* break-keep: 라벨이 두 줄로 접힐 때 한글 단어 중간에서 끊기지 않게 한다
          ("PETOPIA와 함께한 행사"는 320px 화면에서 한 줄에 안 들어간다). */}
      <p className="mt-2 break-keep text-xs font-bold text-muted sm:text-sm">{label}</p>
    </div>
  );
}

/**
 * 히어로 아래 얇은 숫자 띠. 서비스가 지금까지 쌓아온 실적을 한눈에 보여준다.
 *
 * <p>행사 수만 HomePage가 넘겨준다(공개 행사 목록에서 세면 되는 값이라 API를 더 부르지
 * 않는다). 방문자·반려동물은 이 띠 말고 쓰는 곳이 없어서 여기서 직접 불러온다.
 *
 * <p>셋 중 하나라도 아직 못 받았으면 띠를 그리지 않는다 - 세 칸 중 한 칸이 비거나 "0"으로
 * 남으면 정보가 아니라 고장으로 읽힌다. 숫자가 진짜 0인 것과 못 불러온 것은 다르게 다룬다:
 * 진짜 0은 그대로 "0"으로 보여준다(아직 방문 기록이 없다는 정확한 사실이다).
 *
 * @param openedFairCount 지금까지 열린 행사 수. null이면 아직 못 받았다는 뜻.
 */
export function HomeStatsStrip({ openedFairCount }: { openedFairCount: number | null }) {
  const [stats, setStats] = useState<PublicEntryStats | null>(null);

  useEffect(() => {
    let ignore = false;
    getPublicEntryStats()
      .then((data) => { if (!ignore) setStats(data); })
      // 실패하면 null로 남겨 띠를 감춘다. 0으로 채우면 "방문자가 없다"는 거짓말이 된다.
      .catch(() => {});
    return () => { ignore = true; };
  }, []);

  if (openedFairCount === null || stats === null) return null;

  return (
    <div className="border-b border-line bg-page">
      <div className="page-shell grid grid-cols-3 divide-x divide-line py-6 sm:py-7">
        <StatCell label="PETOPIA와 함께한 행사" value={openedFairCount} unit="개" />
        <StatCell label="찾아온 반려인" value={stats.visitorCount} unit="명" />
        <StatCell label="만나온 반려동물" value={stats.petCount} unit="마리" />
      </div>
    </div>
  );
}
