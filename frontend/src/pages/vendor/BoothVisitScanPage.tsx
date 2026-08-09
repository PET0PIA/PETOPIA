import { CheckCircle2, Gift, RotateCcw, ScanLine, Search, XCircle } from "lucide-react";
import { useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import { scanBoothVisit, type BoothScanResultCode } from "../../api/reservation";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";

// 결과 코드별 표시 규칙. pass=최초 방문(초록) / block=이미 방문·거부(빨강)
const resultConfig: Record<
  BoothScanResultCode,
  { tone: "pass" | "block"; title: string; description: string }
> = {
  FIRST_VISIT: { tone: "pass", title: "방문 확인", description: "최초 방문이에요. 사은품을 지급해도 돼요." },
  ALREADY_VISITED: { tone: "block", title: "이미 방문한 예약", description: "이미 방문한 예약이에요. 사은품 중복 지급에 주의하세요." },
  NOT_FOUND: { tone: "block", title: "QR을 찾을 수 없어요", description: "등록되지 않은 입장 QR이에요." },
  FAIR_MISMATCH: { tone: "block", title: "다른 행사 QR", description: "이 행사의 입장 QR이 아니에요." },
  NOT_AVAILABLE: { tone: "block", title: "사용할 수 없는 QR", description: "지금은 사용할 수 없는 QR이에요." },
  INVALID_RESERVATION_STATUS: {
    tone: "block",
    title: "유효하지 않은 예약",
    description: "취소·만료 등으로 유효하지 않은 예약이에요.",
  },
};

const toneStyles: Record<"pass" | "block", string> = {
  pass: "border-leaf/40 bg-leaf-soft text-ink",
  block: "border-primary-strong/40 bg-primary-soft text-primary-strong",
};

interface ScanLogItem {
  at: string;
  resultCode: BoothScanResultCode;
  tokenTail: string;
}

export function BoothVisitScanPage() {
  // TODO 참가업체 세션에 담당 부스(boothId)가 연결되면 이 입력을 없애고 세션 값을 바로 쓴다.
  const [boothIdInput, setBoothIdInput] = useState("");
  const [boothId, setBoothId] = useState<number | null>(null);
  const [startError, setStartError] = useState<string | null>(null);

  const [qrInput, setQrInput] = useState("");
  const [scanning, setScanning] = useState(false);
  const [scanError, setScanError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<{ code: BoothScanResultCode; visitCount: number } | null>(null);
  const [logs, setLogs] = useState<ScanLogItem[]>([]);

  const qrInputRef = useRef<HTMLInputElement>(null);

  function handleStart(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(boothIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setStartError("부스 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    setStartError(null);
    // 이전 부스의 결과·로그·입력이 새 부스 화면에 남지 않도록 초기화한다.
    setLastResult(null);
    setLogs([]);
    setScanError(null);
    setQrInput("");
    setBoothId(parsed);
    setTimeout(() => qrInputRef.current?.focus(), 0);
  }

  async function handleScan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const token = qrInput.trim();
    if (boothId === null || token === "" || scanning) return;

    setScanning(true);
    setScanError(null);
    // 이번 스캔 결과만 보이도록 직전 결과를 먼저 지운다(실패 시 이전 결과 오인 방지).
    setLastResult(null);
    try {
      const res = await scanBoothVisit(boothId, token);
      setLastResult({ code: res.resultCode, visitCount: res.visitCount });
      setLogs((previous) =>
        [
          { at: new Date().toLocaleTimeString(), resultCode: res.resultCode, tokenTail: token.slice(-6) },
          ...previous,
        ].slice(0, 20),
      );
      setQrInput("");
    } catch (err) {
      // resultCode는 200으로 오므로, 여기 오는 건 네트워크/권한(부스 소유 아님 등) 예외다.
      setScanError(err instanceof ApiError ? err.message : "스캔 처리 중 오류가 났어요.");
    } finally {
      setScanning(false);
      qrInputRef.current?.focus();
    }
  }

  // 시작 전: 부스 ID 입력
  if (boothId === null) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <PageHeader
          eyebrow="참가업체"
          title="부스 방문 스캔"
          description="부스를 방문한 관람객의 입장 QR을 스캔해 방문을 기록해요. 먼저 담당 부스 ID를 입력해 주세요."
        />
        <form onSubmit={handleStart} className="surface flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
          <div className="flex-1">
            <span className="mb-1.5 block text-sm font-bold text-ink">부스 ID</span>
            <Input
              type="number"
              min={1}
              value={boothIdInput}
              onChange={(event) => setBoothIdInput(event.target.value)}
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

  const result = lastResult
    ? { ...resultConfig[lastResult.code], code: lastResult.code, visitCount: lastResult.visitCount }
    : null;

  // 스캔 스테이션
  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader
        eyebrow={`부스 #${boothId}`}
        title="부스 방문 스캔"
        description="스캐너로 입장 QR을 읽으면 자동으로 처리돼요. 손으로 입력한 뒤 Enter를 눌러도 돼요."
        action={
          <Button variant="outline" onClick={() => setBoothId(null)}>
            <RotateCcw size={16} />
            부스 변경
          </Button>
        }
      />

      <Card className="mb-4 p-5">
        <form onSubmit={handleScan}>
          <label htmlFor="booth-qr-token" className="mb-1.5 block text-sm font-bold text-ink">
            입장 QR 토큰
          </label>
          <div className="flex gap-2">
            {/* Input 컴포넌트는 ref를 받지 않아(공유 컴포넌트 미변경) 스캔 포커스용으로만 네이티브 input 사용 */}
            <input
              id="booth-qr-token"
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
        </form>
      </Card>

      {scanError && <p className="mb-4 text-sm font-bold text-primary-strong">{scanError}</p>}

      {/* 최근 스캔 결과 */}
      {result && (
        <div className={`mb-4 flex items-start gap-3 rounded-card border p-5 ${toneStyles[result.tone]}`}>
          {result.tone === "pass" ? (
            <Gift size={28} className="shrink-0" />
          ) : result.code === "ALREADY_VISITED" ? (
            <CheckCircle2 size={28} className="shrink-0" />
          ) : (
            <XCircle size={28} className="shrink-0" />
          )}
          <div>
            <p className="text-lg font-extrabold">{result.title}</p>
            <p className="mt-1 text-sm">{result.description}</p>
            {(result.code === "FIRST_VISIT" || result.code === "ALREADY_VISITED") && (
              <p className="mt-2 text-xs">누적 방문 {result.visitCount}회</p>
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
