import { ChevronLeft, MapPin, PawPrint, Route as RouteIcon, Sparkles } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { HallBoothMap, type HallBoothMapSlot } from "../../components/booth-map/HallBoothMap";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ApiError } from "../../api/client";
import { getMyPets, type Pet } from "../../api/pet";
import {
  getBoothRecommendations,
  getBoothRoutes,
  type BoothRecommendationItem,
  type HallRoute,
} from "../../api/recommendation";
import { useAuth } from "../../contexts/AuthContext";

type Tab = "booths" | "routes";

const NEED_OPTIONS = [
  { value: "사료&간식", description: "사료, 간식, 음료 등" },
  { value: "리빙용품", description: "가구, 하우스, 방석, 캣타워, 캣휠 등" },
  { value: "장난감", description: "공, 터널, 낚싯대, 노즈워크 등" },
  { value: "의류&악세사리", description: "옷, 목걸이, 모자 등" },
  { value: "펫테크", description: "자동급수기, 자동급식기, 공기청정기, 청소기 등" },
  { value: "서비스", description: "장례, 사진촬영, 보험 등" },
  { value: "외출용품", description: "개모차, 하네스, 슬링백, 카시트 등" },
  { value: "미용용품", description: "목욕용품, 빗, 드라이기 등" },
  { value: "위생&배변용품", description: "탈취제, 모래, 화장실, 배변패드 등" },
  { value: "건강", description: "영양제, 보조기, 진단키트 등" },
  { value: "여행", description: "반려동물 동반 호텔, 항공, 캠핑 등" },
] as const;

export function BoothRecommendationPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const id = Number(fairId);
  const idValid = Number.isInteger(id) && id > 0;

  if (!idValid) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="잘못된 행사 주소예요." description="행사 주소가 올바르지 않아요." actionTo="/businesses" actionLabel="행사별 참여 기업으로" />
      </PageContainer>
    );
  }

  return <BoothRecommendationContent key={id} fairId={id} />;
}

