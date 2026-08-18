import type { PetBreedStat } from "../../api/statistics";
import { Table } from "../ui/Table";

interface PetBreedBreakdownProps {
  data: PetBreedStat[];
}

/** 반려동물 품종별 방문 건수. count DESC로 이미 정렬되어 내려온다. */
export function PetBreedBreakdown({ data }: PetBreedBreakdownProps) {
  if (data.length === 0) {
    return <p className="py-4 text-center text-sm text-muted">아직 데이터가 없어요.</p>;
  }

  return (
    <Table>
      <thead>
        <tr className="border-b border-line text-xs font-bold text-muted">
          <th className="px-4 py-2.5">종</th>
          <th className="px-4 py-2.5">품종</th>
          <th className="px-4 py-2.5 text-right">건수</th>
        </tr>
      </thead>
      <tbody>
        {data.map((row) => (
          <tr key={`${row.species}-${row.breed}`} className="border-b border-line last:border-0">
            <td className="px-4 py-2.5 text-sm font-bold text-ink">{row.species}</td>
            <td className="px-4 py-2.5 text-sm text-muted">{row.breed}</td>
            <td className="px-4 py-2.5 text-right text-sm font-bold tabular-nums text-ink">{row.count}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}
