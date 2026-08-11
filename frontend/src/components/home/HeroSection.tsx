import { useEffect, useState } from "react";
import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";
import { heroSlides } from "../../mocks/home";

const SLIDE_MS = 3000;

export function HeroSection() {
  const [index, setIndex] = useState(0);
  useEffect(() => {
    const timer = setTimeout(() => setIndex((i) => (i + 1) % heroSlides.length), SLIDE_MS);
    return () => clearTimeout(timer);
  }, [index]);
  const slide = heroSlides[index];
  return <section className="relative overflow-hidden transition-colors duration-500" style={{ backgroundColor: slide.bg }}><div className="page-shell relative grid items-center gap-8 pt-10 sm:pt-14 md:grid-cols-[0.75fr_1.25fr]"><div key={index} className="py-3" style={{ animation: "heroTextIn 600ms ease-out both" }}>{slide.eyebrow && <p className="mb-3 text-sm font-bold text-ink">{slide.eyebrow}</p>}<h1 className="whitespace-pre-line text-3xl font-black leading-[1.2] tracking-tight text-ink sm:text-4xl">{slide.title}</h1><p className="mt-5 max-w-md whitespace-pre-line text-sm leading-7 text-ink/70 sm:text-base">{slide.subtitle}</p><div className="mt-8 flex flex-wrap gap-3"><Link to={slide.cta.to} className="inline-flex min-h-11 items-center gap-2 rounded-button bg-primary px-5 text-sm font-bold text-white transition hover:opacity-90">{slide.cta.label}<ArrowRight size={16} /></Link>{slide.cta2 && <Link to={slide.cta2.to} className="inline-flex min-h-11 items-center gap-2 rounded-button border border-ink/15 bg-card px-5 text-sm font-bold text-ink transition hover:bg-white">{slide.cta2.label}</Link>}</div></div><div className="relative mx-auto flex h-64 w-full items-center justify-center sm:h-80 md:h-[26rem] md:justify-end lg:h-[28rem]"><img key={index} src={slide.image} alt="" className="max-h-full max-w-full object-contain" style={{ animation: "heroPosterIn 600ms ease-out both" }} /></div></div><div className="page-shell relative flex items-center justify-center gap-2 pb-6 pt-2">{heroSlides.map((_, i) => i === index ? <span key={i} className="relative h-1.5 w-10 overflow-hidden rounded-pill bg-ink/20"><span key={index} className="absolute inset-y-0 left-0 bg-ink" style={{ animation: `heroProgress ${SLIDE_MS}ms linear forwards` }} /></span> : <button key={i} type="button" aria-label={`${i + 1}번째 배너 보기`} onClick={() => setIndex(i)} className="size-1.5 rounded-pill bg-ink/30 transition hover:bg-ink/50" />)}</div></section>;
}
