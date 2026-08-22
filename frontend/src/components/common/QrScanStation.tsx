import { Camera, CheckCircle2, Keyboard, RotateCcw, ScanLine, Volume2, VolumeX, XCircle } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Button } from "../ui/Button";
import { Card } from "../ui/Card";

/**
 * 게이트 입장 스캔과 부스 방문 스캔이 공유하는 QR 리더 스테이션.
 * 입력 수단(카메라·리더기), 재스캔 쿨다운, 결과 표시/결과음까지만 담당하고
 * 스캔 API 호출과 결과 배너·로그는 페이지가 맡는다.
 *
 * 카메라 인식은 jsQR로 직접 해독한다. 브라우저 내장 BarcodeDetector는 윈도우·리눅스 크롬/엣지에
 * 아예 없어서 담당자 PC를 가리므로 쓰지 않는다. 해독기는 카메라를 켤 때 동적 import로 받아
 * 일반 사용자 번들에는 넣지 않는다. 게이트·부스 기본 장비는 여전히 USB 스캐너이고 카메라는 보조 수단이다.
 */
export type ScanTone = "pass" | "info" | "reject";

/** 스캔 1건의 결과를 화면·소리로 알리기 위한 최소 정보. */
export interface ScanFeedback {
  tone: ScanTone;
  title: string;
}

/** 프레임을 읽는 주기. 더 촘촘하게 돌려도 인식률은 그대로이고 CPU만 먹는다. */
const DETECT_INTERVAL_MS = 250;
/** 해독에 쓰는 프레임 폭. 원본 해상도로 넘겨도 인식률은 거의 같고 CPU만 더 쓴다. */
const DETECT_WIDTH = 480;
/**
 * 같은 QR을 다시 서버로 보내기까지 기다리는 시간. 카메라 앞에 QR이 계속 머무르므로
 * 이게 없으면 초당 4회씩 같은 토큰으로 스캔 API를 때린다(그리고 감사 로그가 그만큼 쌓인다).
 * QR을 치우고 다음 사람이 들어설 만큼은 여유가 있어야 해서 넉넉하게 잡는다.
 */
const SAME_TOKEN_COOLDOWN_MS = 8000;
/** 카메라 프리뷰 위에 결과를 띄워두는 시간. 재스캔 대기(쿨다운)보다는 짧게 둔다. */
const RESULT_FLASH_MS = 2000;

/**
 * 담당자가 화면을 계속 보고 있지 않아도 알 수 있게 결과음을 낸다. 오디오 파일을 번들에
 * 넣지 않으려고 WebAudio로 직접 만든다. AudioContext는 사용자 조작(스캔) 이후에 만들어야
 * 브라우저 자동재생 정책에 막히지 않는다.
 */
const beepPatterns: Record<ScanTone, { hz: number; ms: number }[]> = {
  pass: [
    { hz: 1320, ms: 90 },
    { hz: 1760, ms: 130 },
  ],
  info: [{ hz: 880, ms: 140 }],
  reject: [
    { hz: 300, ms: 220 },
    { hz: 220, ms: 300 },
  ],
};

/** 프리뷰 위에 겹치는 결과 표시는 밝은 조명 아래 멀리서도 보이도록 불투명하게 채운다. */
const flashStyles: Record<ScanTone, string> = {
  pass: "border-leaf bg-leaf/85 text-white",
  info: "border-line bg-ink/75 text-white",
  reject: "border-primary-strong bg-primary-strong/85 text-white",
};

type ScanMode = "camera" | "manual";

interface QrScanStationProps {
  /** 리더기 입력 input의 id. 한 화면에 여러 스테이션이 없으므로 페이지별로 하나만 준다. */
  inputId: string;
  /**
   * 토큰 1건을 처리한다. 반환한 tone·title로 프리뷰 표시와 결과음을 낸다.
   * null을 반환하면 알림 없이 넘어간다(페이지가 직접 처리한 경우).
   */
  onScan: (token: string) => Promise<ScanFeedback | null>;
  /** 리더기 입력 아래에 붙는 추가 입력(예: 게이트/기기 정보). */
  extraFields?: ReactNode;
}

