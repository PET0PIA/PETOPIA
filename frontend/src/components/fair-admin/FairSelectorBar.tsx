import type { AssignedFairSummary } from "../../api/fair";
import { Select } from "../ui/Select";

interface FairSelectorBarProps {
  selectableFairs: AssignedFairSummary[];
  fairId: number | null;
  onFairIdChange: (id: number) => void;
  /** 고를 수 있는 행사가 하나도 없을 때 보여줄 문구 */
  emptyLabel?: string;
  /** 드롭다운 위 레이블 */
  label?: string;
}

/**
 * fair-admin 페이지 상단에 공통으로 들어가는 행사 선택 바. 이름으로 표시되는 드롭다운 하나로
 * EVENT_ADMIN(담당 행사)과 SUPER_ADMIN(전체 운영 행사)을 동일하게 처리한다 - 목록은
 * useFairSelector가 role에 맞게 채워준다.
 */
export function FairSelectorBar({
  selectableFairs,
  fairId,
  onFairIdChange,
  emptyLabel = "선택할 수 있는 행사가 없어요.",
  label = "관리할 행사",
}: FairSelectorBarProps) {
  if (selectableFairs.length === 0) {
    return (
      <div className="surface mb-6 p-5 text-sm text-muted">{emptyLabel}</div>
    );
  }

  return (
    <div className="surface mb-6 p-5">
      <label htmlFor="fair-selector-id" className="mb-1.5 block text-sm font-bold text-ink">{label}</label>
      <Select
        id="fair-selector-id"
        value={fairId ?? selectableFairs[0].fairId}
        onChange={(event) => onFairIdChange(Number(event.target.value))}
      >
        {selectableFairs.map((fair) => (
          <option key={fair.fairId} value={fair.fairId}>
            {fair.name}
          </option>
        ))}
      </Select>
    </div>
  );
}
