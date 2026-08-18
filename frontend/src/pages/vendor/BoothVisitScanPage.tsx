import { CheckCircle2, Gift, RotateCcw, ScanLine, XCircle } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import { getMyBooths, type BoothFavoriteResponse } from "../../api/booth";
import { scanBoothVisit, type BoothScanResultCode } from "../../api/reservation";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { QrScanStation, type ScanFeedback } from "../../components/common/QrScanStation";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Select } from "../../components/ui/Select";
import { useAuth } from "../../contexts/AuthContext";

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

function boothLabel(booth: BoothFavoriteResponse) {
  return `${booth.name || `부스 #${booth.boothId}`} · ${booth.fairName}`;
}

export function BoothVisitScanPage() {
  // 새로고침 직후에는 silent refresh가 끝나기 전까지 user가 null이므로 status도 함께 본다
  // (안 보면 로그인 상태인데 "로그인이 필요해요"가 잠깐 보인다).
  const { user, status } = useAuth();

  // 스캔은 내가 소유한 부스에서만 된다(서버도 booth → business.owner_id로 검증한다).
  // 그래서 부스 번호를 직접 입력받지 않고 내 부스 목록에서 고르게 한다.
  const [booths, setBooths] = useState<BoothFavoriteResponse[]>([]);
  const [loadedFor, setLoadedFor] = useState<number | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  // draftBoothId=드롭다운에서 고르는 중인 값, boothId=스캔을 시작한 부스
  const [draftBoothId, setDraftBoothId] = useState("");
  const [boothId, setBoothId] = useState<number | null>(null);

  // 로딩은 상태로 들고 있지 않고 "이 사용자 것을 이미 받았는지"로 판단한다
  // (effect 안에서 동기적으로 setState 하지 않기 위함).
  const loading = status === "loading" || (user !== null && loadedFor !== user.userId);

  useEffect(() => {
    if (!user) return;
    let ignore = false;

    getMyBooths()
      .then((data) => {
        if (ignore) return;
        setBooths(data);
        setLoadError(null);
        // 부스가 하나면 고를 게 없으니 바로 스캔 화면으로 넘긴다.
        setDraftBoothId(data.length > 0 ? String(data[0].boothId) : "");
        setBoothId(data.length === 1 ? data[0].boothId : null);
      })
      .catch((error) => {
        if (ignore) return;
        setBooths([]);
        setLoadError(error instanceof ApiError ? error.message : "내 부스 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (!ignore) setLoadedFor(user.userId);
      });

    return () => {
      ignore = true;
    };
  }, [user]);

  function handleStart(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(draftBoothId);
    // 드롭다운 값은 항상 내 부스 목록에서 나오지만, 값이 비어 있는 상태로 제출되는 것만 막는다.
    if (!booths.some((booth) => booth.boothId === parsed)) return;
    setBoothId(parsed);
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <PageHeader eyebrow="참가업체" title="부스 방문 스캔" description="내 부스 목록을 불러오고 있어요." />
        <p className="text-sm text-muted">불러오는 중...</p>
      </div>
    );
  }

  if (!user || loadError || booths.length === 0) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <PageHeader
          eyebrow="참가업체"
          title="부스 방문 스캔"
          description="부스를 방문한 관람객의 입장 QR을 스캔해 방문을 기록해요."
        />
        {!user ? (
          <EmptyState
            title="로그인이 필요해요"
            description="참가업체 계정으로 로그인하면 내 부스에서 스캔할 수 있어요."
            actionTo="/login"
            actionLabel="로그인하러 가기"
          />
        ) : loadError ? (
          <EmptyState title="목록을 불러올 수 없어요" description={loadError} />
        ) : (
          <EmptyState
            title="스캔할 부스가 없어요"
            description="참가 신청이 확정되면 부스가 만들어지고, 그 부스에서 방문 스캔을 할 수 있어요."
            actionTo="/participations/me"
            actionLabel="내 부스 참가 신청 목록"
          />
        )}
      </div>
    );
  }

  // 부스가 2개 이상일 때만 고르는 단계를 둔다(사업자를 여러 개 등록하거나 행사가 여러 개인 경우).
  if (boothId === null) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <PageHeader
          eyebrow="참가업체"
          title="부스 방문 스캔"
          description="스캔할 부스를 먼저 선택해 주세요. 내가 소유한 부스만 보여요."
        />
        <form onSubmit={handleStart} className="surface flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label htmlFor="booth-select" className="mb-1.5 block text-sm font-bold text-ink">
              부스
            </label>
            <Select
              id="booth-select"
              value={draftBoothId}
              onChange={(event) => setDraftBoothId(event.target.value)}
            >
              {booths.map((booth) => (
                <option key={booth.boothId} value={booth.boothId}>
                  {boothLabel(booth)}
                </option>
              ))}
            </Select>
          </div>
          <Button type="submit" variant="outline">
            <ScanLine size={16} />
            스캔 시작
          </Button>
        </form>
      </div>
    );
  }

  const selectedBooth = booths.find((booth) => booth.boothId === boothId);

  return (
    <BoothScanStation
      key={boothId}
      boothId={boothId}
      eyebrow={selectedBooth ? boothLabel(selectedBooth) : `부스 #${boothId}`}
      // 부스가 하나뿐이면 바꿀 대상이 없으므로 변경 버튼을 숨긴다.
      onChangeBooth={booths.length > 1 ? () => setBoothId(null) : undefined}
    />
  );
}

interface BoothScanStationProps {
  boothId: number;
  eyebrow: string;
  onChangeBooth?: () => void;
}

function BoothScanStation({ boothId, eyebrow, onChangeBooth }: BoothScanStationProps) {
  const [scanError, setScanError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<{ code: BoothScanResultCode; visitCount: number } | null>(null);
  const [logs, setLogs] = useState<ScanLogItem[]>([]);

  async function handleScan(token: string): Promise<ScanFeedback | null> {
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
      const config = resultConfig[res.resultCode];
      // 사은품 중복 지급을 막아야 하므로 최초 방문이 아닌 결과는 모두 거부(빨강)로 알린다.
      return { tone: config.tone === "pass" ? "pass" : "reject", title: config.title };
    } catch (err) {
      // resultCode는 200으로 오므로, 여기 오는 건 네트워크/권한(부스 소유 아님 등) 예외다.
      setScanError(err instanceof ApiError ? err.message : "스캔 처리 중 오류가 났어요.");
      return { tone: "reject", title: "스캔 오류" };
    }
  }

  const result = lastResult
    ? { ...resultConfig[lastResult.code], code: lastResult.code, visitCount: lastResult.visitCount }
    : null;

  // 스캔 스테이션
  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader
        eyebrow={eyebrow}
        title="부스 방문 스캔"
        description="스캐너로 입장 QR을 읽으면 자동으로 처리돼요. 카메라로 비추거나 손으로 입력해도 돼요."
        action={
          onChangeBooth && (
            <Button variant="outline" onClick={onChangeBooth}>
              <RotateCcw size={16} />
              부스 변경
            </Button>
          )
        }
      />

      <QrScanStation inputId="booth-qr-token" onScan={handleScan} />

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