export function QrScanStation({ inputId, onScan, extraFields }: QrScanStationProps) {
  const [mode, setMode] = useState<ScanMode>("manual");
  const [qrInput, setQrInput] = useState("");
  const [scanning, setScanning] = useState(false);
  const [cameraOn, setCameraOn] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  // 프리뷰 위에 잠깐 띄우는 결과 표시. 페이지의 결과 배너와 달리 스스로 사라진다.
  const [flash, setFlash] = useState<ScanFeedback | null>(null);
  const [soundOn, setSoundOn] = useState(true);

  const qrInputRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  // 카메라 루프는 렌더와 무관하게 돌아가므로 상태 대신 ref로 중복 요청을 막는다.
  const scanningRef = useRef(false);
  const audioContextRef = useRef<AudioContext | null>(null);
  const flashTimerRef = useRef(0);

  // 카메라는 https·localhost에서만 열린다. http로 IP 접속하면 mediaDevices 자체가 없다.
  const cameraSupported =
    typeof navigator !== "undefined" && navigator.mediaDevices?.getUserMedia !== undefined;

  /** 결과음. 소리는 보조 신호라 재생이 막혀도 스캔 처리에는 영향을 주지 않는다. */
  function playBeep(tone: ScanTone) {
    if (!soundOn || typeof window === "undefined" || window.AudioContext === undefined) return;
    try {
      const context = (audioContextRef.current ??= new AudioContext());
      void context.resume();
      let startAt = context.currentTime;
      for (const step of beepPatterns[tone]) {
        const duration = step.ms / 1000;
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        oscillator.type = "square";
        oscillator.frequency.value = step.hz;
        // 시작·끝을 짧게 페이드해 딸깍거리는 클릭음을 없앤다.
        gain.gain.setValueAtTime(0, startAt);
        gain.gain.linearRampToValueAtTime(0.18, startAt + 0.01);
        gain.gain.setValueAtTime(0.18, startAt + Math.max(duration - 0.02, 0.02));
        gain.gain.linearRampToValueAtTime(0, startAt + duration);
        oscillator.connect(gain).connect(context.destination);
        oscillator.start(startAt);
        oscillator.stop(startAt + duration);
        startAt += duration;
      }
    } catch {
      // 자동재생 차단·오디오 장치 없음 등은 무시한다.
    }
  }

  function showFlash(feedback: ScanFeedback) {
    window.clearTimeout(flashTimerRef.current);
    setFlash(feedback);
    flashTimerRef.current = window.setTimeout(() => setFlash(null), RESULT_FLASH_MS);
  }

  useEffect(
    () => () => {
      window.clearTimeout(flashTimerRef.current);
      void audioContextRef.current?.close();
    },
    [],
  );

  async function runScan(token: string) {
    if (token === "" || scanningRef.current) return;

    scanningRef.current = true;
    setScanning(true);
    try {
      const feedback = await onScan(token);
      setQrInput("");
      // 화면을 보고 있지 않아도, 프리뷰만 보고 있어도 처리됐다는 걸 알 수 있게 한다.
      if (feedback) {
        showFlash(feedback);
        playBeep(feedback.tone);
      }
    } finally {
      scanningRef.current = false;
      setScanning(false);
      if (mode === "manual") qrInputRef.current?.focus();
    }
  }

  // 카메라 루프가 매 프레임 최신 runScan을 보게 한다. onScan이 바뀔 때마다
  // 카메라를 다시 켜지 않으려고 의존성 대신 ref를 쓴다.
  const runScanRef = useRef(runScan);
  useEffect(() => {
    runScanRef.current = runScan;
  });

  function handleManualScan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runScan(qrInput.trim());
  }

  // 카메라 켜짐 → 스트림 열고 QR을 반복 인식. 탭 이동·언마운트 시 반드시 끈다
  // (끊지 않으면 카메라 표시등이 계속 켜져 있고 트랙이 살아남는다).
  useEffect(() => {
    if (mode !== "camera" || !cameraOn) return;

    const video = videoRef.current;
    if (video === null) return;

    let cancelled = false;
    let timer = 0;
    let stream: MediaStream | null = null;
    // 같은 QR을 계속 비추는 동안 재요청하지 않도록 직전 토큰과 시각을 기억한다.
    let lastToken: string | null = null;
    let lastSentAt = 0;

    void (async () => {
      try {
        // 게이트·부스에서는 후면 카메라가 기본이다. 노트북은 facingMode를 무시하고 전면을 준다.
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
      } catch {
        if (!cancelled) {
          setCameraError("카메라를 열 수 없어요. 브라우저 권한을 확인해 주세요.");
          setCameraOn(false);
        }
        return;
      }
      if (cancelled) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }

      video.srcObject = stream;
      try {
        await video.play();
      } catch {
        // 자동재생이 막힌 경우 - 프리뷰만 멈추고 인식 루프는 그대로 진행한다.
      }

      // 해독기는 카메라를 켤 때만 받는다(일반 사용자 번들에서 빼기 위해 동적 import).
      // 배포 직후 낡은 페이지가 사라진 청크를 부르거나 네트워크가 끊기면 이 요청이 실패한다.
      // 그냥 두면 카메라는 켜진 채 인식만 조용히 멈춰 담당자가 이유를 알 수 없으므로,
      // 카메라를 끄고(정리 함수가 트랙까지 끊는다) 이유를 보여준다.
      const decoder = await import("jsqr").catch(() => null);
      if (cancelled) return;
      if (decoder === null) {
        setCameraError("QR 인식 기능을 불러오지 못했어요. 새로고침하거나 리더기 입력을 사용해 주세요.");
        setCameraOn(false);
        return;
      }
      const jsQR = decoder.default;

      // 프레임을 옮겨 픽셀을 직접 읽을 캔버스. 화면에는 붙이지 않는다.
      const canvas = document.createElement("canvas");
      const context = canvas.getContext("2d", { willReadFrequently: true });
      if (context === null) {
        setCameraError("이 브라우저에서는 카메라 화면을 읽을 수 없어요. 리더기 입력을 사용해 주세요.");
        setCameraOn(false);
        return;
      }

      const tick = async () => {
        if (cancelled) return;
        try {
          // 프리뷰가 붙기 전 초기 프레임은 크기가 0이라 그릴 것이 없다.
          if (video.videoWidth > 0 && video.videoHeight > 0) {
            const scale = Math.min(1, DETECT_WIDTH / video.videoWidth);
            canvas.width = Math.round(video.videoWidth * scale);
            canvas.height = Math.round(video.videoHeight * scale);
            context.drawImage(video, 0, 0, canvas.width, canvas.height);
            const frame = context.getImageData(0, 0, canvas.width, canvas.height);
            const value = jsQR(frame.data, frame.width, frame.height)?.data.trim() ?? "";
            const now = Date.now();
            const isRepeat = value === lastToken && now - lastSentAt < SAME_TOKEN_COOLDOWN_MS;
            if (value !== "" && !isRepeat) {
              lastToken = value;
              await runScanRef.current(value);
              // 쿨다운은 응답을 받은 시점부터 센다. 요청 시점부터 재면 응답이 늦을수록
              // 담당자가 결과를 볼 수 있는 시간이 줄어든다.
              lastSentAt = Date.now();
            }
          }
        } catch {
          // 프레임 하나를 못 읽는 건 정상이다(초점·흔들림). 다음 프레임에서 다시 시도한다.
        }
        timer = window.setTimeout(() => void tick(), DETECT_INTERVAL_MS);
      };
      void tick();
    })();

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
      stream?.getTracks().forEach((track) => track.stop());
      video.srcObject = null;
    };
  }, [mode, cameraOn]);

  return (
    <Card className="mb-4 p-5">
      <div className="mb-4 flex items-center justify-between gap-2">
        {/* 입력 수단 전환. 기본은 USB 스캐너(리더기 입력)이고 카메라는 보조 수단이다. */}
        <div className="inline-flex gap-1 rounded-button border border-line bg-page p-1">
          {(
            [
              { value: "camera", label: "카메라", icon: Camera },
              { value: "manual", label: "리더기 입력", icon: Keyboard },
            ] as const
          ).map((tab) => (
            <button
              key={tab.value}
              type="button"
              aria-pressed={mode === tab.value}
              onClick={() => {
                setMode(tab.value);
                // 카메라를 켠 채 리더기 탭으로 가면 스트림이 남으므로 여기서 끊는다.
                if (tab.value === "manual") {
                  setCameraOn(false);
                  setTimeout(() => qrInputRef.current?.focus(), 0);
                }
              }}
              className={`inline-flex items-center gap-1.5 rounded-button px-3.5 py-1.5 text-sm font-bold ${
                mode === tab.value ? "bg-card text-ink shadow-sm" : "text-muted"
              }`}
            >
              <tab.icon size={15} />
              {tab.label}
            </button>
          ))}
        </div>
        {/* 결과음 켜기/끄기. 조용한 실내에서는 끌 수 있어야 한다. */}
        <button
          type="button"
          aria-pressed={soundOn}
          onClick={() => setSoundOn(!soundOn)}
          title={soundOn ? "결과음 끄기" : "결과음 켜기"}
          className="inline-flex items-center gap-1.5 rounded-button border border-line px-3 py-1.5 text-sm font-bold text-muted"
        >
          {soundOn ? <Volume2 size={15} /> : <VolumeX size={15} />}
          {soundOn ? "소리 켜짐" : "소리 꺼짐"}
        </button>
      </div>

      {mode === "camera" && (
        <div className="mb-4 space-y-3">
          {!cameraSupported ? (
            <p className="text-sm text-muted">
              이 주소에서는 카메라를 쓸 수 없어요. 브라우저가 https 또는 localhost 접속에서만 카메라를
              허용해요. localhost로 접속하거나 리더기 입력을 사용해 주세요.
            </p>
          ) : (
            <>
              <div className="relative overflow-hidden rounded-card border border-line bg-page">
                {/* muted·playsInline이 없으면 모바일 브라우저가 자동재생을 막는다. */}
                <video ref={videoRef} muted playsInline className="aspect-video w-full object-cover" />
                {!cameraOn && (
                  <div className="absolute inset-0 flex items-center justify-center text-sm text-muted">
                    카메라를 켜면 QR을 자동으로 인식해요.
                  </div>
                )}
                {cameraOn && flash === null && (
                  // 조준 가이드. 프리뷰 위에 겹치는 장식이라 클릭을 가로채지 않게 한다.
                  <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
                    <div className="h-40 w-40 rounded-card border-2 border-primary/80" />
                  </div>
                )}
                {/* 인식되면 프리뷰를 잠깐 덮어 "찍혔다"는 걸 보여준다. */}
                {flash && (
                  <div
                    className={`pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-2 border-4 ${flashStyles[flash.tone]}`}
                  >
                    {flash.tone === "pass" ? (
                      <CheckCircle2 size={56} />
                    ) : flash.tone === "reject" ? (
                      <XCircle size={56} />
                    ) : (
                      <RotateCcw size={56} />
                    )}
                    <p className="text-xl font-extrabold">{flash.title}</p>
                  </div>
                )}
              </div>
              <div className="flex items-center gap-2">
                <Button type="button" variant={cameraOn ? "outline" : "primary"} onClick={() => setCameraOn(!cameraOn)}>
                  <Camera size={16} />
                  {cameraOn ? "카메라 끄기" : "카메라 켜기"}
                </Button>
                {cameraOn && (
                  <span className="text-sm text-muted">
                    {scanning
                      ? "확인 중…"
                      : flash
                        ? `${flash.title} · 다음 QR을 준비해 주세요.`
                        : "QR을 사각형 안에 비춰 주세요."}
                  </span>
                )}
              </div>
              {cameraError && <p className="text-sm font-bold text-primary-strong">{cameraError}</p>}
            </>
          )}
        </div>
      )}

      <form onSubmit={handleManualScan} className="space-y-4">
        {mode === "manual" && (
          <div>
            <label htmlFor={inputId} className="mb-1.5 block text-sm font-bold text-ink">
              입장 QR 토큰
            </label>
            <div className="flex gap-2">
              {/* Input 컴포넌트는 ref를 받지 않아(공유 컴포넌트 미변경) 스캔 포커스용으로만 네이티브 input 사용 */}
              <input
                id={inputId}
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
        )}
        {extraFields}
      </form>
    </Card>
  );
}