function BoothRecommendationContent({ fairId }: { fairId: number }) {
  const { status } = useAuth();
  const loggedIn = status === "authenticated";

  const [pets, setPets] = useState<Pet[]>([]);
  // 반려동물 목록을 아직 못 받아온 동안엔 "등록된 반려동물이 없어요" 안내를 섣불리 보여주면 안
  // 되므로(깜빡이거나 잘못된 안내), 조회가 끝났는지 따로 든다.
  const [petsLoaded, setPetsLoaded] = useState(false);
  const [selectedPetIds, setSelectedPetIds] = useState<number[]>([]);
  const [selectedNeeds, setSelectedNeeds] = useState<string[]>([]);
  const [customNeed, setCustomNeed] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  // 결과 섹션을 보여줄지는 "데이터가 있는지"가 아니라 "한 번이라도 제출했는지"로 판단한다.
  // 데이터로 판단하면 두 요청이 다 실패했을 때 boothItems/hallRoutes가 둘 다 null로 남아서
  // 에러 메시지조차 보여줄 섹션이 렌더링되지 않는 문제가 있었다.
  const [hasSubmitted, setHasSubmitted] = useState(false);
  const [tab, setTab] = useState<Tab>("booths");
  const [boothItems, setBoothItems] = useState<BoothRecommendationItem[] | null>(null);
  const [hallRoutes, setHallRoutes] = useState<HallRoute[] | null>(null);
  // 부스 추천/동선 추천은 별개 API 호출이라 하나만 실패할 수도 있다 - 결과와 에러를 탭별로 따로 든다.
  const [boothsError, setBoothsError] = useState<string | null>(null);
  const [routesError, setRoutesError] = useState<string | null>(null);

  // 로그인했을 때만 반려동물 선택지를 보여준다(petId를 보내려면 로그인 필수 - 서버도 동일하게 검증).
  useEffect(() => {
    if (!loggedIn) return;
    let alive = true;
    getMyPets()
      .then((result) => {
        if (alive) setPets(result);
      })
      .catch(() => {
        // 반려동물 목록을 못 불러와도 "필요한 것"만으로 추천받는 건 가능하므로 폼 자체를 막지 않는다.
      })
      .finally(() => {
        if (alive) setPetsLoaded(true);
      });
    return () => {
      alive = false;
    };
  }, [loggedIn]);

  function togglePet(petId: number) {
    setSelectedPetIds((prev) => (prev.includes(petId) ? prev.filter((id) => id !== petId) : [...prev, petId]));
  }

  function toggleNeed(value: string) {
    setSelectedNeeds((prev) => {
      const next = prev.includes(value) ? prev.filter((item) => item !== value) : [...prev, value];
      if (value === "기타" && prev.includes(value)) setCustomNeed("");
      return next;
    });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const trimmedCustomNeed = customNeed.trim();
    const needs = selectedNeeds.filter((value) => value !== "기타");
    if (selectedNeeds.includes("기타") && trimmedCustomNeed.length > 0) needs.push(trimmedCustomNeed);
    if (selectedPetIds.length === 0 && needs.length === 0) {
      setFormError("반려동물을 선택하거나, 필요한 제품·서비스를 선택해 주세요.");
      return;
    }

    setFormError(null);
    setSubmitting(true);
    setHasSubmitted(true);
    setBoothsError(null);
    setRoutesError(null);

    const payload = {
      petIds: selectedPetIds.length > 0 ? selectedPetIds : undefined,
      need: needs.length > 0 ? needs.join(", ") : undefined,
    };

    // 두 API가 독립적이라(하나가 실패해도 다른 하나는 보여줄 수 있게) allSettled로 따로 처리한다.
    const [boothsResult, routesResult] = await Promise.allSettled([
      getBoothRecommendations(fairId, payload),
      getBoothRoutes(fairId, payload),
    ]);

    if (boothsResult.status === "fulfilled") {
      setBoothItems(boothsResult.value);
    } else {
      setBoothItems(null);
      setBoothsError(boothsResult.reason instanceof ApiError ? boothsResult.reason.message : "부스 추천을 불러오지 못했어요.");
    }

    if (routesResult.status === "fulfilled") {
      setHallRoutes(routesResult.value);
    } else {
      setHallRoutes(null);
      setRoutesError(routesResult.reason instanceof ApiError ? routesResult.reason.message : "동선 추천을 불러오지 못했어요.");
    }

    setSubmitting(false);
  }


  return (
    <PageContainer className="py-7 sm:py-10">
      <Link to={`/fairs/${fairId}/booths`} className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink">
        <ChevronLeft size={16} />
        확정 참가 부스
      </Link>

      <div className="mt-4">
        <PageHeader eyebrow="AI 추천" title="부스·동선 추천" description="반려동물이나 필요한 것을 알려주시면 어울리는 부스와 동선을 추천해 드려요." />
      </div>

      <Card className="mt-6 p-8">
        <form onSubmit={handleSubmit} className="space-y-5">
          {loggedIn && petsLoaded && pets.length > 0 && (
            <div>
              <p className="mb-1.5 block text-sm font-bold text-ink">반려동물 (선택, 여러 마리 가능)</p>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                {pets.map((pet) => (
                  <PetOption key={pet.petId} pet={pet} selected={selectedPetIds.includes(pet.petId)} onToggle={() => togglePet(pet.petId)} />
                ))}
              </div>
            </div>
          )}

          {loggedIn && petsLoaded && pets.length === 0 && (
            <div className="flex items-center justify-between gap-3 rounded-button border border-dashed border-line bg-page p-4">
              <p className="inline-flex items-center gap-2 text-sm text-muted">
                <PawPrint size={16} className="shrink-0 text-primary-strong" aria-hidden="true" />
                등록된 반려동물이 없어요. 반려동물을 추가하시겠어요?
              </p>
              <Link to="/mypage/pets/new">
                <Button type="button" variant="outline">
                  추가하기
                </Button>
              </Link>
            </div>
          )}

          <fieldset>
            <legend className="mb-2 block text-sm font-bold text-ink">필요한 제품·서비스 (여러 개 선택 가능)</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {NEED_OPTIONS.map((option) => (
                <label
                  key={option.value}
                  className={`flex cursor-pointer items-start gap-2 rounded-button border p-3 transition-colors ${
                    selectedNeeds.includes(option.value)
                      ? "border-primary-strong bg-primary-soft ring-2 ring-primary ring-offset-1 ring-offset-page"
                      : "border-line bg-card hover:bg-surface-alt"
                  }`}
                >
                  <input
                    type="checkbox"
                    value={option.value}
                    checked={selectedNeeds.includes(option.value)}
                    onChange={() => toggleNeed(option.value)}
                    className="mt-0.5 accent-primary-strong"
                  />
                  <span>
                    <span className="block text-sm font-bold text-ink">{option.value}</span>
                    {option.description && <span className="mt-0.5 block text-xs leading-5 text-muted">({option.description})</span>}
                  </span>
                </label>
              ))}
              <label
                className={`flex cursor-pointer items-start gap-2 rounded-button border p-3 transition-colors ${
                  selectedNeeds.includes("기타")
                    ? "border-primary-strong bg-primary-soft ring-2 ring-primary ring-offset-1 ring-offset-page"
                    : "border-line bg-card hover:bg-surface-alt"
                }`}
              >
                <input
                  type="checkbox"
                  value="기타"
                  checked={selectedNeeds.includes("기타")}
                  onChange={() => toggleNeed("기타")}
                  className="mt-0.5 accent-primary-strong"
                />
                <span>
                  <span className="block text-sm font-bold text-ink">기타</span>
                  <span className="mt-0.5 block text-xs leading-5 text-muted">원하는 제품이나 서비스를 직접 입력</span>
                </span>
              </label>
            </div>
            {selectedNeeds.includes("기타") && (
              <Input
                className="mt-3"
                value={customNeed}
                onChange={(event) => setCustomNeed(event.target.value)}
                placeholder="예: 반려동물 전용 응급키트"
                maxLength={100}
                aria-label="기타 필요한 제품이나 서비스"
              />
            )}
          </fieldset>

          {formError && <p className="text-sm font-bold text-primary-strong">{formError}</p>}

          <div className="text-right">
            <Button type="submit" disabled={submitting}>
              {submitting ? "추천받는 중…" : "추천받기"}
            </Button>
          </div>
        </form>
      </Card>

      {hasSubmitted && (
        <div className="mt-8">
          {submitting ? (
            <p className="py-10 text-center text-sm text-muted">추천받는 중…</p>
          ) : (
            <>
              <div className="mb-4 flex gap-2">
                <Button variant={tab === "booths" ? "primary" : "outline"} onClick={() => setTab("booths")}>
                  <Sparkles size={16} />
                  추천 부스
                </Button>
                <Button variant={tab === "routes" ? "primary" : "outline"} onClick={() => setTab("routes")}>
                  <RouteIcon size={16} />
                  동선 추천
                </Button>
              </div>

              {tab === "booths" && <BoothRecommendationResults items={boothItems} error={boothsError} />}
              {tab === "routes" && <RouteRecommendationResults routes={hallRoutes} error={routesError} />}
            </>
          )}
        </div>
      )}
    </PageContainer>
  );
}

