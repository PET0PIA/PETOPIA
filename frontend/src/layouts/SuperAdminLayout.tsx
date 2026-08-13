import { Outlet } from "react-router-dom";
import { AdminChrome } from "../components/layout/AdminChrome";
import { superAdminNavigation } from "../config/navigation";

export function SuperAdminLayout() {
  return (
    <AdminChrome navigation={superAdminNavigation}>
      <Outlet />
    </AdminChrome>
  );
}
