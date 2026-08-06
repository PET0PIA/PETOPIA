import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";

interface QrCanvasProps {
  /** QR로 만들 값(입장 QR 토큰 등) */
  value: string;
  /** QR 한 변의 픽셀 크기 */
  size?: number;
}

/**
 * 값(문자열)을 받아 캔버스에 QR 코드를 그려주는 컴포넌트.
 * 강사님 제공 Generator의 핵심(QRCode.toCanvas)만 재사용하고, 입력창·다운로드 UI는 뺐다.
 */
export function QrCanvas({ value, size = 200 }: QrCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    if (!value.trim()) {
      canvas.getContext("2d")?.clearRect(0, 0, canvas.width, canvas.height);
      return;
    }

    QRCode.toCanvas(canvas, value, { width: size, margin: 2 })
      .then(() => setError(""))
      .catch((err: Error) => setError(err.message));
  }, [value, size]);

  if (error) {
    return <p className="text-sm text-muted">QR을 만들지 못했어요: {error}</p>;
  }

  return <canvas ref={canvasRef} className="rounded-button" aria-label="입장 QR 코드" />;
}
