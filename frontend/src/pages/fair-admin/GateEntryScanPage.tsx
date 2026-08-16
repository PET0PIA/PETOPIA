import { CheckCircle2, RotateCcw, XCircle } from "lucide-react";
import { useState } from "react";
import { ApiError } from "../../api/client";
import { scanGateEntry, type GateScanResultCode } from "../../api/reservation";
import { PageHeader } from "../../components/common/PageHeader";
import { QrScanStation, type ScanFeedback } from "../../components/common/QrScanStation";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { useFairSelector } from "../../contexts/FairSelectorContext";

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
  // 콘솔 상단 바의 "관리 행사" 선택기가 현재 행사를 정한다.
  const { fairId } = useFairSelector();

  // 상단 선택기에 행사가 없으면(배정 0개) 스캔 스테이션 대신 안내를 보여준다.
  if (fairId === null) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <PageHeader
          eyebrow="박람회 관리자"
          title="QR 입장 스캔"
          description="게이트에서 관람객의 입장 QR을 스캔해 입장을 처리해요."
        />
        <div className="surface p-8 text-center text-sm text-muted">
          상단 바에서 행사를 선택하면 스캔을 시작할 수 있어요. 배정된 행사가 없다면 관리자에게 문의해 주세요.
        </div>
      </div>
    );
  }

  // 행사가 바뀌면 이전 행사의 스캔 결과·로그·카메라가 남지 않도록 통째로 다시 마운트한다.
  return <GateScanStation key={fairId} fairId={fairId} />;
}

function GateScanStation({ fairId }: { fairId: number }) {
  const [deviceInfo, setDeviceInfo] = useState("");
  const [scanError, setScanError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<
    { code: GateScanResultCode; entrySource: string | null; firstCheckedInAt: string | null } | null
  >(null);
  const [logs, setLogs] = useState<ScanLogItem[]>([]);

  async function handleScan(token: string): Promise<ScanFeedback | null> {
    setScanError(null);
    // 이번 스캔 결과만 보이도록 직전 결과를 먼저 지운다(실패 시 이전 성공 배너 오인 방지).
    setLastResult(null);
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
      const config = resultConfig[res.resultCode];
      return { tone: config.tone, title: config.title };
    } catch (err) {
      // resultCode는 200으로 오므로, 여기 오는 건 네트워크/권한 등 진짜 예외다.
      setScanError(err instanceof ApiError ? err.message : "스캔 처리 중 오류가 났어요.");
      return { tone: "reject", title: "스캔 오류" };
    }
  }

  const result = lastResult ? { ...resultConfig[lastResult.code], data: lastResult } : null;

  // 스캔 스테이션
  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader
        eyebrow={`행사 #${fairId}`}
        title="QR 입장 스캔"
        description="스캐너로 입장 QR을 읽으면 자동으로 처리돼요. 카메라로 비추거나 손으로 입력해도 돼요."
      />

      <QrScanStation
        inputId="gate-qr-token"
        onScan={handleScan}
        extraFields={
          <div>
            <span className="mb-1.5 block text-sm font-bold text-ink">게이트/기기 정보 (선택)</span>
            <Input
              value={deviceInfo}
              onChange={(event) => setDeviceInfo(event.target.value)}
              placeholder="예: GATE-01 / iPad"
            />
          </div>
        }
      />

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
