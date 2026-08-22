import { useCallback, useEffect, useRef, useState } from "react";
import { CalendarDays, ChevronRight, ImageOff, MapPin, PawPrint, Store } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { KakaoMap } from "../../components/map/KakaoMap";
import { PageContainer } from "../../components/common/PageContainer";
import { Badge } from "../../components/ui/Badge";
import { ApiError } from "../../api/client";
import { getFairPublicSummary, type FairPublicSummary } from "../../api/fair";
import { getReservationAvailability, type ReservationAvailability } from "../../api/reservation";
import { todayInSeoul } from "../../utils/date";
import { fairCategoryLabels, formatFairPeriodDow, isFairInProgress } from "./fairCard";
import { FairParticipatingBooths } from "./FairParticipatingBooths";
import { FairReviews } from "./FairReviews";

const INDOOR_OUTDOOR_LABELS: Record<string, string> = { INDOOR: "실내", OUTDOOR: "실외" };

// 사이트 상단 헤더(PublicHeader)의 높이(px). 스크롤 고정 바를 이 아래에 붙이고, 등장 판정 기준선도 여기로 맞춘다.
const SITE_HEADER_PX = 72;

// 관람료: 0이면 무료, 그 외엔 "N원". reservationFee는 예매 창이 열렸을 때만 온다.
function formatFee(fee: number): string {
  return fee > 0 ? `${fee.toLocaleString()}원` : "무료";
}

// "10:00:00"(LocalTime) → "10:00". 값이 이상하면 그대로 둔다.
function formatTime(time: string): string {
  return /^\d{2}:\d{2}/.test(time) ? time.slice(0, 5) : time;
}

/**
 * 공개 행사 상세. getFairPublicSummary로 정보를, getReservationAvailability로 예매 정보(관람료·운영일정)를 가져온다.
 * 예매 정보 조회는 "예매 창이 열렸을 때만" 성공한다(백엔드 validateReservableFair: 예매기간 밖·미공개·취소면 R003).
 * 그래서 실패(R003 등)는 페이지 에러가 아니라 "예매 준비 중"으로만 처리하고, 관람료·운영일정은 성공했을 때만 보여준다.
 */
export function FairDetailPage() {
  const { fairId } = useParams();
  // 라우트는 /fairs/:fairId가 바뀌어도 같은 엘리먼트를 재사용한다(remount 안 됨).
  // key로 행사마다 통째로 remount시켜 이전 행사의 상태(정보·예매·notFound·에러)가 새 행사로 새지 않게 하고,
  // 자식(FairReviews 등)도 함께 새 인스턴스로 만들어 이전 행사의 늦은 응답이 섞이지 않게 한다.
  return <FairDetailView key={fairId ?? ""} fairId={fairId} />;
}

