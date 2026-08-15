import { useEffect, useState } from "react";
import { ArrowRight } from "lucide-react";
import { getActiveBanners, type Banner } from "../../api/banner";
import { SmartLink } from "../common/SmartLink";

const SLIDE_MS = 3000;
const prefersReducedMotion = () => typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

export function HeroSection() {
  const [banners, setBanners] = useState<Banner[] | null>(null);
  const [index, setIndex] = useState(0);
  // 움직임에 민감한 사용자는 자동 전환을 멈춘 상태로 시작. 배너를 클릭하면 정지/재생 토글.
  const [paused, setPaused] = useState(prefersReducedMotion);

  useEffect(() => {
    let ignore = false;
    getActiveBanners()
      .then((data) => { if (!ignore) setBanners(data); })
      .catch(() => { if (!ignore) setBanners([]); });
    return () => { ignore = true; };
  }, []);

  useEffect(() => {
    if (paused || !banners || banners.length <= 1) return;
    const timer = setTimeout(() => setIndex((i) => (i + 1) % banners.length), SLIDE_MS);
    return () => clearTimeout(timer);
  }, [index, paused, banners]);

  // OS "동작 줄이기" 설정이 도중에 켜지면 자동 전환을 멈춘다. (꺼져도 사용자의 정지 선택은 유지)
  useEffect(() => {
    const mql = window.matchMedia("(prefers-reduced-motion: reduce)");
    const onChange = (event: MediaQueryListEvent) => {
      if (event.matches) setPaused(true);
    };
    mql.addEventListener("change", onChange);
    return () => mql.removeEventListener("change", onChange);
  }, []);

  // 로딩 중에는 실제 히어로와 비슷한 높이의 빈 영역을 잡아둬서, 데이터가 도착했을 때
  // 레이아웃이 출렁이지 않게 한다. 노출 중인 배너가 0개로 확정되면 섹션 자체를 그리지 않는다.
  if (!banners) return <section className="h-[26rem]" aria-hidden="true" />;
  if (banners.length === 0) return null;

  const slide = banners[Math.min(index, banners.length - 1)];
  const hasPrimaryCta = Boolean(slide.linkLabel && slide.linkUrl);
  const hasSecondaryCta = Boolean(slide.link2Label && slide.link2Url);

  return (
    <section className="relative overflow-hidden transition-colors duration-500 motion-reduce:transition-none" style={{ backgroundColor: slide.bgColor ?? "var(--color-point-yellow)" }}>
      {/* 배너 아무 곳이나 클릭하면 자동 넘김 정지/재생. 버튼 UI는 안 보이지만 키보드/스크린리더로 조작 가능 */}
      <button type="button" onClick={() => setPaused((value) => !value)} aria-label={paused ? "배너 자동 넘김 재생" : "배너 자동 넘김 일시정지"} className="absolute inset-0 z-0 cursor-default" />
      <div className="page-shell pointer-events-none relative z-10 grid items-center gap-8 pt-10 sm:pt-14 md:grid-cols-[0.75fr_1.25fr]">
        <div key={index} className="animate-[heroTextIn_600ms_ease-out_both] py-3 motion-reduce:animate-none">
          {slide.eyebrow && <p className="mb-3 text-sm font-bold text-ink">{slide.eyebrow}</p>}
          <h1 className="whitespace-pre-line text-3xl font-black leading-[1.2] tracking-tight text-ink sm:text-4xl">{slide.title}</h1>
          {slide.subtitle && <p className="mt-5 max-w-md whitespace-pre-line text-sm leading-7 text-ink/70 sm:text-base">{slide.subtitle}</p>}
          {(hasPrimaryCta || hasSecondaryCta) && (
            <div className="pointer-events-auto mt-8 flex flex-wrap gap-3">
              {hasPrimaryCta && (
                <SmartLink to={slide.linkUrl!} target={slide.linkTarget} className="inline-flex min-h-11 items-center gap-2 rounded-button bg-primary px-5 text-sm font-bold text-white transition hover:opacity-90">
                  {slide.linkLabel}
                  <ArrowRight size={16} />
                </SmartLink>
              )}
              {hasSecondaryCta && (
                <SmartLink to={slide.link2Url!} target={slide.link2Target} className="inline-flex min-h-11 items-center gap-2 rounded-button border border-ink/15 bg-card px-5 text-sm font-bold text-ink transition hover:bg-white">
                  {slide.link2Label}
                </SmartLink>
              )}
            </div>
          )}
        </div>
        <div className="relative mx-auto flex h-64 w-full items-center justify-center sm:h-80 md:h-[26rem] md:justify-end lg:h-[28rem]">
          <img key={index} src={slide.imageKey} alt="" className="max-h-full max-w-full animate-[heroPosterIn_600ms_ease-out_both] object-contain motion-reduce:animate-none" />
        </div>
      </div>
      {banners.length > 1 && (
        <div className="page-shell pointer-events-none relative z-10 flex items-center justify-center gap-2 pb-6 pt-2">
          {banners.map((banner, i) => {
            const isActive = i === index;
            return (
              <button key={banner.bannerId} type="button" onClick={() => setIndex(i)} aria-label={`${i + 1}번째 배너${isActive ? " (현재)" : " 보기"}`} aria-current={isActive ? "true" : undefined} className={`pointer-events-auto rounded-pill transition ${isActive ? "relative h-1.5 w-10 overflow-hidden bg-ink/20" : "size-1.5 bg-ink/30 hover:bg-ink/50"}`}>
                {isActive && <span key={index} className="absolute inset-y-0 left-0 bg-ink" style={{ animation: `heroProgress ${SLIDE_MS}ms linear forwards`, animationPlayState: paused ? "paused" : "running" }} />}
              </button>
            );
          })}
        </div>
      )}
    </section>
  );
}
