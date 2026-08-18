import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  createAdminMenu,
  deactivateAdminMenu,
  fetchAdminMenus,
  fetchBusinessHours,
  fetchChatSettings,
  fetchChatStats,
  saveBusinessHours,
  saveChatSettings,
  updateAdminMenu,
  type AdminChatMenu,
  type ChatBusinessHour,
  type ChatMenuStat,
  type ChatSetting,
} from "../../api/adminChatSettings";
import type { ChatAnswerType } from "../../api/chat";
import { ApiError } from "../../api/client";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { Button } from "../../components/ui/Button";

/**
 * 상담 운영 설정.
 *
 * 이 화면의 목적은 하나다 - 문구 한 줄, 버튼 하나 바꾸려고 배포하지 않게 하는 것.
 * 배포가 필요하면 결국 아무도 안 고치고 초기 시딩 값이 그대로 운영에 남는다.
 */

/** 1=월 ... 7=일. 서버(java.time.DayOfWeek)와 같은 규칙이라 인덱스 변환을 하지 않는다. */
const DAY_LABELS = ["월", "화", "수", "목", "금", "토", "일"];

const ANSWER_TYPE_LABELS: Record<ChatAnswerType, string> = {
  FIXED: "고정 답변",
  AI: "AI 답변(운영시간 외 1회)",
  AGENT: "상담사 연결",
};

const EMPTY_MENU = {
  code: "",
  label: "",
  answerType: "FIXED" as ChatAnswerType,
  fixedAnswer: "",
  aiContext: "",
};

function formatSeconds(seconds: number | null): string {
  // null은 "아직 아무도 답하지 않음"이다. 0분으로 표시하면 즉시 응답한 것처럼 읽힌다.
  if (seconds == null) return "—";
  if (seconds < 60) return `${Math.round(seconds)}초`;
  const minutes = Math.round(seconds / 60);
  return minutes < 60 ? `${minutes}분` : `${Math.round(minutes / 60)}시간`;
}

