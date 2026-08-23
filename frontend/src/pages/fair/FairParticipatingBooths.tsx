import { useEffect, useState } from "react";
import { PublicBoothLayoutCanvas } from "../../components/booth/PublicBoothLayoutCanvas";
import {
  getConfirmedBooths,
  type ConfirmedBoothResponse,
} from "../../api/booth";

interface Company {
  boothId: number;
  businessName: string;
  imageUrl: string | null;
  hallName: string;
}

// confirmed-booths는 부스가 슬롯을 여러 개 쓰면 슬롯당 한 행으로 온다. boothId로 묶어 "참가업체" 하나로 만든다.
function toCompanies(rows: ConfirmedBoothResponse[]): Company[] {
  const byId = new Map<number, Company>();
  for (const row of rows) {
    if (!byId.has(row.boothId)) {
      byId.set(row.boothId, { boothId: row.boothId, businessName: row.businessName, imageUrl: row.imageUrl, hallName: row.hallName });
    }
  }
  return [...byId.values()];
}

/**
 * 행사 상세의 "참가업체" 섹션. confirmed-booths(공개)로 참가업체 수를 세고, 배치도(PublicBoothLayoutCanvas)를 보여준다.
 * 카드 목록은 별도 페이지(FairBoothsPage, /fairs/:fairId/booths)로 분리했다 - 진입은
 * FairDetailPage의 "부스 목록" 버튼에서 한다. 배치도의 슬롯 클릭 시 뜨는 참가업체 정보 패널은
 * PublicBoothLayoutCanvas 내부 기능이라 그대로 유지된다.
 * 참가업체 조회가 비거나 실패하면 섹션 자체를 숨긴다(상세의 부가 정보라 페이지를 막지 않는다).
 */
export function FairParticipatingBooths({ fairId }: { fairId: number }) {
  const [companies, setCompanies] = useState<Company[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    getConfirmedBooths(fairId)
      .then((rows) => {
        if (alive) setCompanies(toCompanies(rows));
      })
      .catch(() => {
        /* 부가 정보라 조용히 숨김 */
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [fairId]);

  // 로딩 중이거나 참가업체이 없으면 섹션을 통째로 숨긴다.
  if (loading || companies.length === 0) return null;

  return (
    <section className="mt-10">
      <h2 className="text-lg font-extrabold">
        참가업체 <span className="text-sm font-normal text-muted">{companies.length}</span>
      </h2>

      {/* 배치도(읽기 전용) - 부스를 클릭하면 참가업체 정보 패널이 뜬다. 카드 목록과 같은
          공개 API(getConfirmedBooths)를 쓰지만 이 컴포넌트가 직접 다시 불러온다 - 독립
          모듈 원칙(petopia-booth-public-view-idea 스킬 참고). */}
      <div className="mt-3">
        <PublicBoothLayoutCanvas fairId={fairId} />
      </div>
    </section>
  );
}
