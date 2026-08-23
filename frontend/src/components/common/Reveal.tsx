import { useEffect, useRef, useState, type ReactNode } from "react";

/**
 * 스크롤해서 화면에 들어오는 순간 한 번만 "아래에서 살짝 올라오며" 나타나는 래퍼.
 *
 * 애니메이션은 히어로 배너가 쓰는 heroTextIn(styles/theme.css)을 그대로 재사용한다 -
 * 홈 안에서 등장 움직임이 두 가지로 갈리지 않게 하려는 것이고, 새 keyframes를 만들 이유도 없다.
 *
 * <p>한 번 보이면 관찰을 끊는다(disconnect). 스크롤을 올렸다 내릴 때마다 다시 사라졌다
 * 나타나면 읽던 내용이 깜빡여서 오히려 방해가 된다.
 *
 * <p>움직임에 민감한 사용자(OS "동작 줄이기")는 애니메이션 없이 처음부터 보이게 둔다 -
 * opacity-0으로 숨겨만 두고 애니메이션을 끄면 내용이 영원히 안 보이므로, 숨기는 쪽도
 * motion-reduce로 함께 해제해야 한다.
 */
export function Reveal({ children, className = "", delayMs = 0 }: { children: ReactNode; className?: string; delayMs?: number }) {
  // IntersectionObserver가 없는 환경(구형 브라우저 등)에서는 효과를 포기하고 처음부터 보여준다.
  // effect 안에서 켜지 않고 초기값으로 정하는 이유: 렌더 직후 setState로 다시 그리는 것을 피한다.
  const [shown, setShown] = useState(() => typeof IntersectionObserver === "undefined");
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    if (typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        setShown(true);
        observer.disconnect();
      },
      // 화면 아래쪽 10%에 걸치기 전까지는 기다린다 - 바닥에 살짝 걸친 순간 시작하면
      // 사용자가 그 섹션에 도착했을 때 이미 애니메이션이 끝나 있어 효과가 보이지 않는다.
      { rootMargin: "0px 0px -10% 0px", threshold: 0.05 },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return (
    <div
      ref={ref}
      style={shown && delayMs > 0 ? { animationDelay: `${delayMs}ms` } : undefined}
      className={`${shown ? "animate-[heroTextIn_700ms_ease-out_both] motion-reduce:animate-none" : "opacity-0 motion-reduce:opacity-100"} ${className}`}
    >
      {children}
    </div>
  );
}
