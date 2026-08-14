import { useEffect, useState } from "react";
import { CalendarDays, ChevronRight, ImageOff, MapPin } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { Badge } from "../../components/ui/Badge";
import { ApiError } from "../../api/client";
import { getFairPublicSummary, type FairPublicSummary } from "../../api/fair";
import { getReservationAvailability, type ReservationAvailability } from "../../api/reservation";
import { fairCategoryLabels, formatFairPeriodDow } from "./fairCard";
import { FairParticipatingBooths } from "./FairParticipatingBooths";
import { FairReviews } from "./FairReviews";

const INDOOR_OUTDOOR_LABELS: Record<string, string> = { INDOOR: "실내", OUTDOOR: "실외" };

// 오늘(Asia/Seoul 기준) YYYY-MM-DD. operationEndDate와 문자열 비교로 종료 판정.
// 브라우저 시간대와 무관하게 KST로 고정한다(해외 기기에서 종료 판정이 하루 밀리는 것 방지).
function todayISO(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

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

  const ended = !!fair.operationEndDate && fair.operationEndDate < todayISO();
  const indoorOutdoor = fair.indoorOutdoor ? INDOOR_OUTDOOR_LABELS[fair.indoorOutdoor] ?? null : null;
  // 예매 가능 = 예매 창이 열려(availability 성공) 잔여석 있는 날짜가 하나라도 있음.
  const reservable = !ended && !!availability && availability.dates.some((date) => date.available);
  const schedule = availability?.dates ?? [];

  return (
    <PageContainer className="py-7 sm:py-10">
      <div className="grid gap-6 md:grid-cols-[300px_1fr] md:gap-8">
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
              {reservable ? (
                <Link
                  to={`/tickets/${fair.fairId}`}
                  className="inline-flex min-h-12 items-center justify-center gap-1 rounded-button bg-primary-strong px-8 text-base font-bold text-white transition hover:opacity-90"
                >
                  예매하기
                  <ChevronRight size={18} aria-hidden="true" />
                </Link>
              ) : (
                <>
                  <span
                    className="inline-flex min-h-12 cursor-not-allowed items-center justify-center rounded-button bg-surface-alt px-8 text-base font-bold text-muted"
                    aria-disabled="true"
                  >
                    {ended ? "종료된 행사" : "예매 준비 중"}
                  </span>
                  {!ended && availabilityError && <p className="mt-2 text-sm text-muted">{availabilityError}</p>}
                </>
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
