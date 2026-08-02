import { Outlet } from "react-router-dom";
import { AdminChrome } from "../components/layout/AdminChrome";
import { fairAdminNavigation } from "../config/navigation";

export function FairAdminLayout() { return <AdminChrome navigation={fairAdminNavigation} accountName="행사 관리자 김페어" fairName="2026 서울 펫페어"><Outlet /></AdminChrome>; }
