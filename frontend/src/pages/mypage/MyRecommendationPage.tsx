import { useEffect, useState } from "react";
import { ApiError } from "../../api/client";
import { getMyReservations, getReservationDetail, type ReservationListItem } from "../../api/reservation";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Card } from "../../components/ui/Card";
import { Select } from "../../components/ui/Select";
import { BoothRecommendationContent } from "../fair/BoothRecommendationPage";

interface RecommendedFair {
  fairId: number;
  fairName: string;
}

function isRecommendationEligible(reservation: ReservationListItem) {
  return !reservation.isEnded && (reservation.reservationStatus === "CONFIRMED" || reservation.reservationStatus === "CHECKED_IN");
}

export function MyRecommendationPage() {
  const [fairs, setFairs] = useState<RecommendedFair[]>([]);
  const [selectedFairId, setSelectedFairId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getMyReservations(0, 50)
      .then(async (response) => {
        const eligibleReservations = response.items.filter(isRecommendationEligible);
        const details = await Promise.allSettled(eligibleReservations.map((reservation) => getReservationDetail(reservation.reservationId)));
        const byFairId = new Map<number, RecommendedFair>();

        details.forEach((result, index) => {
          if (result.status === "fulfilled") {
            byFairId.set(result.value.fairId, {
              fairId: result.value.fairId,
              fairName: eligibleReservations[index].fairName,
            });
          }
        });

        if (!alive) return;
        const nextFairs = Array.from(byFairId.values());
        setFairs(nextFairs);
        setSelectedFairId((previous) => previous ?? nextFairs[0]?.fairId ?? null);
      })
      .catch((reason: unknown) => {
        if (alive) setError(reason instanceof ApiError ? reason.message : "예약한 행사 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });

    return () => {
      alive = false;
    };
  }, []);

  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader eyebrow="AI 추천" title="내 예약 행사 부스 추천" description="예약한 행사 중 아직 종료되지 않은 행사를 골라 맞춤 부스를 추천받아 보세요." />

      {loading ? (
        <p className="py-16 text-center text-sm text-muted">예약한 행사 목록을 불러오는 중이에요…</p>
      ) : error ? (
        <EmptyState title="행사 목록을 불러올 수 없어요." description={error} />
      ) : fairs.length === 0 ? (
        <EmptyState title="추천받을 수 있는 예약 행사가 없어요." description="예약이 확정된 행사 중 아직 종료되지 않은 행사가 있어야 추천받을 수 있어요." actionTo="/fairs/upcoming" actionLabel="행사 둘러보기" />
      ) : (
        <Card className="mt-6 p-6">
          <label htmlFor="recommendation-fair" className="mb-2 block text-sm font-bold text-ink">행사 선택</label>
          <Select
            id="recommendation-fair"
            value={selectedFairId ?? ""}
            onChange={(event) => setSelectedFairId(Number(event.target.value))}
            aria-label="AI 추천을 받을 행사 선택"
          >
            {fairs.map((fair) => (
              <option key={fair.fairId} value={fair.fairId}>{fair.fairName}</option>
            ))}
          </Select>
          <p className="mt-3 text-xs text-muted">행사를 선택하면 아래에서 바로 추천 조건을 입력할 수 있어요.</p>
        </Card>
      )}

      {!loading && !error && selectedFairId !== null && fairs.length > 0 && (
        <BoothRecommendationContent key={selectedFairId} fairId={selectedFairId} embedded />
      )}
    </div>
  );
}
