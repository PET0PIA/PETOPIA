import { useEffect, useState } from "react";
import { ArrowRight } from "lucide-react";
import { getActiveBanners, type Banner } from "../../api/banner";
import { SmartLink } from "../common/SmartLink";
import homeHeroPets from "../../assets/home_banner_basic.png";

const SLIDE_MS = 3000;
const prefersReducedMotion = () => typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

// 관리자가 등록한 배너가 하나도 없을 때 대신 보여줄 기본 배너. bannerId는 실제 배너와
// 겹치지 않는 음수를 써서(DB는 AUTO_INCREMENT라 양수만 나옴) 자연스럽게 구분되게 한다.
const FALLBACK_BANNER: Banner = {
  bannerId: -1,
  title: "우리 브랜드를\nPETOPIA에 소개해보세요",
  eyebrow: "광고 문의",
  subtitle: "반려동물을 사랑하는 방문자들에게 홈 화면 배너로 브랜드를 알릴 수 있어요.",
  imageKey: homeHeroPets,
  linkUrl: "/advertising",
  linkTarget: "SELF",
  linkLabel: "광고 문의하기",
  link2Label: null,
  link2Url: null,
  link2Target: null,
  // 노출할 배너가 하나도 없을 때만 뜨는 화면이라, 튀는 색 대신 따뜻한 크림색으로 차분하게 둔다.
  // 일러스트에 흰 털 아이들이 많지만 외곽선이 검정이라 이 밝은 배경 위에서도 형태가 뭉개지지
  // 않고, 배경이 밝아 본문 글자(검정 계열) 대비도 넉넉하다.
  bgColor: "var(--color-point-cream)",
  sortOrder: 0,
  active: true,
  startedAt: null,
  endedAt: null,
  createdAt: "",
};

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
  // 레이아웃이 출렁이지 않게 한다. 노출 중인 배너가 0개로 확정되면 광고 문의로 이어지는
  // 기본 배너를 대신 보여준다 - 완전히 빈 화면보다는 광고 영업 기회로 쓰는 편이 낫다.
  if (!banners) return <section className="h-[26rem]" aria-hidden="true" />;
  const slides = banners.length > 0 ? banners : [FALLBACK_BANNER];

  const slide = slides[Math.min(index, slides.length - 1)];
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
        <div className="relative mx-auto flex h-56 w-full items-center justify-center sm:h-64 md:h-[20rem] md:justify-end lg:h-[24rem]">
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
