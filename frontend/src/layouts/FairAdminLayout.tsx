import { Outlet } from "react-router-dom";
import { ConsoleChrome } from "../components/layout/ConsoleChrome";
import { FairSelectorProvider, useFairSelector } from "../contexts/FairSelectorContext";
import { fairAdminNavigation } from "../config/navigation";

/**
 * 선택된 행사를 key로 삼아 라우트 페이지를 통째로 remount한다. 헤더 스위처로 행사를 바꾸면
 * 페이지가 새로 마운트되며 열려 있던 편집 다이얼로그·선택 항목·진행 중 상태가 전부 리셋된다.
 * (안 그러면 이전 행사에서 열어둔 다이얼로그를 새 행사에 저장하는 경쟁 상태가 생긴다.)
 * FairSwitcher는 ConsoleChrome 상단 바에 있어 이 remount에 영향받지 않는다.
 */
function FairScopedOutlet() {
  const { fairId } = useFairSelector();
  return <Outlet key={fairId ?? "none"} />;
}

export function FairAdminLayout() {
  return (
    <FairSelectorProvider>
      <ConsoleChrome navigation={fairAdminNavigation} consoleLabel="행사 관리자">
        <FairScopedOutlet />
      </ConsoleChrome>
    </FairSelectorProvider>
  );
}
