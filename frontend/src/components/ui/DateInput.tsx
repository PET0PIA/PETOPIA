import { CalendarDays } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type ClipboardEvent, type KeyboardEvent } from "react";

type DateInputType = "date" | "datetime-local";

interface DateInputProps {
  id?: string;
  /** ISO 형식. type="date"면 "YYYY-MM-DD", type="datetime-local"면 "YYYY-MM-DDTHH:mm". 빈 값이면 "". */
  value: string;
  onChange: (value: string) => void;
  type?: DateInputType;
  required?: boolean;
  disabled?: boolean;
  min?: string;
  max?: string;
  className?: string;
  "aria-label"?: string;
}

type SegmentKey = "y" | "m" | "d" | "hh" | "mm";

interface SegmentDef {
  key: SegmentKey;
  maxLen: number;
  bounds: { min: number; max: number } | null; // null이면 연도(자유 입력)
  placeholder: string;
  label: string;
}

const DATE_SEGMENTS: SegmentDef[] = [
  { key: "y", maxLen: 4, bounds: null, placeholder: "YYYY", label: "연도" },
  { key: "m", maxLen: 2, bounds: { min: 1, max: 12 }, placeholder: "MM", label: "월" },
  { key: "d", maxLen: 2, bounds: { min: 1, max: 31 }, placeholder: "DD", label: "일" },
];
const DATETIME_SEGMENTS: SegmentDef[] = [
  ...DATE_SEGMENTS,
  { key: "hh", maxLen: 2, bounds: { min: 0, max: 23 }, placeholder: "HH", label: "시" },
  { key: "mm", maxLen: 2, bounds: { min: 0, max: 59 }, placeholder: "mm", label: "분" },
];

function segmentsFor(type: DateInputType): SegmentDef[] {
  return type === "date" ? DATE_SEGMENTS : DATETIME_SEGMENTS;
}

type Segments = Record<SegmentKey, string>;
const EMPTY_SEGMENTS: Segments = { y: "", m: "", d: "", hh: "", mm: "" };

// ISO 문자열("2026-08-27" 또는 "2026-08-27T14:30")을 세그먼트별로 나눈다.
function toSegments(value: string, type: DateInputType): Segments {
  const digits = value.replace(/\D/g, "");
  const segs = segmentsFor(type);
  const result: Segments = { ...EMPTY_SEGMENTS };
  let pos = 0;
  for (const seg of segs) {
    result[seg.key] = digits.slice(pos, pos + seg.maxLen);
    pos += seg.maxLen;
  }
  return result;
}

// year/month/day가 실제로 존재하는 날짜인지 확인한다("2026-02-31" 같은 값을 걸러낸다).
// JS Date는 없는 날짜를 다음 달로 넘겨버리므로(2/31 -> 3/3), 되돌아온 값이 입력과
// 같은지 비교해서 판정한다.
function isValidCalendarDate(y: string, m: string, d: string): boolean {
  const year = Number(y);
  const month = Number(m);
  const day = Number(d);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
}

// 세그먼트들이 전부 다 채워졌고, 실제로 존재하는 날짜일 때만 ISO 문자열로, 아니면 "".
function segmentsToIso(segments: Segments, type: DateInputType): string {
  const segs = segmentsFor(type);
  if (segs.some((seg) => segments[seg.key].length !== seg.maxLen)) return "";
  if (!isValidCalendarDate(segments.y, segments.m, segments.d)) return "";
  const date = `${segments.y}-${segments.m}-${segments.d}`;
  return type === "datetime-local" ? `${date}T${segments.hh}:${segments.mm}` : date;
}

// 붙여넣기 등 한 번에 여러 자리를 처리할 때 쓰는 단순 버전(자리별 유효성만 확인, 즉시 패딩은 안 함).
function typeDigitPlain(current: string, digit: string, seg: SegmentDef): { value: string; advance: boolean } {
  if (!seg.bounds) {
    const next = (current.length >= seg.maxLen ? "" : current) + digit;
    return { value: next, advance: next.length >= seg.maxLen };
  }
  const { min, max } = seg.bounds;
  if (current.length === 0 || current.length >= seg.maxLen) {
    const d = Number(digit);
    if (d <= Math.floor(max / 10)) return { value: digit, advance: false };
    if (d >= min && d <= max) return { value: `0${digit}`, advance: true };
    return { value: current.length >= seg.maxLen ? current : "", advance: false };
  }
  const candidate = Number(current + digit);
  if (candidate >= min && candidate <= max) return { value: current + digit, advance: true };
  return typeDigitPlain("", digit, seg);
}