// YYYY-MM-DD 생년월일로 만 나이를 계산한다. 백엔드 ClaudeBoothRecommender의 Period.between()과
// 같은 계산(생일이 아직 안 지났으면 -1) - 화면에 보여주는 나이와 실제로 Claude에 넘어가는 나이가
// 어긋나지 않게 맞춘다.
function calculateAge(birthDate: string | null): number | null {
  if (!birthDate) return null;
  const birth = new Date(birthDate);
  if (Number.isNaN(birth.getTime())) return null;
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const hasHadBirthdayThisYear =
    today.getMonth() > birth.getMonth() || (today.getMonth() === birth.getMonth() && today.getDate() >= birth.getDate());
  if (!hasHadBirthdayThisYear) age -= 1;
  return age;
}

function PetOption({ pet, selected, onToggle }: { pet: Pet; selected: boolean; onToggle: () => void }) {
  const age = calculateAge(pet.birthDate);
  return (
    <label
      className={`relative flex cursor-pointer items-center gap-2.5 rounded-button border p-3 transition-colors ${
        selected ? "border-primary-strong bg-primary-soft ring-2 ring-primary ring-offset-1 ring-offset-page" : "border-line bg-card hover:bg-page"
      }`}
    >
      <input type="checkbox" className="sr-only" checked={selected} onChange={onToggle} />
      {pet.imageUrl ? (
        <img src={pet.imageUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
      ) : (
        <span className="grid size-10 shrink-0 place-items-center rounded-full bg-page text-muted">
          <PawPrint size={18} aria-hidden="true" />
        </span>
      )}
      <div className="min-w-0">
        <p className="truncate text-sm font-bold text-ink">{pet.name}</p>
        <p className="truncate text-xs text-muted">
          {pet.species}
          {pet.breed && ` · ${pet.breed}`}
          {age !== null && ` · ${age}살`}
        </p>
      </div>
    </label>
  );
}

function BoothRecommendationResults({ items, error }: { items: BoothRecommendationItem[] | null; error: string | null }) {
  if (error) return <EmptyState title="부스 추천을 불러오지 못했어요." description={error} />;
  if (!items || items.length === 0) return <EmptyState title="추천할 부스를 찾지 못했어요." description="다른 조건으로 다시 시도해 보세요." />;

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {items.map((item) => (
        <Card key={item.boothId} className="p-5">
          <p className="font-bold text-ink">{item.boothName}</p>
          <p className="mt-1 inline-flex items-center gap-1 text-xs font-bold text-muted">
            <MapPin size={12} aria-hidden="true" />
            {item.hallName} {item.slotNumber}
          </p>
          <p className="mt-3 text-sm leading-6 text-muted">{item.reason}</p>
        </Card>
      ))}
    </div>
  );
}

