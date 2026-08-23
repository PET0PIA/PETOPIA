import { useEffect, useState } from "react";
import { ImageOff } from "lucide-react";
import { Link } from "react-router-dom";
import { getConfirmedBooths, type ConfirmedBoothResponse } from "../../api/booth";
import type { FairPublicListItem } from "../../api/fair";
import { SectionHeader } from "../common/SectionHeader";
import { HomeBand } from "./HomeBand";

/**
 * 한 부스가 슬롯을 여러 개 쓰면 API가 슬롯마다 한 행씩 내려주므로, boothId로 묶어서
 * 슬롯 번호를 모은다(묶지 않으면 같은 참가업체가 홈에 두 번 세 번 나온다).
 * 행사 부스 목록 화면(FairBoothsPage)이 같은 이유로 같은 처리를 한다.
 */
interface BoothGroup {
  boothId: number;
  businessName: string;
  imageUrl: string | null;
  category: string | null;
  slotNumbers: string[];
}

function groupByBooth(booths: ConfirmedBoothResponse[]): BoothGroup[] {
  const groups: BoothGroup[] = [];
  for (const booth of booths) {
    const existing = groups.find((group) => group.boothId === booth.boothId);
    if (existing) {
      existing.slotNumbers.push(booth.slotNumber);
    } else {
      groups.push({
        boothId: booth.boothId,
        businessName: booth.businessName,
        imageUrl: booth.imageUrl,
        category: booth.category,
        slotNumbers: [booth.slotNumber],
      });
    }
  }
  return groups;
}

/**
 * 흐르는 띠로 만들 최소 개수. 이보다 적으면 한 벌의 폭이 화면보다 좁아서, 흘릴 때 중간에
 * 빈 공간이 크게 지나간다(화면이 비어 보인다). 그럴 땐 가만히 있는 줄로 보여준다.
 */
const MIN_FOR_MARQUEE = 6;

function BoothItem({ booth, hidden = false }: { booth: BoothGroup; hidden?: boolean }) {
  const caption = [booth.category, booth.slotNumbers.join(", ")].filter(Boolean).join(" · ");
  return (
    // 간격을 gap이 아니라 항목의 왼쪽 여백으로 주는 이유: 띠는 같은 내용을 두 벌 이어 붙여
    // 흘리는데, gap은 두 벌이 만나는 이음새에만 간격이 빠져서 그 부분이 붙어 보인다.
    <div className="shrink-0 pl-5">
      <Link
        to={`/booths/${booth.boothId}`}
        tabIndex={hidden ? -1 : undefined}
        className="group block w-32 sm:w-36"
      >
        <div className="aspect-square overflow-hidden rounded-card bg-surface-alt ring-1 ring-line">
          {booth.imageUrl ? (
            <img
              src={booth.imageUrl}
              alt={`${booth.businessName} 부스 이미지`}
              className="size-full object-cover transition duration-300 group-hover:scale-[1.03]"
            />
          ) : (
            <div className="grid size-full place-items-center text-muted">
              <ImageOff size={24} aria-hidden="true" />
            </div>
          )}
        </div>
        <h3 className="mt-2 truncate text-sm font-extrabold text-ink">{booth.businessName}</h3>
        {caption && <p className="truncate text-xs text-muted">{caption}</p>}
      </Link>
    </div>
  );
}

function BoothRow({ booths, hidden = false }: { booths: BoothGroup[]; hidden?: boolean }) {
  return (
    <div className="flex shrink-0 items-start" aria-hidden={hidden || undefined}>
      {booths.map((booth) => (
        <BoothItem key={booth.boothId} booth={booth} hidden={hidden} />
      ))}
    </div>
  );
}

/**
 * 홈의 "이번 행사의 참가 부스". 부스는 행사별로만 조회할 수 있어서(전체 부스 목록 API가 없다)
 * 홈에서 대표 행사 하나를 골라 그 행사의 확정 부스를 보여준다. 어떤 행사를 고를지는
 * HomePage가 정해서 넘겨준다.
 *
 * <p>보여줄 게 없으면(대표 행사 없음·확정 부스 0개·조회 실패) 섹션을 통째로 감춘다.
 * 부가 정보라, 빈 껍데기로 홈을 늘리는 것보다 없는 편이 낫다(공지 띠·배너와 같은 규칙).
 * 로딩 중에도 아직 그리지 않는다 - 어차피 비어서 사라질 수 있는 섹션에 자리표시부터
 * 띄우면 화면이 한 번 늘어난 뒤 다시 줄어든다.
 *
 * <p>대표 행사가 바뀔 때 이전 행사의 부스가 잠깐 남지 않도록, 부르는 쪽(HomePage)이 fairId를
 * key로 줘서 이 컴포넌트를 새로 그린다(state 초기화를 effect에서 하지 않는 방식).
 */
export function BoothPreviewSection({ fair }: { fair: FairPublicListItem | null }) {
  const [booths, setBooths] = useState<BoothGroup[] | null>(null);

  useEffect(() => {
    if (!fair) return;
    let ignore = false;
    getConfirmedBooths(fair.fairId)
      .then((data) => { if (!ignore) setBooths(groupByBooth(data)); })
      .catch(() => { if (!ignore) setBooths([]); });
    return () => { ignore = true; };
  }, [fair]);

  if (!fair || booths === null || booths.length === 0) return null;

  return (
    <HomeBand>
      <section>
        <SectionHeader
          title="이번 행사의 참가 부스"
          description={fair.name}
          linkTo={`/fairs/${fair.fairId}/booths`}
          linkLabel="참가 부스 전체 보기"
          centered
        />
        {booths.length >= MIN_FOR_MARQUEE ? (
          // 화면 폭을 넘기는 부분은 잘라내고, 같은 줄을 두 벌 이어 붙여 끊김 없이 흘린다.
          // 마우스를 올리면 멈춰서 눌러볼 수 있게 하고, 동작 줄이기 설정이면 아예 흐르지 않는다.
          <div className="-ml-5 overflow-hidden">
            <div className="flex w-max animate-[marquee_45s_linear_infinite] hover:[animation-play-state:paused] motion-reduce:animate-none">
              <BoothRow booths={booths} />
              <BoothRow booths={booths} hidden />
            </div>
          </div>
        ) : (
          <div className="-ml-5 flex flex-wrap justify-center">
            {booths.map((booth) => (
              <BoothItem key={booth.boothId} booth={booth} />
            ))}
          </div>
        )}
      </section>
    </HomeBand>
  );
}
