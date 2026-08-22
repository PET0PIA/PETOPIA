import type { LabelCount } from "../../api/statistics";
import { Table } from "../ui/Table";

interface PetAllergyBreakdownProps {
  data: LabelCount[];
}

/** 반려동물 알레르기 분포. count DESC로 이미 정렬되어 내려온다. 알레르기가 없는 반려동물은 집계에서 빠진다. */
export function PetAllergyBreakdown({ data }: PetAllergyBreakdownProps) {
  if (data.length === 0) {
    return <p className="py-4 text-center text-sm text-muted">아직 데이터가 없어요.</p>;
  }

  return (
    <Table>
      <thead>
        <tr className="border-b border-line text-xs font-bold text-muted">
          <th className="px-4 py-2.5">알레르기</th>
          <th className="px-4 py-2.5 text-right">건수</th>
        </tr>
      </thead>
      <tbody>
        {data.map((row) => (
          <tr key={row.label} className="border-b border-line last:border-0">
            <td className="px-4 py-2.5 text-sm font-bold text-ink">{row.label}</td>
            <td className="px-4 py-2.5 text-right text-sm font-bold tabular-nums text-ink">{row.count}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}