// HallRoute의 stops(방문 순서·매칭 여부)를 HallBoothMap이 그릴 수 있는 슬롯 형태로 바꾼다.
// 박스엔 이미 slotNumber(부스 위치, 예: A-01)가 크게 뜨니 caption엔 부스명을 넣는다. 방문
// 순서(N번째)는 텍스트 대신 HallRouteMap이 모서리에 스티커 배지로 따로 그린다.
// 반려동물/조건에 맞는 부스(matched)는 leaf, 그 외 둘러볼만한 부스는 sun 톤으로 구분한다.
function toMapSlots(stops: HallRoute["stops"]): HallBoothMapSlot[] {
  return stops.map((stop) => ({
    boothSlotsId: stop.boothId,
    slotNumber: stop.slotNumber,
    posX: stop.posX,
    posY: stop.posY,
    width: stop.width,
    height: stop.height,
    caption: stop.boothName,
    tone: stop.matched ? "leaf" : "sun",
  }));
}

// 화살표가 부스 박스 안쪽까지 파고들지 않도록, 시작점 쪽엔 조금 짧게, 끝점(화살촉) 쪽엔 더
// 넉넉하게 여백을 두고 선을 짧게 그린다.
const ARROW_START_GAP = 10;
const ARROW_END_GAP = 16;

/**
 * HallBoothMap 위에 방문 순서 화살표를 겹쳐 그린다. HallBoothMap 파일 자체는 안 건드리고
 * (다른 화면 2곳에서도 쓰는 공용 컴포넌트라 손대지 않기로 함), 이 화면에서만 DOM을 측정해서
 * 그 위에 SVG를 얹는 방식 - HallBoothMap이 h3 제목 + 지도 박스 순서로 렌더링하는 걸 이용해
 * 두 번째 자식(지도 박스)의 실제 위치·크기를 재서 그 위치에 맞춰 SVG를 겹친다.
 *
 * 하나로 이어진 선 대신, 방문 구간(1→2, 2→3, ...)마다 화살표를 따로 끊어 그린다 - 이어진
 * 선보다 "다음엔 여기로" 한 구간씩 눈에 들어와서 순서를 파악하기 쉽다. 좌표도 0~100% 비율
 * 대신 측정한 박스의 실제 픽셀 크기로 계산해서(viewBox를 박스 픽셀 크기와 1:1로 맞춤)
 * 화살촉이 종횡비 때문에 찌그러지는 문제도 없앴다.
 */
