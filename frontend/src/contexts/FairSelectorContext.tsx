import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useAuth } from "./AuthContext";
import { getAssignedFairs, type AssignedFairSummary } from "../api/fair";
import { getAdminDashboardFairs } from "../api/adminDashboard";

export interface FairSelectorState {
  fairId: number | null;
  setFairId: (id: number | null) => void;
  /** 드롭다운에 보여줄 행사 목록 - EVENT_ADMIN은 담당 행사만, SUPER_ADMIN은 운영 중인 전체 행사. */
  selectableFairs: AssignedFairSummary[];
}

/** 마지막으로 고른 행사를 새로고침/페이지 이동 후에도 기억하기 위한 localStorage 키. */
const STORAGE_KEY = "petopia.fairAdmin.selectedFairId";

/**
 * Provider 밖에서 useFairSelector()가 호출돼도 터지지 않도록 throw 대신 안전한 빈 기본값을 준다.
 * VisitStatisticsPage가 콘솔(FairAdminLayout) 밖의 경로(/admin/dashboard/fairs/:fairId)로도
 * 진입하는데, 그 경우 이 컨텍스트 없이 렌더되기 때문이다(그 화면은 URL 경로 파라미터로 동작).
 * AuthContext가 Provider 없으면 throw하는 것과 일부러 다르게 간다.
 */
const FairSelectorContext = createContext<FairSelectorState>({
  fairId: null,
  setFairId: () => {},
  selectableFairs: [],
});

function readStoredFairId(): number | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) return null;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * fair-admin 콘솔 전체가 공유하는 "현재 작업 중인 행사" 상태. FairAdminLayout이 이 Provider로
 * 하위 페이지를 감싸므로, 콘솔 안에서는 어느 페이지로 옮겨도 같은 선택이 유지된다. role에 따라
 * 고를 수 있는 목록을 받아오고(EVENT_ADMIN=배정 행사, SUPER_ADMIN=운영 중/종료 전체), 선택은
 * localStorage에 저장해 새로고침에도 살아남는다.
 */
export function FairSelectorProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const isEventAdmin = user?.role === "EVENT_ADMIN";
  const isSuperAdmin = user?.role === "SUPER_ADMIN";

  const [selectableFairs, setSelectableFairs] = useState<AssignedFairSummary[]>([]);
  const [fairId, setFairIdState] = useState<number | null>(null);

  /** 선택을 바꿀 때 localStorage에도 반영한다(null이면 저장값을 지운다). */
  const setFairId = (id: number | null) => {
    setFairIdState(id);
    try {
      if (id === null) localStorage.removeItem(STORAGE_KEY);
      else localStorage.setItem(STORAGE_KEY, String(id));
    } catch {
      // 프라이빗 모드 등으로 저장이 막혀도 앱 동작엔 지장 없다.
    }
  };

  useEffect(() => {
    // role이나 계정이 바뀌면 이전 사용자의 목록/선택이 남아있지 않도록 먼저 비운다.
    setSelectableFairs([]);
    setFairIdState(null);

    if (!isEventAdmin && !isSuperAdmin) return;
    let ignore = false;

    const request = isEventAdmin
      ? getAssignedFairs()
      : getAdminDashboardFairs().then((fairs) => fairs.map((fair) => ({ fairId: fair.fairId, name: fair.fairName })));

    request
      .then((fairs) => {
        if (ignore) return;
        setSelectableFairs(fairs);
        if (fairs.length === 0) return;
        // 저장된 선택이 지금 목록에 있으면 복원하고, 없으면(담당 해제 등) 첫 행사로 대체한다.
        const stored = readStoredFairId();
        const restored = stored !== null && fairs.some((fair) => fair.fairId === stored) ? stored : fairs[0].fairId;
        setFairIdState((current) => current ?? restored);
      })
      .catch(() => {});

    return () => {
      ignore = true;
    };
  }, [isEventAdmin, isSuperAdmin, user?.userId]);

  return (
    <FairSelectorContext.Provider value={{ fairId, setFairId, selectableFairs }}>
      {children}
    </FairSelectorContext.Provider>
  );
}

/**
 * 콘솔 페이지가 "현재 작업 중인 행사"를 읽고 바꾸는 훅. 반환 shape는 기존 hooks/useFairSelector와
 * 동일해서(호출부 { fairId, setFairId, selectableFairs }) 페이지 코드는 import 경로만 바꾸면 된다.
 */
export function useFairSelector(): FairSelectorState {
  return useContext(FairSelectorContext);
}
