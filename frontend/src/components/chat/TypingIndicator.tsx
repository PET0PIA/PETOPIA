/**
 * "상담사가 입력 중이에요" 표시.
 *
 * 사용자는 이 화면에서 입력이 잠긴 채 기다리고 있다. 이 표시가 있으면 잠금이 "방치"가 아니라
 * "응답 준비 중"으로 읽힌다 - 기능의 값은 애니메이션이 아니라 그 해석에 있다.
 *
 * 상담사가 누구인지는 드러내지 않는다. 실명·계정은 개인정보이고 사용자에게 필요하지도 않다.
 */
export function TypingIndicator() {
  return (
    <div className="flex justify-start px-4 pb-2" aria-live="polite">
      <div className="flex items-center gap-2 rounded-card bg-surface-alt px-3 py-2">
        {/*
          애니메이션은 장식이 아니라 "지금 벌어지는 일"이라는 신호다. 다만 움직임에 민감한
          사용자를 위해 motion-reduce에서는 멈춘 점으로 남긴다(내용은 그대로 전달된다).
        */}
        <span className="flex gap-1" aria-hidden>
          {[0, 150, 300].map((delay) => (
            <span
              key={delay}
              className="h-1.5 w-1.5 animate-bounce rounded-pill bg-muted motion-reduce:animate-none"
              style={{ animationDelay: `${delay}ms` }}
            />
          ))}
        </span>
        <span className="text-xs text-muted">상담사가 입력 중이에요</span>
      </div>
    </div>
  );
}
