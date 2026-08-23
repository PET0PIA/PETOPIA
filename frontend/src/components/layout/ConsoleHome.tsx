import { ChevronRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import type { NavigationItem } from "../../config/navigation";
import { getMe } from "../../api/user";
import { Card } from "../ui/Card";
import { PageHeader } from "../common/PageHeader";

interface ConsoleHomeProps {
  /** 콘솔 이름. eyebrow로 노출한다. 예: "행사 관리자". */
  consoleLabel: string;
  /** 홈 상단 안내 문구. */
  description: string;
  /** 콘솔 네비게이션(그룹 = 카드, path만 있는 항목 = 바로가기 카드). */
  navigation: NavigationItem[];
}

/**
 * 콘솔에 들어왔을 때 처음 만나는 홈(랜딩). 작업 페이지로 곧장 떨어지지 않도록 인사말과
 * 영역별 바로가기 카드를 보여줘 "지금 어떤 콘솔에 있고 뭘 할 수 있는지"를 한눈에 준다.
 * 카드 내용은 콘솔 네비게이션(navigation.ts)을 그대로 재사용한다 - 메뉴와 항상 일치한다.
 */
export function ConsoleHome({ consoleLabel, description, navigation }: ConsoleHomeProps) {
  const [nickname, setNickname] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    getMe()
      .then((me) => { if (active) setNickname(me.nickname); })
      .catch(() => {});
    return () => { active = false; };
  }, []);

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader
        eyebrow={consoleLabel}
        title={nickname ? `안녕하세요, ${nickname}님` : "안녕하세요"}
        description={description}
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {navigation.map((item) => {
          const Icon = item.icon;

          // 단일 링크(대시보드 등): 카드 전체가 바로가기.
          if (!item.children?.length) {
            return (
              <Link
                key={item.label}
                to={item.path ?? "#"}
                className="surface group flex items-center gap-3 p-5 transition hover:border-primary"
              >
                {Icon && (
                  <span className="grid size-10 shrink-0 place-items-center rounded-card bg-primary-soft text-primary-strong">
                    <Icon size={20} />
                  </span>
                )}
                <span className="flex-1 text-base font-extrabold text-ink">{item.label}</span>
                <ChevronRight size={18} className="text-muted transition group-hover:translate-x-0.5 group-hover:text-primary-strong" />
              </Link>
            );
          }

          // 그룹: 제목 + 하위 항목 목록 카드.
          return (
            <Card key={item.label} className="p-5">
              <div className="mb-3 flex items-center gap-2">
                {Icon && <Icon size={18} className="text-primary-strong" />}
                <h2 className="text-base font-extrabold text-ink">{item.label}</h2>
              </div>
              <ul className="flex flex-col gap-0.5">
                {item.children.map((child) => {
                  const ChildIcon = child.icon;
                  return (
                    <li key={child.label}>
                      <Link
                        to={child.path ?? "#"}
                        className="group flex items-center gap-2 rounded-button px-2 py-2 text-sm text-muted hover:bg-page hover:text-ink"
                      >
                        {ChildIcon && <ChildIcon size={16} className="shrink-0" />}
                        <span className="flex-1">{child.label}</span>
                        <ChevronRight size={15} className="shrink-0 opacity-0 transition group-hover:opacity-100" />
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
