import { useEffect, useState } from "react";
import { useAuth } from "../contexts/AuthContext";
import { getAssignedFairs, type AssignedFairSummary } from "../api/fair";
import { getAdminDashboardFairs } from "../api/adminDashboard";

export interface FairSelectorState {
  fairId: number | null;
  setFairId: (id: number | null) => void;
  /** 드롭다운에 보여줄 행사 목록 - EVENT_ADMIN은 담당 행사만, SUPER_ADMIN은 운영 중인 전체 행사. */
  selectableFairs: AssignedFairSummary[];
}

/**
 * fair-admin 페이지가 사용하는 행사 선택 훅. role에 따라 고를 수 있는 행사 목록을 받아와
 * 첫 번째 행사를 자동 선택한다 - EVENT_ADMIN은 배정된 행사, SUPER_ADMIN은 운영 중/종료된 전체
 * 행사(PREPARING/IN_PROGRESS/ENDED, 심사 대기 중인 행사는 통계가 의미 없어 제외됨).
 */
export function useFairSelector(): FairSelectorState {
  const { user } = useAuth();
  const isEventAdmin = user?.role === "EVENT_ADMIN";
  const isSuperAdmin = user?.role === "SUPER_ADMIN";

  const [selectableFairs, setSelectableFairs] = useState<AssignedFairSummary[]>([]);
  const [fairId, setFairId] = useState<number | null>(null);

  useEffect(() => {
    if (!isEventAdmin && !isSuperAdmin) return;
    let ignore = false;

    const request = isEventAdmin
      ? getAssignedFairs()
      : getAdminDashboardFairs().then((fairs) => fairs.map((fair) => ({ fairId: fair.fairId, name: fair.fairName })));

    request
      .then((fairs) => {
        if (ignore) return;
        setSelectableFairs(fairs);
        if (fairs.length > 0) setFairId((current) => current ?? fairs[0].fairId);
      })
      .catch(() => {});

    return () => { ignore = true; };
  }, [isEventAdmin, isSuperAdmin]);

  return { fairId, setFairId, selectableFairs };
}