function FairDetailView({ fairId }: { fairId: string | undefined }) {
  const id = Number(fairId);
  const idValid = Number.isInteger(id) && id > 0;

  const [fair, setFair] = useState<FairPublicSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [availability, setAvailability] = useState<ReservationAvailability | null>(null);
  const [availabilityError, setAvailabilityError] = useState<string | null>(null);

  useEffect(() => {
    if (!idValid) return;
    let alive = true;
    Promise.allSettled([getFairPublicSummary(id), getReservationAvailability(id)]).then(([summaryRes, availRes]) => {
      if (!alive) return;
      if (summaryRes.status === "fulfilled") {
        setFair(summaryRes.value);
      } else {
        const err = summaryRes.reason;
        if (err instanceof ApiError && err.status === 404) setNotFound(true);
        else setError(err instanceof ApiError ? err.message : "행사 정보를 불러오지 못했어요.");
      }
      if (availRes.status === "fulfilled") {
        setAvailability(availRes.value);
      } else {
        const err = availRes.reason;
        setAvailabilityError(err instanceof ApiError ? err.message : "지금은 예매할 수 없어요.");
      }
      setLoading(false);
    });
    return () => {
      alive = false;
    };
  }, [id, idValid]);

  // 스크롤로 원래 상단 헤더가 화면 위(사이트 헤더 아래 기준선)로 사라지면 얇은 고정 바를 띄운다.
  const [showCompact, setShowCompact] = useState(false);
  const observerRef = useRef<IntersectionObserver | null>(null);
  // 상단 헤더에 붙는 콜백 ref. 헤더가 붙을 때 관찰을 시작하고, 떨어질 때(null) 정리한다.
  const headerRef = useCallback((node: HTMLDivElement | null) => {
    observerRef.current?.disconnect();
    if (!node) return;
    observerRef.current = new IntersectionObserver(
      ([entry]) => {
        // 헤더가 위로 지나갔을 때만 켠다(아직 헤더에 닿기 전엔 top이 양수라 무시).
        setShowCompact(!entry.isIntersecting && entry.boundingClientRect.top < 0);
      },
      { rootMargin: `-${SITE_HEADER_PX}px 0px 0px 0px`, threshold: 0 },
    );
    observerRef.current.observe(node);
  }, []);

  if (!idValid || notFound) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="행사를 찾을 수 없어요." description="비공개거나 삭제된 행사일 수 있어요." actionTo="/fairs/upcoming" actionLabel="행사 목록으로" />
      </PageContainer>
    );
  }
  if (loading) {
    return (
      <PageContainer className="py-7 sm:py-10">
        <FairDetailSkeleton />
      </PageContainer>
    );
  }
  if (error || !fair) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="행사 정보를 불러오지 못했어요." description={error ?? "잠시 후 다시 시도해 주세요."} actionTo="/fairs/upcoming" actionLabel="행사 목록으로" />
      </PageContainer>
    );
  }

  // 생애주기(진행 중·종료)는 서버 status를 먼저 믿는다. 목록의 FairPublicListItem.status 주석과
  // 같은 원칙으로, 사용자 PC 날짜가 틀려도 판정이 흔들리지 않게 한다. status가 비어 있을 때만
  // (백엔드가 null로 줄 수 있다) 날짜로 판정한다 - 운영종료일이 없는 행사도 이 경우에 들어온다.
  const endedByDate = !!fair.operationEndDate && fair.operationEndDate < todayInSeoul();
  const ended = fair.status === "ENDED" ? true : fair.status === "IN_PROGRESS" ? false : endedByDate;
  const indoorOutdoor = fair.indoorOutdoor ? INDOOR_OUTDOOR_LABELS[fair.indoorOutdoor] ?? null : null;
  // 예매 가능 = 예매 창이 열려(availability 성공) 잔여석 있는 날짜가 하나라도 있음.
  const reservable = !ended && !!availability && availability.dates.some((date) => date.available);
  // 운영 중인 행사. 사전예약이 닫혔어도(reservable=false) 현장예매는 열려 있을 수 있으므로
  // 버튼을 비활성으로 막지 않고 예매 화면으로 보낸다 - 그 화면이 사전예약과 현장예매를 둘 다
  // 다루고, 판매 상태·입장 마감 같은 최종 판정은 백엔드가 한다.
  // 위 ended가 status를 먼저 보므로 진행 중이면 ended는 반드시 false다(!ended는 불필요).
  // status가 비었을 때 운영기간으로 판정하는 폴백은 ended와 같은 규칙을 쓴다(isFairInProgress).
  const inProgress = isFairInProgress(fair.status, fair.operationStartDate, fair.operationEndDate);
  const schedule = availability?.dates ?? [];

  return (
    <PageContainer className="py-7 sm:py-10">
      {/* 스크롤로 상단 헤더가 사라지면 나타나는 얇은 고정 바(행사명 + 예매 CTA). fixed라 화면 기준 배치다. */}
      <div
        inert={!showCompact}
        style={{ top: SITE_HEADER_PX }}
        className={`fixed inset-x-0 z-30 border-b border-line bg-card/95 backdrop-blur transition-all duration-200 ${
          showCompact ? "translate-y-0 opacity-100" : "pointer-events-none -translate-y-full opacity-0"
        }`}
      >
        <div className="page-shell flex items-center gap-3 py-2.5">
          {fair.posterImageUrl && (
            <img src={fair.posterImageUrl} alt="" className={`size-10 shrink-0 rounded-lg object-cover ${ended ? "grayscale" : ""}`} />
          )}
          <span className="min-w-0 flex-1 truncate text-sm font-extrabold sm:text-base">{fair.name}</span>
          <div className="shrink-0">
            <ReserveButton reservable={reservable} ended={ended} inProgress={inProgress} fairId={fair.fairId} size="sm" />
          </div>
        </div>
      </div>

      <div ref={headerRef} className="grid gap-6 md:grid-cols-[300px_1fr] md:gap-8">
        {/* 포스터 */}
        <div className="relative mx-auto aspect-[4/5] w-full max-w-[300px] overflow-hidden rounded-card bg-surface-alt">
          {fair.posterImageUrl ? (
            <img src={fair.posterImageUrl} alt={`${fair.name} 포스터`} className={`size-full object-cover ${ended ? "grayscale" : ""}`} />
          ) : (
            <div className="grid size-full place-items-center text-muted">
              <ImageOff size={32} aria-hidden="true" />
            </div>
          )}
          {fair.category && (
            <div className="absolute left-3 top-3">
              <Badge tone="ink">{fairCategoryLabels[fair.category] ?? fair.category}</Badge>
            </div>
          )}
        </div>

        {/* 정보 + CTA */}
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-3">
            <h1 className="text-2xl font-extrabold leading-tight sm:text-3xl">{fair.name}</h1>
            <dl className="flex flex-col gap-2 text-sm">
              <div className="flex gap-2">
                <CalendarDays size={18} className="mt-0.5 shrink-0 text-muted" aria-hidden="true" />
                <dd>{formatFairPeriodDow(fair.operationStartDate, fair.operationEndDate)}</dd>
              </div>
              {/* 동반 가능 여부는 "데려갈 수 있나"를 판단하는 정보라, 기본값이든 아니든 양쪽 모두
                  문장으로 보여준다(목록 카드는 반대로 예외인 '불가'만 배지로 띄운다). */}
              <div className="flex gap-2">
                <PawPrint size={18} className="mt-0.5 shrink-0 text-muted" aria-hidden="true" />
                <dd>{fair.petAllowed ? "반려동물 동반 가능" : "반려동물 동반 불가"}</dd>
              </div>
              <div className="flex gap-2">
                <MapPin size={18} className="mt-0.5 shrink-0 text-muted" aria-hidden="true" />
                <dd className="flex flex-col">
                  <span>
                    {fair.placeName ?? "장소 미정"}
                    {indoorOutdoor ? ` · ${indoorOutdoor}` : ""}
                  </span>
                  {fair.address && <span className="text-muted">{fair.address}</span>}
                </dd>
              </div>
            </dl>
            {/* 지오코딩 실패(주소 못 찾음) 또는 지도 SDK 키 미설정이면 KakaoMap이 스스로 null을 반환한다 */}
            {fair.latitude != null && fair.longitude != null && (
              <KakaoMap latitude={fair.latitude} longitude={fair.longitude} label={fair.placeName} />
            )}
          </div>

          {/* 관람료 + 예매 CTA (관람료는 예매 창이 열렸을 때만 온다) */}
          <div className="flex flex-col gap-3">
            {availability && (
              <p className="text-sm">
                <span className="text-muted">관람료 </span>
                <span className="text-base font-extrabold">{formatFee(availability.reservationFee)}</span>
              </p>
            )}
            <div>
              <div className="flex items-center gap-2">
                <ReserveButton reservable={reservable} ended={ended} inProgress={inProgress} fairId={fair.fairId} size="lg" />
                <Link
                  to={`/fairs/${fair.fairId}/booths`}
                  className="inline-flex min-h-12 items-center justify-center gap-1 rounded-button border border-line bg-card px-6 text-base font-bold text-ink transition hover:bg-page"
                >
                  <Store size={18} aria-hidden="true" />
                  참가 부스 보기
                </Link>
              </div>
              {/* 사전예약 조회 실패 사유(R003 등)는 할 수 있는 행동이 없을 때만 띄운다 -
                  현장예매 버튼 아래에 "예약을 접수하지 않는 행사"라고 붙으면 서로 어긋난다. */}
              {!reservable && !ended && !inProgress && availabilityError && (
                <p className="mt-2 text-sm text-muted">{availabilityError}</p>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 운영 일정: 오늘 이후 운영일의 입장시간·잔여석. 예매 창이 열렸을 때만 내려온다. */}
      {schedule.length > 0 && (
        <section className="mt-10">
          <h2 className="text-lg font-extrabold">운영 일정</h2>
          <ul className="mt-3 divide-y divide-line overflow-hidden rounded-card border border-line">
            {schedule.map((date) => (
              <li key={date.visitDate} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-3 text-sm">
                <span className="font-bold">{formatFairPeriodDow(date.visitDate, null)}</span>
                <span className="text-muted">
                  입장 {formatTime(date.entryStartTime)}~{formatTime(date.entryEndTime)}
                </span>
                <span className={date.available ? "font-bold text-leaf" : "text-muted"}>
                  {date.available ? `잔여 ${date.remainingCapacity}석` : "마감"}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* 소개 */}
      {fair.description && (
        <section className="mt-10">
          <h2 className="text-lg font-extrabold">행사 소개</h2>
          <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-ink">{fair.description}</p>
        </section>
      )}

      {/* 참가기업 (섹션 자체가 비면 컴포넌트가 null을 반환해 안 보인다) */}
      <FairParticipatingBooths fairId={fair.fairId} />

      {/* 관람 안내 */}
      {fair.noticeText && (
        <section className="mt-8">
          <h2 className="text-lg font-extrabold">관람 안내</h2>
          <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-muted">{fair.noticeText}</p>
        </section>
      )}

      {/* 리뷰 (리뷰가 없으면 컴포넌트가 null을 반환해 안 보인다) */}
      <FairReviews fairId={fair.fairId} />
    </PageContainer>
  );
}

/** 예매 CTA. 상단 헤더(size="lg")와 스크롤 고정 바(size="sm")가 같은 상태 판정을 공유한다. */
function ReserveButton({
  reservable,
  ended,
  inProgress,
  fairId,
  size,
}: {
  reservable: boolean;
  ended: boolean;
  inProgress: boolean;
  fairId: number;
  size: "lg" | "sm";
}) {
  const sizeClass = size === "lg" ? "min-h-12 px-8 text-base" : "min-h-10 px-5 text-sm";
  // 예매 화면은 사전예약·현장예매를 둘 다 다루므로, 둘 중 하나라도 가능성이 있으면 링크를 준다.
  // 운영 중인데 사전예약만 닫힌 행사를 비활성 버튼으로 막으면 현장예매 입구까지 사라진다.
  const actionLabel = reservable ? "예매하기" : inProgress ? "현장예매" : null;
  if (actionLabel) {
    return (
      <Link
        to={`/tickets/${fairId}`}
        className={`inline-flex items-center justify-center gap-1 rounded-button bg-primary-strong font-bold text-white transition hover:opacity-90 ${sizeClass}`}
      >
        {actionLabel}
        <ChevronRight size={size === "lg" ? 18 : 16} aria-hidden="true" />
      </Link>
    );
  }
  return (
    <span
      className={`inline-flex cursor-not-allowed items-center justify-center rounded-button bg-surface-alt font-bold text-muted ${sizeClass}`}
      aria-disabled="true"
    >
      {ended ? "종료된 행사" : "예매 준비 중"}
    </span>
  );
}

/** 로딩 자리표시. 실제 상세와 같은 2열 배치라 로딩→본문 전환이 매끄럽다. */
function FairDetailSkeleton() {
  return (
    <div className="grid gap-6 md:grid-cols-[300px_1fr] md:gap-8" aria-hidden="true">
      <div className="mx-auto aspect-[4/5] w-full max-w-[300px] animate-pulse rounded-card bg-surface-alt" />
      <div className="flex flex-col gap-4">
        <div className="h-8 w-3/4 animate-pulse rounded bg-surface-alt" />
        <div className="h-4 w-1/2 animate-pulse rounded bg-surface-alt" />
        <div className="h-4 w-2/5 animate-pulse rounded bg-surface-alt" />
        <div className="mt-2 h-12 w-40 animate-pulse rounded-button bg-surface-alt" />
      </div>
    </div>
  );
}
