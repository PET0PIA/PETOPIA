import type { ReactNode } from "react";
import { Reveal } from "../common/Reveal";

/**
 * 홈의 섹션 한 칸. 화면 전체 폭 배경을 깔고, 그 안에서만 본문 폭(page-shell)을 맞춘다.
 * 흰색(page)과 아주 연한 회색(alt)을 번갈아 쓰면 스크롤할 때 섹션 경계가 생겨서
 * 긴 페이지가 한 덩어리로 늘어져 보이지 않는다.
 *
 * <p>이 래퍼를 홈 페이지(HomePage)가 아니라 각 섹션 컴포넌트가 직접 두르는 이유:
 * 섹션은 보여줄 데이터가 없으면 스스로 null을 반환해 사라지는데(공지 띠·배너와 같은 규칙),
 * 배경 띠를 바깥에서 두르면 내용만 사라지고 색칠된 빈 줄이 남아 화면이 고장 난 것처럼 보인다.
 */
export function HomeBand({ tone = "page", children }: { tone?: "page" | "alt"; children: ReactNode }) {
  return (
    <div className={tone === "alt" ? "bg-surface-alt" : "bg-page"}>
      <Reveal className="page-shell py-12 sm:py-16">{children}</Reveal>
    </div>
  );
}
