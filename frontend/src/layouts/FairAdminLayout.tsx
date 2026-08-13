import { Outlet } from "react-router-dom";
import { AdminChrome } from "../components/layout/AdminChrome";
import { FairSelectorProvider } from "../contexts/FairSelectorContext";
import { fairAdminNavigation } from "../config/navigation";

export function FairAdminLayout() {
  return (
    <FairSelectorProvider>
      <AdminChrome navigation={fairAdminNavigation}>
        <Outlet />
      </AdminChrome>
    </FairSelectorProvider>
  );
}