const BACKSPACE_CLEAR_STREAK = 3;
const BACKSPACE_STREAK_WINDOW_MS = 600;

/**
 * 네이티브 <input type="date">/<input type="datetime-local">가 브라우저·OS 입력기에 따라
 * 키보드 타이핑·삭제가 매끄럽지 않다는 피드백(2026-08-23)에 대응한 대체 컴포넌트.
 *
 * 연/월/일(/시/분)을 각각 독립된 입력칸으로 나눴다(신용카드 유효기간 입력과 동일한 패턴).
 * 이어질 수 있는 첫 자리(예: 일의 "3" -> 30~31 후보)를 치면 즉시 "0"을 붙여 "03"으로
 * 보여주면서(pendingRef로 기억) 다음 숫자를 기다린다. 이어지는 숫자가 유효하면
 * 합쳐서 확정하고(예: "3"+"1" -> "31"), 유효하지 않으면 방금 보여준 "03"으로 그대로
 * 확정한 뒤 그 숫자를 다음 칸의 새 입력으로 넘긴다.
 *
 * Backspace를 짧은 시간(0.6초) 안에 3번 연속 누르면 전체를 한 번에 지운다 - 2번으로
 * 하면 일반적인 "두 자리 지우기" 동작과 헷갈려서 3번으로 잡았다.
 *
 * 일(day)은 자리 검증만으로는 1~31까지 다 통과하므로(2월 31일 등 실존하지 않는 날짜),
 * segmentsToIso에서 실제 달력 유효성을 한 번 더 확인한다(코드래빗 리뷰 반영, 2026-08-23).
 */
