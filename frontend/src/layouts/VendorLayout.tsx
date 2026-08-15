import { Outlet } from "react-router-dom";
import { ConsoleChrome } from "../components/layout/ConsoleChrome";
import { vendorNavigation } from "../config/navigation";

/**
 * 부스(참여기업) 콘솔 레이아웃. VENDOR 역할 사용자의 참가업체 관리 기능(내 사업자·내 부스·
 * 참가 신청·부스 방문 스캔)을 한 콘솔로 모은다. 상세·수정·신청 퍼널 화면은 공용(PublicLayout)에
 * 그대로 두고, 이 콘솔은 "내 목록" 허브 역할을 한다.
 */
export function VendorLayout() {
  return (
    <ConsoleChrome navigation={vendorNavigation} consoleLabel="부스 관리자">
      <Outlet />
    </ConsoleChrome>
  );
}
