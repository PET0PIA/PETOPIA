import { Outlet } from "react-router-dom";
import { ConsoleChrome } from "../components/layout/ConsoleChrome";
import { superAdminNavigation } from "../config/navigation";

export function SuperAdminLayout() {
  return (
    <ConsoleChrome navigation={superAdminNavigation} consoleLabel="최고 관리자">
      <Outlet />
    </ConsoleChrome>
  );
}