function HallRouteMap({ hall }: { hall: HallRoute }) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const [box, setBox] = useState<{ width: number; height: number; top: number; left: number } | null>(null);

  useEffect(() => {
    const wrapper = wrapperRef.current;
    const mapBox = wrapper?.children[0]?.children[1] as HTMLElement | undefined;
    if (!wrapper || !mapBox) return;

    function measure() {
      const wrapperRect = wrapper!.getBoundingClientRect();
      const boxRect = mapBox!.getBoundingClientRect();
      setBox({
        top: boxRect.top - wrapperRect.top,
        left: boxRect.left - wrapperRect.left,
        width: boxRect.width,
        height: boxRect.height,
      });
    }
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(mapBox);
    return () => observer.disconnect();
  }, [hall.hallId, hall.stops.length]);

  const orderedStops = [...hall.stops].sort((a, b) => a.order - b.order);

  // 방문 순서대로 각 부스의 중심점을 박스의 실제 픽셀 좌표로 뽑는다 - posX/posY는 슬롯의
  // 좌상단 비율(0~1)이라 폭/높이의 절반을 더해야 박스 한가운데를 가리킨다.
  const points =
    box == null
      ? []
      : orderedStops.map((stop) => ({
          x: (stop.posX + stop.width / 2) * box.width,
          y: (stop.posY + stop.height / 2) * box.height,
        }));

  const segments = points.slice(0, -1).map((from, i) => {
    const to = points[i + 1];
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const dist = Math.hypot(dx, dy);
    if (dist === 0) return null;
    // 두 부스가 너무 가까우면(간격 합보다 짧으면) 여백을 생략하고 원점 그대로 잇는다.
    const canShrink = dist > ARROW_START_GAP + ARROW_END_GAP;
    const ux = dx / dist;
    const uy = dy / dist;
    return {
      key: i,
      x1: canShrink ? from.x + ux * ARROW_START_GAP : from.x,
      y1: canShrink ? from.y + uy * ARROW_START_GAP : from.y,
      x2: canShrink ? to.x - ux * ARROW_END_GAP : to.x,
      y2: canShrink ? to.y - uy * ARROW_END_GAP : to.y,
      angleDeg: (Math.atan2(dy, dx) * 180) / Math.PI,
    };
  });

  // 방문 순서 번호 스티커는 부스 박스 한가운데(caption 텍스트 자리)를 가리지 않도록 박스
  // 좌상단 모서리에 걸치듯 배치한다 - 화살표는 박스 중심을 향하니 서로 안 겹친다.
  const badges =
    box == null
      ? []
      : orderedStops.map((stop) => ({
          key: stop.boothId,
          order: stop.order,
          x: stop.posX * box.width,
          y: stop.posY * box.height,
        }));

  return (
    <div ref={wrapperRef} className="relative">
      <HallBoothMap hallName={hall.hallName} backgroundImageUrl={hall.floorPlanImageUrl} slots={toMapSlots(hall.stops)} />
      {box && (
        <svg
          aria-hidden="true"
          className="pointer-events-none absolute text-primary-strong"
          style={{ top: box.top, left: box.left, width: box.width, height: box.height }}
          viewBox={`0 0 ${box.width} ${box.height}`}
        >
          {segments.map(
            (segment) =>
              segment && (
                <g key={segment.key}>
                  {/* 배경(도면 이미지·격자) 위에서도 선이 잘 보이도록 흰색 테두리를 먼저 깔고 그 위에 색선을 그린다 */}
                  <line x1={segment.x1} y1={segment.y1} x2={segment.x2} y2={segment.y2} stroke="white" strokeWidth={4.5} strokeLinecap="round" />
                  <line x1={segment.x1} y1={segment.y1} x2={segment.x2} y2={segment.y2} stroke="currentColor" strokeWidth={2} strokeLinecap="round" />
                  {/* 화살촉: 꽉 찬 삼각형 대신 얇은 셰브런("＞") 모양이 더 정제돼 보인다.
                      끝점(x2,y2)에 중심을 두고 진행 방향으로 회전시킨다. */}
                  <g transform={`translate(${segment.x2}, ${segment.y2}) rotate(${segment.angleDeg})`}>
                    <path d="M -8 -5.5 L 0 0 L -8 5.5" fill="none" stroke="white" strokeWidth={4.5} strokeLinecap="round" strokeLinejoin="round" />
                    <path d="M -8 -5.5 L 0 0 L -8 5.5" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
                  </g>
                </g>
              )
          )}
          {/* 방문 순서 번호 스티커. 선/화살표보다 나중에 그려서 항상 맨 위에 보이게 한다. */}
          {badges.map((badge) => (
            <g key={badge.key} transform={`translate(${badge.x}, ${badge.y})`}>
              <circle r={11} fill="currentColor" stroke="white" strokeWidth={2} />
              <text textAnchor="middle" dominantBaseline="central" fill="white" fontSize={12} fontWeight={700}>
                {badge.order}
              </text>
            </g>
          ))}
        </svg>
      )}
    </div>
  );
}

function RouteRecommendationResults({ routes, error }: { routes: HallRoute[] | null; error: string | null }) {
  if (error) return <EmptyState title="동선 추천을 불러오지 못했어요." description={error} />;
  if (!routes || routes.length === 0) return <EmptyState title="추천할 동선을 찾지 못했어요." description="다른 조건으로 다시 시도해 보세요." />;

  return (
    <div className="space-y-6">
      {routes.map((hall) => (
        <Card key={hall.hallId} className="p-6">
          <HallRouteMap hall={hall} />
          <ol className="mt-4 space-y-3">
            {hall.stops.map((stop) => (
              <li key={stop.boothId} className="flex gap-3">
                <span className="grid size-7 shrink-0 place-items-center rounded-full bg-primary-strong text-xs font-bold text-white">
                  {stop.order}
                </span>
                <div>
                  <p className="text-sm font-bold text-ink">
                    {stop.boothName}
                    <span className="ml-2 text-xs font-normal text-muted">{stop.slotNumber}</span>
                    {stop.matched && (
                      <span className="ml-2 rounded-full bg-page px-2 py-0.5 text-[11px] font-bold text-primary-strong">맞춤</span>
                    )}
                  </p>
                  <p className="mt-0.5 text-sm leading-6 text-muted">{stop.reason}</p>
                </div>
              </li>
            ))}
          </ol>
        </Card>
      ))}
    </div>
  );
}
