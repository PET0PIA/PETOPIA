import { AlertCircle, Check } from "lucide-react";
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
  type AdminChatMenuPayload,
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
 *
 * <p>저장 결과를 페이지 최상단에만 띄우면 안 된다. 이 화면은 네 섹션이 세로로 길게 붙어
 * 있어서, 아래쪽 버튼을 눌렀을 때 결과 문구가 스크롤 밖에 그려지면 "눌러도 아무 일도
 * 안 난다"로 읽힌다. 진행 상태는 눌린 버튼 자체에, 결과는 그 버튼 옆에 붙인다.
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

/** 아직 DB에 행이 없는 요일의 초기값. 저장을 눌러야 실제로 생긴다. */
const UNSET_HOUR = { startTime: "09:00:00", endTime: "18:00:00", isActive: false };

/** 성공 문구가 계속 남아 있으면 다음 저장이 먹었는지 알 수 없다. 실패는 읽을 시간이 필요해 남긴다. */
const OK_MESSAGE_MS = 3000;

/** 결과 문구를 어느 버튼 옆에 붙일지 - slot이 그 위치를 가리킨다. */
interface Feedback {
  slot: string;
  text: string;
  kind: "ok" | "error";
}

function formatSeconds(seconds: number | null): string {
  // null은 "아직 아무도 답하지 않음"이다. 0분으로 표시하면 즉시 응답한 것처럼 읽힌다.
  if (seconds == null) return "—";
  if (seconds < 60) return `${Math.round(seconds)}초`;
  const minutes = Math.round(seconds / 60);
  return minutes < 60 ? `${minutes}분` : `${Math.round(minutes / 60)}시간`;
}

/** code는 수정 시 서버가 무시하지만 @NotBlank라서 채워 보낸다. */
function toPayload(menu: AdminChatMenu): AdminChatMenuPayload {
  return {
    code: menu.code,
    label: menu.label,
    answerType: menu.answerType,
    fixedAnswer: menu.fixedAnswer,
    aiContext: menu.aiContext,
    displayOrder: menu.displayOrder,
    isActive: menu.isActive,
  };
}

/**
 * 일곱 요일을 모두 채운다.
 *
 * 조회된 행만 그리면 마이그레이션이 시딩한 평일 5개만 화면에 남아 토·일요일을 추가할
 * 방법이 아예 없다. 저장은 요일 단위 upsert라, 없는 요일도 그려두면 그대로 새 행이 된다.
 */
function fillMissingDays(saved: ChatBusinessHour[]): ChatBusinessHour[] {
  return DAY_LABELS.map((_, index) => {
    const dayOfWeek = index + 1;
    return saved.find((hour) => hour.dayOfWeek === dayOfWeek) ?? { dayOfWeek, ...UNSET_HOUR };
  });
}

/** time 입력은 비울 수 있고 그때 값은 빈 문자열이다. 그대로 ":00"을 붙여 보내면 400이 난다. */
function toApiTime(value: string, previous: string): string {
  return value ? `${value}:00` : previous;
}

