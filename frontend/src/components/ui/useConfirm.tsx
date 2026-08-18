import { AlertTriangle } from "lucide-react";
import { useCallback, useRef, useState } from "react";
import { Button } from "./Button";
import { Dialog } from "./Dialog";

interface ConfirmOptions {
  title?: string;
  description: string;
  /** 위험한 동작(삭제, 되돌리기 등)이면 경고 아이콘을 보여준다. 기본 true. */
  danger?: boolean;
  confirmLabel?: string;
  cancelLabel?: string;
}

interface ConfirmState extends ConfirmOptions {
  open: boolean;
}

const initialState: ConfirmState = { open: false, description: "" };

/**
 * window.confirm 대신 쓰는 커스텀 확인 모달 훅.
 *
 * window.confirm과 달리 비동기(Promise<boolean>)라서 호출부는 await로 받아야 한다.
 *
 * ```tsx
 * const { confirm, confirmDialog } = useConfirm();
 * ...
 * async function handleDelete() {
 *   if (!(await confirm({ description: "삭제할까요?" }))) return;
 *   ...
 * }
 * ...
 * return <>{...기존 JSX...}{confirmDialog}</>;
 * ```
 */
export function useConfirm() {
  const [state, setState] = useState<ConfirmState>(initialState);
  const resolveRef = useRef<((value: boolean) => void) | null>(null);

  const confirm = useCallback((options: ConfirmOptions) => {
    return new Promise<boolean>((resolve) => {
      resolveRef.current = resolve;
      setState({ ...options, open: true });
    });
  }, []);

  function settle(result: boolean) {
    resolveRef.current?.(result);
    resolveRef.current = null;
    setState(initialState);
  }

  const confirmDialog = (
    <Dialog open={state.open} onClose={() => settle(false)} title={state.title ?? "확인이 필요해요"}>
      <div className="space-y-4">
        <div className="flex items-start gap-3 text-sm text-ink">
          {state.danger !== false && <AlertTriangle size={18} className="mt-0.5 shrink-0 text-primary-strong" />}
          <p className="whitespace-pre-line leading-6">{state.description}</p>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={() => settle(false)}>{state.cancelLabel ?? "취소"}</Button>
          <Button type="button" onClick={() => settle(true)}>{state.confirmLabel ?? "확인"}</Button>
        </div>
      </div>
    </Dialog>
  );

  return { confirm, confirmDialog };
}
