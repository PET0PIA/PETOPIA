import { CheckCircle2, RotateCcw, ScanLine, Search, XCircle } from "lucide-react";
import { useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import { scanGateEntry, type GateScanResultCode } from "../../api/reservation";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";

// 6종 결과 코드별 표시 규칙. tone: pass=통과(초록) / info=재스캔(회색) / reject=거부(빨강)
const resultConfig: Record<
  GateScanResultCode,
  { tone: "pass" | "info" | "reject"; title: string; description: string }
> = {
  FIRST_ENTRY: { tone: "pass", title: "입장 확인", description: "최초 입장 처리됐어요." },
  ALREADY_CHECKED_IN: { tone: "info", title: "이미 입장한 예약", description: "이미 입장한 QR이에요(재스캔)." },
  NOT_FOUND: { tone: "reject", title: "QR을 찾을 수 없어요", description: "등록되지 않은 입장 QR이에요." },
  FAIR_MISMATCH: { tone: "reject", title: "다른 행사 QR", description: "이 행사의 입장 QR이 아니에요." },
  NOT_AVAILABLE: { tone: "reject", title: "입장 시간 아님", description: "지금은 입장 가능 시간이 아니에요." },
  INVALID_RESERVATION_STATUS: {
    tone: "reject",
    title: "입장할 수 없는 예약",
    description: "취소·만료 등으로 입장할 수 없는 예약이에요.",
  },
};

const toneStyles: Record<"pass" | "info" | "reject", string> = {
  pass: "border-leaf/40 bg-leaf-soft text-ink",
  info: "border-line bg-page text-ink",
  reject: "border-primary-strong/40 bg-primary-soft text-primary-strong",
};

const entrySourceLabels: Record<string, string> = {
  ADVANCE: "사전예약",
  ONSITE_DIRECT: "현장예매",
  KIOSK: "키오스크",
};

interface ScanLogItem {
  at: string;
  resultCode: GateScanResultCode;
  tokenTail: string;
}

function formatClockTime(iso: string | null) {
  return iso ? iso.replace("T", " ").slice(11, 19) : "-";
}

export function GateEntryScanPage() {
  // TODO 관리자 세션에 담당 행사(fairId)가 연결되면 이 입력을 없애고 세션 값을 바로 쓴다.
  const [fairIdInput, setFairIdInput] = useState("");
  const [fairId, setFairId] = useState<number | null>(null);
  const [startError, setStartError] = useState<string | null>(null);

  const [deviceInfo, setDeviceInfo] = useState("");
  const [qrInput, setQrInput] = useState("");
  const [scanning, setScanning] = useState(false);
  const [scanError, setScanError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<
    { code: GateScanResultCode; entrySource: string | null; firstCheckedInAt: string | null } | null
  >(null);
  const [logs, setLogs] = useState<ScanLogItem[]>([]);

  const qrInputRef = useRef<HTMLInputElement>(null);

  function handleStart(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(fairIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setStartError("행사 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    setStartError(null);
    setFairId(parsed);
    // 스캐너 입력이 바로 들어오도록 QR 입력창에 포커스.
    setTimeout(() => qrInputRef.current?.focus(), 0);
  }

  async function handleScan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const token = qrInput.trim();
    if (fairId === null || token === "" || scanning) return;

    setScanning(true);
    setScanError(null);
    try {
      const res = await scanGateEntry(fairId, token, deviceInfo.trim() || undefined);
      setLastResult({
        code: res.resultCode,
        entrySource: res.entrySource,
        firstCheckedInAt: res.firstCheckedInAt,
      });
      setLogs((previous) =>
        [
          { at: new Date().toLocaleTimeString(), resultCode: res.resultCode, tokenTail: token.slice(-6) },
          ...previous,
        ].slice(0, 20),
      );
      setQrInput("");
    } catch (err) {
      // resultCode는 200으로 오므로, 여기 오는 건 네트워크/권한 등 진짜 예외다.
      setScanError(err instanceof ApiError ? err.message : "스캔 처리 중 오류가 났어요.");
    } finally {
      setScanning(false);
      qrInputRef.current?.focus();
    }
  }

  // 시작 전: 행사 ID 입력
  if (fairId === null) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <PageHeader
          eyebrow="박람회 관리자"
          title="QR 입장 스캔"
          description="게이트에서 관람객의 입장 QR을 스캔해 입장을 처리해요. 먼저 담당 행사 ID를 입력해 주세요."
        />
        <form onSubmit={handleStart} className="surface flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
          <div className="flex-1">
            <span className="mb-1.5 block text-sm font-bold text-ink">행사 ID</span>
            <Input
              type="number"
              min={1}
              value={fairIdInput}
              onChange={(event) => setFairIdInput(event.target.value)}
              placeholder="예: 1"
            />
          </div>
          <Button type="submit" variant="outline">
            <Search size={16} />
            스캔 시작
          </Button>
        </form>
        {startError && <p className="mt-3 text-sm font-bold text-primary-strong">{startError}</p>}
      </div>
    );
  }

  const result = lastResult ? { ...resultConfig[lastResult.code], data: lastResult } : null;

  // 스캔 스테이션
  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader
        eyebrow={`행사 #${fairId}`}
        title="QR 입장 스캔"
        description="스캐너로 입장 QR을 읽으면 자동으로 처리돼요. 손으로 입력한 뒤 Enter를 눌러도 돼요."
        action={
          <Button variant="outline" onClick={() => setFairId(null)}>
            <RotateCcw size={16} />
            행사 변경
          </Button>
        }
      />

      <Card className="mb-4 p-5">
        <form onSubmit={handleScan} className="space-y-4">
          <div>
            <span className="mb-1.5 block text-sm font-bold text-ink">입장 QR 토큰</span>
            <div className="flex gap-2">
              {/* Input 컴포넌트는 ref를 받지 않아(공유 컴포넌트 미변경) 스캔 포커스용으로만 네이티브 input 사용 */}
              <input
                ref={qrInputRef}
                value={qrInput}
                onChange={(event) => setQrInput(event.target.value)}
                placeholder="스캐너로 읽거나 토큰을 붙여넣고 Enter"
                autoFocus
                className="h-12 w-full rounded-button border border-line bg-card px-4 text-sm text-ink placeholder:text-muted focus:border-primary"
              />
              <Button type="submit" disabled={scanning || qrInput.trim() === ""}>
                <ScanLine size={16} />
                {scanning ? "확인 중…" : "스캔"}
              </Button>
            </div>
          </div>
          <div>
            <span className="mb-1.5 block text-sm font-bold text-ink">게이트/기기 정보 (선택)</span>
            <Input
              value={deviceInfo}
              onChange={(event) => setDeviceInfo(event.target.value)}
              placeholder="예: GATE-01 / iPad"
            />
          </div>
        </form>
      </Card>

      {scanError && <p className="mb-4 text-sm font-bold text-primary-strong">{scanError}</p>}

      {/* 최근 스캔 결과 */}
      {result && (
        <div className={`mb-4 flex items-start gap-3 rounded-card border p-5 ${toneStyles[result.tone]}`}>
          {result.tone === "pass" ? (
            <CheckCircle2 size={28} className="shrink-0" />
          ) : result.tone === "reject" ? (
            <XCircle size={28} className="shrink-0" />
          ) : (
            <RotateCcw size={28} className="shrink-0" />
          )}
          <div>
            <p className="text-lg font-extrabold">{result.title}</p>
            <p className="mt-1 text-sm">{result.description}</p>
            {(result.data.entrySource || result.data.firstCheckedInAt) && (
              <p className="mt-2 text-xs">
                {result.data.entrySource &&
                  `입장 경로: ${entrySourceLabels[result.data.entrySource] ?? result.data.entrySource}`}
                {result.data.entrySource && result.data.firstCheckedInAt && " · "}
                {result.data.firstCheckedInAt && `최초 입장: ${formatClockTime(result.data.firstCheckedInAt)}`}
              </p>
            )}
          </div>
        </div>
      )}

      {/* 스캔 로그 */}
      {logs.length > 0 && (
        <Card className="p-5">
          <p className="mb-3 text-sm font-bold text-ink">최근 스캔 기록</p>
          <ul className="space-y-1.5 text-sm">
            {logs.map((log, index) => (
              <li key={`${log.at}-${index}`} className="flex items-center justify-between gap-3 text-muted">
                <span className="tabular-nums">{log.at}</span>
                <span className="font-mono text-xs">…{log.tokenTail}</span>
                <span className="font-bold text-ink">{resultConfig[log.resultCode].title}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