export function DateInput({
  id,
  value,
  onChange,
  type = "date",
  required,
  disabled,
  min,
  max,
  className = "",
  "aria-label": ariaLabel,
}: DateInputProps) {
  const [segments, setSegments] = useState<Segments>(() => toSegments(value, type));
  const segs = segmentsFor(type);
  const refs = useRef<Partial<Record<SegmentKey, HTMLInputElement | null>>>({});
  const nativeRef = useRef<HTMLInputElement>(null);
  // 이 칸에 방금 0-패딩으로 보여준 "대기 중" 첫 자리가 있으면 기억해둔다. 다음 키가
  // 오면 이어붙여볼지(예: "3" -> "31") 판단하는 데 쓰인다.
  const pendingRef = useRef<{ key: SegmentKey; digit: string } | null>(null);
  // Backspace를 짧은 시간 안에 연속으로 몇 번 눌렀는지 센다. 3번 연속(0.6초 이내)이면
  // 부분 삭제 대신 전체를 한 번에 지운다.
  const backspaceStreakRef = useRef<{ count: number; lastTime: number }>({ count: 0, lastTime: 0 });
  // 우리가 방금 onChange로 내보낸 값을 기억해둔다. 부모가 그 값을 그대로 다시 value prop으로
  // 돌려주면(controlled 컴포넌트 특성상 항상 그렇게 된다) 그건 우리가 만든 변화의 메아리일
  // 뿐이라 무시해야 한다 - 안 그러면 세그먼트 하나가 미완성돼서 onChange("")가 나갈 때마다
  // 그 ""가 되돌아와 다른 칸까지 전부 리셋되는 버그가 생긴다.
  const lastEmittedRef = useRef<string | null>(null);

  // 부모가 값을 밖에서 바꾸면(초기 로딩, 폼 리셋, 달력 선택 등) 동기화한다.
  useEffect(() => {
    if (value === lastEmittedRef.current) return; // 우리가 방금 emit한 값의 메아리 - 무시
    setSegments(toSegments(value, type));
    pendingRef.current = null;
  }, [value, type]);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  function commit(next: Segments) {
    setSegments(next);
    const iso = segmentsToIso(next, type);
    lastEmittedRef.current = iso;
    onChange(iso);
  }

  function focusSegment(key: SegmentKey) {
    refs.current[key]?.focus();
    refs.current[key]?.select();
  }

  // 연/월/일/시/분 칸에 숫자 하나를 입력한다. base를 명시적으로 받아서(클로저의 오래된
  // segments를 안 쓰고) 다음 칸으로 넘어갈 때(리다이렉트) 이어서 정확히 반영되게 한다.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  function typeDigitAt(segIndex: number, digit: string, base: Segments): Segments {
    const seg = segs[segIndex];
    const current = base[seg.key];
    const pending = pendingRef.current;

    if (!seg.bounds) {
      // 연도: 자유 입력, 4자리 차면 자동으로 다음 칸.
      const next = (current.length >= seg.maxLen ? "" : current) + digit;
      const result = { ...base, [seg.key]: next };
      if (next.length >= seg.maxLen && segIndex < segs.length - 1) focusSegment(segs[segIndex + 1].key);
      return result;
    }

    // 이 칸에 대기 중인 첫 자리가 있으면, 이번 숫자로 이어붙여본다.
    if (pending && pending.key === seg.key) {
      const candidate = Number(pending.digit + digit);
      if (candidate >= seg.bounds.min && candidate <= seg.bounds.max) {
        pendingRef.current = null;
        const result = { ...base, [seg.key]: pending.digit + digit };
        if (segIndex < segs.length - 1) focusSegment(segs[segIndex + 1].key);
        return result;
      }
      // 이어붙일 수 없다 - 현재 칸은 이미 보여준 0-패딩 값 그대로 확정, 이 숫자는 다음 칸의 새 입력으로.
      pendingRef.current = null;
      if (segIndex < segs.length - 1) {
        focusSegment(segs[segIndex + 1].key);
        return typeDigitAt(segIndex + 1, digit, base);
      }
      return base;
    }

    // 새 입력 시작 (포커스 시 select()로 항상 덮어쓰기).
    const d = Number(digit);
    if (d <= Math.floor(seg.bounds.max / 10)) {
      pendingRef.current = { key: seg.key, digit }; // 다음 숫자를 기다린다 - 포커스는 그대로
      return { ...base, [seg.key]: `0${digit}` };
    }
    const result = { ...base, [seg.key]: `0${digit}` }; // 이어질 수 없는 숫자 - 즉시 확정
    if (segIndex < segs.length - 1) focusSegment(segs[segIndex + 1].key);
    return result;
  }

  const handleKeyDown = useCallback(
    (segIndex: number, event: KeyboardEvent<HTMLInputElement>) => {
      const seg = segs[segIndex];
      const current = segments[seg.key];

      if (event.key === "Backspace") {
        event.preventDefault();

        const now = Date.now();
        const streak = backspaceStreakRef.current;
        streak.count = now - streak.lastTime <= BACKSPACE_STREAK_WINDOW_MS ? streak.count + 1 : 1;
        streak.lastTime = now;

        if (streak.count >= BACKSPACE_CLEAR_STREAK) {
          streak.count = 0;
          pendingRef.current = null;
          commit({ y: "", m: "", d: "", hh: "", mm: "" });
          focusSegment(segs[0].key);
          return;
        }

        if (pendingRef.current?.key === seg.key) {
          // 방금 0-패딩으로 보여준 값이면 한 번에 전부 되돌린다(패딩 자체를 취소).
          pendingRef.current = null;
          commit({ ...segments, [seg.key]: "" });
          return;
        }
        if (current.length > 0) {
          commit({ ...segments, [seg.key]: current.slice(0, -1) });
        } else if (segIndex > 0) {
          const prevSeg = segs[segIndex - 1];
          commit({ ...segments, [prevSeg.key]: segments[prevSeg.key].slice(0, -1) });
          focusSegment(prevSeg.key);
        }
        return;
      }

      backspaceStreakRef.current.count = 0; // Backspace가 아닌 다른 키를 누르면 연속 기록을 끊는다

      if (event.key === "ArrowLeft" && segIndex > 0) {
        const el = event.currentTarget;
        if (el.selectionStart === 0 && el.selectionEnd === 0) {
          event.preventDefault();
          focusSegment(segs[segIndex - 1].key);
        }
        return;
      }
      if (event.key === "ArrowRight" && segIndex < segs.length - 1) {
        const el = event.currentTarget;
        if (el.selectionStart === current.length && el.selectionEnd === current.length) {
          event.preventDefault();
          focusSegment(segs[segIndex + 1].key);
        }
        return;
      }

      if (/^\d$/.test(event.key)) {
        event.preventDefault();
        commit(typeDigitAt(segIndex, event.key, segments));
        return;
      }

      // 방향키(Tab 포함)는 기본 동작에 맡기고, 그 외 문자 키는 막는다.
      if (event.key.length === 1) {
        event.preventDefault();
      }
    },
    [segs, segments, commit, typeDigitAt],
  );

  function handleBlur(segIndex: number) {
    if (pendingRef.current?.key === segs[segIndex].key) {
      pendingRef.current = null; // 이 칸을 벗어나면 대기 상태는 끝 - 이미 보여준 0-패딩 값이 최종값.
    }
  }

  function handlePaste(startIndex: number, event: ClipboardEvent<HTMLInputElement>) {
    event.preventDefault();
    pendingRef.current = null;
    const digits = event.clipboardData.getData("text").replace(/\D/g, "");
    const next = { ...segments };
    let segIndex = startIndex;
    let lastFocused: SegmentKey = segs[startIndex].key;

    for (const ch of digits) {
      if (segIndex >= segs.length) break;
      const seg = segs[segIndex];
      const { value: nextValue, advance } = typeDigitPlain(next[seg.key], ch, seg);
      next[seg.key] = nextValue;
      lastFocused = seg.key;
      if (advance && segIndex < segs.length - 1) segIndex += 1;
    }
    commit(next);
    focusSegment(lastFocused);
  }

  // 자리는 다 채웠는데 실제로 없는 날짜(9월 31일 등)면 안내 메시지를 보여준다.
  // 미완성(자리 안 채움)과는 다른 상황이라 구분해서 알려준다.
  const allFilled = segs.every((seg) => segments[seg.key].length === seg.maxLen);
  const invalidDate = allFilled && !isValidCalendarDate(segments.y, segments.m, segments.d);

  return (
    <div className={className}>
      <div
        className={`flex h-12 items-center rounded-button border bg-card px-3 focus-within:border-primary ${
          invalidDate ? "border-primary-strong" : "border-line"
        }`}
      >
        {segs.map((seg, index) => (
          <div key={seg.key} className="flex items-center">
            {index > 0 && (
              <span className={`text-sm text-muted ${seg.key === "hh" ? "px-1.5" : "px-px"}`}>
                {seg.key === "hh" ? "\u00A0" : seg.key === "mm" ? ":" : "-"}
              </span>
            )}
            <input
              ref={(el) => {
                refs.current[seg.key] = el;
              }}
              id={index === 0 ? id : undefined}
              type="text"
              inputMode="numeric"
              value={segments[seg.key]}
              placeholder={seg.placeholder}
              maxLength={seg.maxLen}
              aria-label={index === 0 && ariaLabel ? ariaLabel : seg.label}
              aria-required={index === 0 ? required : undefined}
              disabled={disabled}
              onKeyDown={(event) => handleKeyDown(index, event)}
              onPaste={(event) => handlePaste(index, event)}
              onFocus={(event) => event.currentTarget.select()}
              onBlur={() => handleBlur(index)}
              onChange={() => {}} // 실제 반영은 onKeyDown/onPaste - React 제어 컴포넌트 경고만 막는 용도
              className={`text-center text-sm text-ink placeholder:text-muted focus:outline-none ${
                seg.key === "y" ? "w-11" : "w-6"
              }`}
            />
          </div>
        ))}
        <button
          type="button"
          onClick={() => nativeRef.current?.showPicker?.()}
          disabled={disabled}
          aria-label="달력에서 선택"
          className="ml-auto grid size-9 shrink-0 place-items-center rounded-button text-muted hover:bg-page disabled:cursor-not-allowed disabled:opacity-50"
        >
          <CalendarDays size={18} aria-hidden="true" />
        </button>
        {/* 
          필수 입력 검증(required)은 부모 폼(BusinessRegisterPage/RecruitNoticeFormPage)의
          validate()가 담당한다 - 이 숨겨진 input에 required를 걸면 화면에 안 보이는
          엘리먼트가 네이티브 폼 검증을 막아버려서 사용자가 뭐가 문제인지 알 수 없다
          (코드래빗 리뷰 반영, 2026-08-23). 이 input은 달력 아이콘용으로만 쓴다.
        */}
        <input
          ref={nativeRef}
          type={type}
          value={segmentsToIso(segments, type)}
          onChange={(event) => {
            pendingRef.current = null;
            commit(toSegments(event.target.value, type));
          }}
          min={min}
          max={max}
          disabled={disabled}
          tabIndex={-1}
          aria-hidden="true"
          className="pointer-events-none absolute size-0 opacity-0"
        />
      </div>
      {invalidDate && <p className="mt-1 text-xs font-bold text-primary-strong">존재하지 않는 날짜예요.</p>}
    </div>
  );
}