export function AdminChatSettingsPage() {
  const [menus, setMenus] = useState<AdminChatMenu[]>([]);
  const [hours, setHours] = useState<ChatBusinessHour[]>([]);
  const [settings, setSettings] = useState<ChatSetting[]>([]);
  const [stats, setStats] = useState<ChatMenuStat[]>([]);
  const [draft, setDraft] = useState(EMPTY_MENU);
  const [loadError, setLoadError] = useState<string | null>(null);
  /** 진행 중인 동작. 저장이 겹치면 어느 결과가 화면에 남는지 알 수 없어 한 번에 하나만 받는다. */
  const [running, setRunning] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  useEffect(() => {
    let canceled = false;
    Promise.all([fetchAdminMenus(), fetchBusinessHours(), fetchChatSettings(), fetchChatStats()])
      .then(([menuList, hourList, settingList, statList]) => {
        if (canceled) return;
        setMenus(menuList);
        setHours(fillMissingDays(hourList));
        setSettings(settingList);
        setStats(statList);
      })
      .catch(() => {
        if (!canceled) setLoadError("설정을 불러오지 못했어요.");
      });
    return () => {
      canceled = true;
    };
  }, []);

  useEffect(() => {
    if (feedback?.kind !== "ok") return;
    const timer = window.setTimeout(() => setFeedback(null), OK_MESSAGE_MS);
    return () => window.clearTimeout(timer);
  }, [feedback]);

  /** 저장 동작 하나를 감싼다. action은 어느 버튼이 도는지, slot은 결과를 어디에 붙일지. */
  const run = async (
    action: string,
    slot: string,
    okText: string,
    fallback: string,
    task: () => Promise<void>,
  ) => {
    if (running) return;
    setRunning(action);
    setFeedback(null);
    try {
      await task();
      setFeedback({ slot, text: okText, kind: "ok" });
    } catch (caught) {
      setFeedback({
        slot,
        text: caught instanceof ApiError ? caught.message : fallback,
        kind: "error",
      });
    } finally {
      setRunning(null);
    }
  };

  const feedbackFor = (slot: string) => {
    if (feedback?.slot !== slot) return null;
    const isError = feedback.kind === "error";
    return (
      <span
        role={isError ? "alert" : "status"}
        className={`inline-flex items-center gap-1 text-xs ${
          isError ? "font-bold text-primary-strong" : "text-muted"
        }`}
      >
        {isError ? <AlertCircle size={14} className="shrink-0" /> : <Check size={14} className="shrink-0" />}
        {feedback.text}
      </span>
    );
  };

  const patchMenu = (menuId: number, patch: Partial<AdminChatMenu>) => {
    setMenus((current) =>
      current.map((menu) => (menu.menuId === menuId ? { ...menu, ...patch } : menu)),
    );
  };

  const patchHour = (dayOfWeek: number, patch: Partial<ChatBusinessHour>) => {
    setHours((current) =>
      current.map((hour) => (hour.dayOfWeek === dayOfWeek ? { ...hour, ...patch } : hour)),
    );
  };

  const patchSetting = (settingKey: string, settingValue: string) => {
    setSettings((current) =>
      current.map((setting) =>
        setting.settingKey === settingKey ? { ...setting, settingValue } : setting,
      ),
    );
  };

  const saveMenu = (menu: AdminChatMenu) =>
    run(
      `save:${menu.menuId}`,
      `menu:${menu.menuId}`,
      `'${menu.label}' 저장했어요.`,
      "버튼을 저장하지 못했어요.",
      async () => {
        // 응답을 버리면 서버가 정규화한 순서·활성 여부가 화면에 반영되지 않아
        // 저장이 안 된 것처럼 보인다.
        patchMenu(menu.menuId, await updateAdminMenu(menu.menuId, toPayload(menu)));
      },
    );

  const restoreMenu = (menu: AdminChatMenu) =>
    run(
      `toggle:${menu.menuId}`,
      `menu:${menu.menuId}`,
      `'${menu.label}'을(를) 다시 올렸어요.`,
      "버튼을 올리지 못했어요.",
      async () => {
        patchMenu(
          menu.menuId,
          await updateAdminMenu(menu.menuId, toPayload({ ...menu, isActive: true })),
        );
      },
    );

  const removeMenu = (menu: AdminChatMenu) =>
    run(
      `toggle:${menu.menuId}`,
      `menu:${menu.menuId}`,
      `'${menu.label}'을(를) 내렸어요. 과거 상담 기록은 그대로 남아요.`,
      "버튼을 내리지 못했어요.",
      async () => {
        await deactivateAdminMenu(menu.menuId);
        patchMenu(menu.menuId, { isActive: false });
      },
    );

  const addMenu = () =>
    run("create", "create", "새 버튼을 추가했어요.", "버튼을 추가하지 못했어요.", async () => {
      // displayOrder를 보내지 않는다 - 서버가 현재 최대값 + 1로 계산한다. 화면의 버튼
      // 개수로 계산하면 비활성 버튼이 섞이거나 순서가 띄워져 있을 때 기존 값과 겹친다.
      const created = await createAdminMenu({ ...draft, isActive: true });
      setMenus((current) => [...current, created]);
      setDraft(EMPTY_MENU);
    });

  const saveHours = () =>
    run("hours", "hours", "운영시간을 저장했어요.", "운영시간을 저장하지 못했어요.", () =>
      saveBusinessHours(hours),
    );

  const saveTexts = () =>
    run("texts", "texts", "문구를 저장했어요.", "문구를 저장하지 못했어요.", () =>
      saveChatSettings(settings),
    );

  const busy = running !== null;

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

      {loadError && (
        <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p role="alert">{loadError}</p>
        </div>
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

              <div className="mt-3 flex flex-wrap items-center gap-2">
                <Button onClick={() => saveMenu(menu)} disabled={busy} className="min-h-9 text-xs">
                  {running === `save:${menu.menuId}` ? "저장중…" : "저장"}
                </Button>
                {menu.isActive ? (
                  <Button
                    variant="outline"
                    onClick={() => removeMenu(menu)}
                    disabled={busy}
                    className="min-h-9 text-xs"
                  >
                    {running === `toggle:${menu.menuId}` ? "내리는 중…" : "내리기"}
                  </Button>
                ) : (
                  <Button
                    variant="outline"
                    onClick={() => restoreMenu(menu)}
                    disabled={busy}
                    className="min-h-9 text-xs"
                  >
                    {running === `toggle:${menu.menuId}` ? "올리는 중…" : "다시 올리기"}
                  </Button>
                )}
                <span className="text-xs text-muted">코드: {menu.code}</span>
                {feedbackFor(`menu:${menu.menuId}`)}
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
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Button
              onClick={addMenu}
              disabled={busy || !draft.code.trim() || !draft.label.trim()}
              className="min-h-9 text-xs"
            >
              {running === "create" ? "추가중…" : "추가"}
            </Button>
            {feedbackFor("create")}
          </div>
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
            {hours.map((hour) => (
              <div key={hour.dayOfWeek} className="flex flex-wrap items-center gap-3">
                <span className="w-8 text-sm font-bold text-ink">{DAY_LABELS[hour.dayOfWeek - 1]}</span>
                <label className="flex items-center gap-2 text-xs text-muted">
                  <input
                    type="checkbox"
                    checked={hour.isActive}
                    onChange={(event) => patchHour(hour.dayOfWeek, { isActive: event.target.checked })}
                  />
                  운영
                </label>
                <input
                  type="time"
                  value={hour.startTime.slice(0, 5)}
                  aria-label={`${DAY_LABELS[hour.dayOfWeek - 1]}요일 시작 시각`}
                  onChange={(event) =>
                    patchHour(hour.dayOfWeek, { startTime: toApiTime(event.target.value, hour.startTime) })
                  }
                  className="min-h-11 rounded-button border border-line bg-card px-3 text-sm"
                />
                <span className="text-muted">~</span>
                <input
                  type="time"
                  value={hour.endTime.slice(0, 5)}
                  aria-label={`${DAY_LABELS[hour.dayOfWeek - 1]}요일 종료 시각`}
                  onChange={(event) =>
                    patchHour(hour.dayOfWeek, { endTime: toApiTime(event.target.value, hour.endTime) })
                  }
                  className="min-h-11 rounded-button border border-line bg-card px-3 text-sm"
                />
              </div>
            ))}
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <Button onClick={saveHours} disabled={busy} className="min-h-9 text-xs">
              {running === "hours" ? "저장중…" : "운영시간 저장"}
            </Button>
            {feedbackFor("hours")}
          </div>
        </div>
      </section>

      {/* 문구 */}
      <section>
        <SectionHeader title="안내 문구" description="위젯에 그대로 노출되는 문구예요." />
        <div className="surface space-y-4 p-4">
          {settings.map((setting) => (
            <div key={setting.settingKey}>
              <label className="text-xs font-bold text-muted" htmlFor={`setting-${setting.settingKey}`}>
                {setting.description ?? setting.settingKey}
              </label>
              <textarea
                id={`setting-${setting.settingKey}`}
                rows={2}
                value={setting.settingValue}
                onChange={(event) => patchSetting(setting.settingKey, event.target.value)}
                className="mt-1 w-full rounded-button border border-line bg-card px-3 py-2 text-sm"
              />
            </div>
          ))}
          <div className="flex flex-wrap items-center gap-2">
            <Button onClick={saveTexts} disabled={busy} className="min-h-9 text-xs">
              {running === "texts" ? "저장중…" : "문구 저장"}
            </Button>
            {feedbackFor("texts")}
          </div>
        </div>
      </section>
    </div>
  );
}