export function AdminChatSettingsPage() {
  const [menus, setMenus] = useState<AdminChatMenu[]>([]);
  const [hours, setHours] = useState<ChatBusinessHour[]>([]);
  const [settings, setSettings] = useState<ChatSetting[]>([]);
  const [stats, setStats] = useState<ChatMenuStat[]>([]);
  const [draft, setDraft] = useState(EMPTY_MENU);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let canceled = false;
    Promise.all([fetchAdminMenus(), fetchBusinessHours(), fetchChatSettings(), fetchChatStats()])
      .then(([menuList, hourList, settingList, statList]) => {
        if (canceled) return;
        setMenus(menuList);
        setHours(hourList);
        setSettings(settingList);
        setStats(statList);
      })
      .catch(() => {
        if (!canceled) setError("설정을 불러오지 못했어요.");
      });
    return () => {
      canceled = true;
    };
  }, []);

  const notify = (text: string) => {
    setMessage(text);
    setError(null);
  };

  const handleError = (caught: unknown, fallback: string) => {
    setError(caught instanceof ApiError ? caught.message : fallback);
    setMessage(null);
  };

  const patchMenu = (menuId: number, patch: Partial<AdminChatMenu>) => {
    setMenus((current) =>
      current.map((menu) => (menu.menuId === menuId ? { ...menu, ...patch } : menu)),
    );
  };

  const saveMenu = async (menu: AdminChatMenu) => {
    try {
      await updateAdminMenu(menu.menuId, {
        code: menu.code,
        label: menu.label,
        answerType: menu.answerType,
        fixedAnswer: menu.fixedAnswer,
        aiContext: menu.aiContext,
        displayOrder: menu.displayOrder,
        isActive: menu.isActive,
      });
      notify(`'${menu.label}' 저장했어요.`);
    } catch (caught) {
      handleError(caught, "버튼을 저장하지 못했어요.");
    }
  };

  const addMenu = async () => {
    try {
      const created = await createAdminMenu({
        ...draft,
        displayOrder: menus.length + 1,
        isActive: true,
      });
      setMenus((current) => [...current, created]);
      setDraft(EMPTY_MENU);
      notify("새 버튼을 추가했어요.");
    } catch (caught) {
      handleError(caught, "버튼을 추가하지 못했어요.");
    }
  };

  const removeMenu = async (menu: AdminChatMenu) => {
    try {
      await deactivateAdminMenu(menu.menuId);
      patchMenu(menu.menuId, { isActive: false });
      notify(`'${menu.label}'을(를) 내렸어요. 과거 상담 기록은 그대로 남아요.`);
    } catch (caught) {
      handleError(caught, "버튼을 내리지 못했어요.");
    }
  };

  const saveHours = async () => {
    try {
      await saveBusinessHours(hours);
      notify("운영시간을 저장했어요.");
    } catch (caught) {
      handleError(caught, "운영시간을 저장하지 못했어요.");
    }
  };

  const saveTexts = async () => {
    try {
      await saveChatSettings(settings);
      notify("문구를 저장했어요.");
    } catch (caught) {
      handleError(caught, "문구를 저장하지 못했어요.");
    }
  };

  return (
    <div className="space-y-10">
      <PageHeader
        title="상담 설정"
        description="문의 버튼, 운영시간, 안내 문구를 배포 없이 바꿀 수 있어요."
        action={
          <Link
            to="/admin/chat"
            className="inline-flex min-h-11 items-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page"
          >
            상담 목록으로
          </Link>
        }
      />

      {message && <p className="text-sm font-bold text-ink">{message}</p>}
      {error && (
        <p role="alert" className="text-sm text-muted">
          {error}
        </p>
      )}

      {/* 지표 */}
      <section>
        <SectionHeader title="최근 30일 지표" description="문의 유형별 접수 건수와 첫 응답까지 걸린 평균 시간이에요." />
        <div className="surface overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-line text-left text-xs text-muted">
              <tr>
                <th className="px-4 py-3">문의 유형</th>
                <th className="px-4 py-3">접수</th>
                <th className="px-4 py-3">대기</th>
                <th className="px-4 py-3">AI 답변</th>
                <th className="px-4 py-3">평균 첫 응답</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {stats.map((stat) => (
                <tr key={stat.menuLabel}>
                  <td className="px-4 py-3 font-bold text-ink">{stat.menuLabel}</td>
                  <td className="px-4 py-3">{stat.conversationCount}</td>
                  <td className="px-4 py-3">{stat.waitingCount}</td>
                  <td className="px-4 py-3">{stat.aiAnsweredCount}</td>
                  <td className="px-4 py-3">{formatSeconds(stat.avgFirstResponseSeconds)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* 버튼 */}
      <section>
        <SectionHeader
          title="문의 유형 버튼"
          description="위젯을 열었을 때 보이는 버튼이에요. AI 답변은 운영시간 외에 대화당 1회만 동작해요."
        />
        <div className="space-y-3">
          {menus.map((menu) => (
            <div key={menu.menuId} className={`surface p-4 ${menu.isActive ? "" : "opacity-60"}`}>
              <div className="grid gap-3 sm:grid-cols-[1fr_200px]">
                <div>
                  <label className="text-xs font-bold text-muted" htmlFor={`label-${menu.menuId}`}>
                    버튼 문구
                  </label>
                  <input
                    id={`label-${menu.menuId}`}
                    value={menu.label}
                    onChange={(event) => patchMenu(menu.menuId, { label: event.target.value })}
                    className="mt-1 min-h-11 w-full rounded-button border border-line bg-card px-3 text-sm"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-muted" htmlFor={`type-${menu.menuId}`}>
                    답변 유형
                  </label>
                  <select
                    id={`type-${menu.menuId}`}
                    value={menu.answerType}
                    onChange={(event) =>
                      patchMenu(menu.menuId, { answerType: event.target.value as ChatAnswerType })
                    }
                    className="mt-1 min-h-11 w-full rounded-button border border-line bg-card px-3 text-sm"
                  >
                    {Object.entries(ANSWER_TYPE_LABELS).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {menu.answerType === "FIXED" && (
                <div className="mt-3">
                  <label className="text-xs font-bold text-muted" htmlFor={`fixed-${menu.menuId}`}>
                    고정 답변
                  </label>
                  <textarea
                    id={`fixed-${menu.menuId}`}
                    rows={3}
                    value={menu.fixedAnswer ?? ""}
                    onChange={(event) => patchMenu(menu.menuId, { fixedAnswer: event.target.value })}
                    className="mt-1 w-full rounded-button border border-line bg-card px-3 py-2 text-sm"
                  />
                </div>
              )}

              {menu.answerType === "AI" && (
                <div className="mt-3">
                  <label className="text-xs font-bold text-muted" htmlFor={`ai-${menu.menuId}`}>
                    AI 참고 정보
                  </label>
                  <p className="mt-1 text-xs text-muted">
                    AI는 여기 적힌 내용 안에서만 답해요. 비어 있으면 대부분 상담사에게 넘깁니다.
                  </p>
                  <textarea
                    id={`ai-${menu.menuId}`}
                    rows={4}
                    value={menu.aiContext ?? ""}
                    onChange={(event) => patchMenu(menu.menuId, { aiContext: event.target.value })}
                    className="mt-1 w-full rounded-button border border-line bg-card px-3 py-2 text-sm"
                  />
                </div>
              )}

              <div className="mt-3 flex items-center gap-2">
                <Button onClick={() => saveMenu(menu)} className="min-h-9 text-xs">
                  저장
                </Button>
                {menu.isActive ? (
                  <Button variant="outline" onClick={() => removeMenu(menu)} className="min-h-9 text-xs">
                    내리기
                  </Button>
                ) : (
                  <Button
                    variant="outline"
                    onClick={() => saveMenu({ ...menu, isActive: true })}
                    className="min-h-9 text-xs"
                  >
                    다시 올리기
                  </Button>
                )}
                <span className="text-xs text-muted">코드: {menu.code}</span>
              </div>
            </div>
          ))}
        </div>

        {/* 새 버튼 */}
        <div className="surface mt-4 p-4">
          <p className="mb-3 text-sm font-bold text-ink">새 버튼 추가</p>
          <div className="grid gap-3 sm:grid-cols-3">
            <input
              value={draft.code}
              onChange={(event) => setDraft({ ...draft, code: event.target.value.toUpperCase() })}
              placeholder="코드 (예: REFUND_INQUIRY)"
              aria-label="새 버튼 코드"
              className="min-h-11 rounded-button border border-line bg-card px-3 text-sm"
            />
            <input
              value={draft.label}
              onChange={(event) => setDraft({ ...draft, label: event.target.value })}
              placeholder="버튼 문구"
              aria-label="새 버튼 문구"
              className="min-h-11 rounded-button border border-line bg-card px-3 text-sm"
            />
            <select
              value={draft.answerType}
              onChange={(event) => setDraft({ ...draft, answerType: event.target.value as ChatAnswerType })}
              aria-label="새 버튼 답변 유형"
              className="min-h-11 rounded-button border border-line bg-card px-3 text-sm"
            >
              {Object.entries(ANSWER_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <Button
            onClick={addMenu}
            disabled={!draft.code.trim() || !draft.label.trim()}
            className="mt-3 min-h-9 text-xs"
          >
            추가
          </Button>
        </div>
      </section>

      {/* 운영시간 */}
      <section>
        <SectionHeader
          title="상담 운영시간"
          description="이 시간 밖에 들어온 AI 유형 문의는 AI가 먼저 1회 답변해요."
        />
        <div className="surface p-4">
          <div className="space-y-2">
            {hours.map((hour, index) => (
              <div key={hour.dayOfWeek} className="flex flex-wrap items-center gap-3">
                <span className="w-8 text-sm font-bold text-ink">{DAY_LABELS[hour.dayOfWeek - 1]}</span>
                <label className="flex items-center gap-2 text-xs text-muted">
                  <input
                    type="checkbox"
                    checked={hour.isActive}
                    onChange={(event) =>
                      setHours(hours.map((h, i) => (i === index ? { ...h, isActive: event.target.checked } : h)))
                    }
                  />
                  운영
                </label>
                <input
                  type="time"
                  value={hour.startTime.slice(0, 5)}
                  aria-label={`${DAY_LABELS[hour.dayOfWeek - 1]}요일 시작 시각`}
                  onChange={(event) =>
                    setHours(hours.map((h, i) => (i === index ? { ...h, startTime: `${event.target.value}:00` } : h)))
                  }
                  className="min-h-11 rounded-button border border-line bg-card px-3 text-sm"
                />
                <span className="text-muted">~</span>
                <input
                  type="time"
                  value={hour.endTime.slice(0, 5)}
                  aria-label={`${DAY_LABELS[hour.dayOfWeek - 1]}요일 종료 시각`}
                  onChange={(event) =>
                    setHours(hours.map((h, i) => (i === index ? { ...h, endTime: `${event.target.value}:00` } : h)))
                  }
                  className="min-h-11 rounded-button border border-line bg-card px-3 text-sm"
                />
              </div>
            ))}
          </div>
          <Button onClick={saveHours} className="mt-4 min-h-9 text-xs">
            운영시간 저장
          </Button>
        </div>
      </section>

      {/* 문구 */}
      <section>
        <SectionHeader title="안내 문구" description="위젯에 그대로 노출되는 문구예요." />
        <div className="surface space-y-4 p-4">
          {settings.map((setting, index) => (
            <div key={setting.settingKey}>
              <label className="text-xs font-bold text-muted" htmlFor={`setting-${setting.settingKey}`}>
                {setting.description ?? setting.settingKey}
              </label>
              <textarea
                id={`setting-${setting.settingKey}`}
                rows={2}
                value={setting.settingValue}
                onChange={(event) =>
                  setSettings(
                    settings.map((s, i) => (i === index ? { ...s, settingValue: event.target.value } : s)),
                  )
                }
                className="mt-1 w-full rounded-button border border-line bg-card px-3 py-2 text-sm"
              />
            </div>
          ))}
          <Button onClick={saveTexts} className="min-h-9 text-xs">
            문구 저장
          </Button>
        </div>
      </section>
    </div>
  );
}
