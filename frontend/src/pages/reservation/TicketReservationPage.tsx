import { CalendarDays, Clock, CreditCard, MapPin, QrCode } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { QrCanvas } from "../../components/ui/QrCanvas";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import { getFairPublicSummary, type FairPublicSummary } from "../../api/fair";
import { createReservationDepositPayment } from "../../api/payment";
import {
  ADVANCE_TERMS_VERSION,
  createAdvanceReservation,
  createOnsiteReservation,
  getReservationAvailability,
  ONSITE_TERMS_VERSION,
  type ReservationAvailability,
  type ReservationAvailabilityDate,
} from "../../api/reservation";
import { releaseWaitingSlot, WAITING_ROOM_REQUIRED_CODE } from "../../api/waitingRoom";
import { useAuth } from "../../contexts/AuthContext";
import { todayInSeoul } from "../../utils/date";
import { WaitingRoomPanel } from "./WaitingRoomPanel";
import {
  isTossConfigured,
  requestReservationPayment,
  RESERVATION_PAYMENT_METHODS,
  type ReservationPaymentMethod,
} from "../../payments/toss";
import { PaymentMethodPicker } from "../../components/payment/PaymentMethodPicker";
import { PetCompanionPicker } from "../../components/reservation/PetCompanionPicker";
import { formatEntryTime, formatVisitDateDow, reservationTypeLabels } from "./reservationDisplay";

type ReservationType= "ADVANCE" | "ONSITE";
/** waiting: 대기열에 막혀 순번을 기다리는 중. 통과하면 곧바로 예약을 재시도한다. */
type Phase = "form" | "waiting" | "done";

interface DoneInfo {
  visitDate: string;
  typeLabel: string;
  entryQrToken: string | null;
}
interface PaymentInfo {
  /** 결제 대상 예약. 결제 생성 API가 이 ID로 원장 금액을 조회한다. */
  reservationId: number;
  visitDate: string;
  typeLabel: string;
  /** 화면에 보여준 예약금이 아니라 예약 생성 응답의 서버 계산값. */
  amount: number;
  /** 결제 제한시각(ISO). 지나면 서버 배치가 예약을 만료시킨다. */
  paymentExpiresAt: string | null;
}

/**
 * 결제 제한시각까지 남은 시간을 "m:ss"로 만든다. 제한시각이 없거나 이미 지났으면 null.
 *
 * 백엔드는 LocalDateTime을 오프셋 없이("2026-08-09T12:34:56") 내려주는데, 서버·DB·컨테이너가
 * 전부 Asia/Seoul로 고정돼 있어(Dockerfile / docker-compose의 TZ) 브라우저 로컬 시각으로
 * 파싱해도 어긋나지 않는다.
 */
function formatRemaining(expiresAt: string | null, now: number): string | null {
  if (!expiresAt) return null;
  const diff = new Date(expiresAt).getTime() - now;
  if (Number.isNaN(diff) || diff <= 0) return null;
  const totalSeconds = Math.floor(diff / 1000);
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, "0")}`;
}

// 운영 시작~종료일을 "2026-09-18 ~ 09-20" 형태로 다듬는다.
// 단, 해가 바뀌는 기간(2026-12-30 ~ 2027-01-02)은 종료 연도를 남긴다.
function formatPeriod(start: string | null, end: string | null) {
  if (!start) return "";
  if (!end || end === start) return start;
  const sameYear = start.slice(0, 4) === end.slice(0, 4);
  return `${start} ~ ${sameYear ? end.slice(5) : end}`;
}

/**
 * 지금 실제로 예약을 넣을 수 있는 날짜인지. 목록에 떠 있어도 마감(available=false)이거나
 * 잔여석이 없으면 예약이 되지 않으므로, 날짜 카드의 "마감" 표시와 사전예약 노출 판정이
 * 같은 기준을 쓰도록 한곳에 모아둔다.
 */
function isReservableDate(date: ReservationAvailabilityDate): boolean {
  return date.available && date.remainingCapacity > 0;
}

/**
 * 오늘이 행사 운영기간(운영 시작일~종료일) 안인지 판단한다. 현장예매는 운영 당일에만
 * 가능하므로, 이 값이 참일 때만 현장예매를 화면에 노출한다.
 * 오늘 날짜는 브라우저 시간대가 아니라 Asia/Seoul 기준으로 구한다(해외 기기에서 자정
 * 근처에 하루 밀려 현장예매가 잘못 열리거나 닫히는 것을 막는다).
 * 운영기간을 모르면(행사 정보 로드 실패) 현장예매를 막지 않으려 true로 폴백한다 —
 * 현장에서 실제 예매하려는 사람을 실수로 차단하지 않기 위함이다(최종 판정은 백엔드).
 */
function isOperatingToday(start: string | null, end: string | null): boolean {
  if (!start || !end) return true;
  const today = todayInSeoul();
  return start <= today && today <= end;
}

export function TicketReservationPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const id = Number(fairId);
  // 경로 파라미터가 양의 정수가 아니면(예: /tickets/abc) NaN을 API URL에 싣지 않는다.
  const idValid = Number.isInteger(id) && id > 0;
  const { confirm, confirmDialog } = useConfirm();
  // 결제 API는 아직 JWT가 아니라 X-User-Id 헤더로 결제자를 식별한다. JWT의 sub를 디코딩한
  // 값이라 결제 API가 JWT로 넘어가기 전에도 정확하다.
  const { user } = useAuth();
  const navigate = useNavigate();

  const [availability, setAvailability] = useState<ReservationAvailability | null>(null);
  const [fair, setFair] = useState<FairPublicSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [type, setType] = useState<ReservationType>("ADVANCE");
  const [selectedVisitDate, setSelectedVisitDate] = useState<string | null>(null);
  const [agreed, setAgreed] = useState(false); // 사전예약 유료 약관
  const [onsiteAgreed, setOnsiteAgreed] = useState(false); // 현장예매 환불불가 약관
  // 함께 갈 반려동물. 사전예약·현장예매가 같은 값을 쓴다(유형을 바꿀 때 초기화한다).
  const [petIds, setPetIds] = useState<number[]>([]);
  const [phase, setPhase] = useState<Phase>("form");

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [doneInfo, setDoneInfo] = useState<DoneInfo | null>(null);
  const [paymentInfo, setPaymentInfo] = useState<PaymentInfo | null>(null);
  // 예약금 결제 팝업 열림 여부. 예약 생성 후 결제가 필요하면 이 팝업을 띄운다.
  const [paymentOpen, setPaymentOpen] = useState(false);

  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);
  // 예약금은 가상계좌를 쓸 수 없어서 타입부터 좁혀 둔다(ReservationPaymentMethod 주석 참고).
  const [paymentMethod, setPaymentMethod] = useState<ReservationPaymentMethod>("CARD");
  const [now, setNow] = useState(() => Date.now());

  // 카운트다운은 결제 팝업이 열렸을 때만 돌린다 - 폼/완료 화면에서 1초마다 리렌더할 이유가 없다.
  useEffect(() => {
    if (!paymentOpen) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [paymentOpen]);

  useEffect(() => {
    if (!idValid) return; // 잘못된 경로 파라미터면 요청하지 않는다
    let alive = true;
    // 행사 이름·장소는 fair 도메인, 예약금·날짜는 예약 도메인에서 각각 가져온다.
    // 행사 정보 조회는 실패해도 예매는 계속할 수 있게 막지 않는다(이름만 못 보여줄 뿐).
    getFairPublicSummary(id)
      .then((res) => {
        if (alive) setFair(res);
      })
      .catch(() => {
        /* 헤더 표시용이라 실패를 치명적으로 다루지 않는다. */
      });
    getReservationAvailability(id)
      .then((res) => {
        if (alive) setAvailability(res);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "예매 정보를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [id, idValid]);

  if (!idValid) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <EmptyState
          title="잘못된 예매 주소예요."
          description="행사 주소가 올바르지 않아요. 예매 가능한 행사에서 다시 선택해 주세요."
          actionTo="/fairs/upcoming"
          actionLabel="예매 가능한 행사 보기"
        />
      </div>
    );
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <p className="py-16 text-center text-sm text-muted">예매 정보를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (loadError || !availability) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <EmptyState
          title="지금은 예매할 수 없어요."
          description={loadError ?? "예매 정보를 불러오지 못했어요."}
          actionTo="/fairs/upcoming"
          actionLabel="예매 가능한 행사 보기"
        />
      </div>
    );
  }

  const fairName = fair?.name ?? `행사 #${id}`;
  const period = formatPeriod(fair?.operationStartDate ?? null, fair?.operationEndDate ?? null);

  const selectedDate = availability.dates.find((date) => date.visitDate === selectedVisitDate) ?? null;

  const price = availability.reservationFee; // 사전예약금
  const isPaid = price > 0;
  const advanceVisitDate = selectedDate?.visitDate ?? null;

  const canProceedAdvance = selectedDate !== null && (!isPaid || agreed) && !submitting;
  const canProceedOnsite = onsiteAgreed && !submitting;

  // 이 화면에 어떤 예약 유형을 노출할지 결정한다.
  // - 사전예약: 잔여석이 남은 날짜가 하나라도 있어야 노출한다(당일은 백엔드가 이미 목록에서
  //   제외한다). 날짜는 내려오지만 전부 매진이면 진행할 수 없는 폼을 띄우게 되므로 제외한다.
  // - 현장예매: 오늘이 행사 운영기간 안일 때만 노출(현장예매는 운영 당일에만 생성 가능).
  // 여러 날 행사에선 운영 중에도 남은 날짜 사전예약이 열려 있어 둘 다 뜰 수 있다.
  const advanceAvailable = availability.dates.some(isReservableDate);
  const onsiteAvailable = isOperatingToday(fair?.operationStartDate ?? null, fair?.operationEndDate ?? null);
  const bothTypes = advanceAvailable && onsiteAvailable;
  // 실제로 그릴 유형. 둘 다면 사용자가 고른 type을, 하나만 되면 그쪽으로 고정한다.
  const activeType: ReservationType = bothTypes ? type : onsiteAvailable ? "ONSITE" : "ADVANCE";

  // 클라이언트 키가 안 들어와 있으면 결제 버튼 대신 안내를 띄운다 - 키 누락을 런타임 에러가
  // 아니라 UI로 드러내려는 목적이다.
  const tossReady = isTossConfigured();
  const expiresAt = paymentInfo?.paymentExpiresAt ?? null;
  const remaining = formatRemaining(expiresAt, now);
  // 현재 백엔드 confirmPayment는 만료를 검증하지 않는다. 만료 후 결제하면 결제는 승인되고
  // 예약 통지만 거절당해 돈만 나가므로, 이 가드가 지금은 유일한 방어선이다.
  // 단, 파싱 불가한 값은 '만료'가 아니라 잘못된 데이터이므로 결제를 막지 않는다(영구 잠금 방지).
  const expiresAtMs = expiresAt !== null ? new Date(expiresAt).getTime() : null;
  const expired = expiresAtMs !== null && !Number.isNaN(expiresAtMs) && expiresAtMs - now <= 0;

  function switchType(next: ReservationType) {
    setType(next);
    setAgreed(false);
    setOnsiteAgreed(false);
    setPetIds([]);
    setSubmitError(null);
  }

  async function handleAdvance() {
    if (!advanceVisitDate) return;
    // 유료는 확인창 없이 바로 예약을 만들고 결제 팝업을 띄운다 - 팝업이 금액·제한시간을 보여주며
    // 확인 역할을 겸한다. 무료는 결제가 없어 여기서 곧바로 확정되므로 확인창을 한 번 둔다.
    if (!isPaid) {
      const proceed = await confirm({
        title: "예약할까요?",
        description: `${fairName} · ${formatVisitDateDow(advanceVisitDate)} 방문으로 사전예약해요. 무료 예약이에요.`,
        confirmLabel: "예약하기",
      });
      if (!proceed) return;
    }
    await submitAdvance();
  }

  /**
   * 확인 절차 없이 예약 생성만 수행한다.
   *
   * 대기열을 통과한 뒤 자동 재시도할 때도 이 함수를 쓴다 - 이미 동의를 받아둔 요청이라
   * 순서를 기다렸다고 확인 창을 다시 띄우면 사용자가 그 사이 자리를 잃는다.
   */
  async function submitAdvance() {
    if (!advanceVisitDate) return;

    setSubmitError(null);
    setSubmitting(true);
    try {
      // 유료도 예약을 먼저 만든다 - 결제 생성 API가 reservationId로 원장 금액을 조회하므로
      // 예약이 없으면 결제를 시작할 수 없다. 유료는 약관 동의가 필수다(R006).
      const created = await createAdvanceReservation(id, {
        visitDate: advanceVisitDate,
        ...(isPaid ? { reservationTermsAgreed: true, reservationTermsVersion: ADVANCE_TERMS_VERSION } : {}),
        // 0마리도 유효한 예약이라(정책 P3) 빈 배열이면 필드를 빼고 보낸다.
        ...(petIds.length > 0 ? { petIds } : {}),
      });

      if (created.paymentRequired) {
        // 대기 슬롯을 여기서 놓지 않는다. 결제 API도 같은 슬롯으로 통과해야 하고,
        // 슬롯 수명(12분)이 결제 제한시간(10분)보다 길게 잡혀 있는 이유가 이것이다.
        setPaymentInfo({
          reservationId: created.reservationId,
          visitDate: advanceVisitDate,
          typeLabel: reservationTypeLabels.ADVANCE,
          amount: created.amount,
          paymentExpiresAt: created.paymentExpiresAt,
        });
        setPayError(null);
        setNow(Date.now()); // 카운트다운 첫 프레임이 옛날 값으로 그려지지 않게 맞춰둔다
        setPaymentOpen(true);
        return;
      }

      // 무료: 서버가 한 트랜잭션에서 CONFIRMED + QR까지 끝낸다.
      // 결제가 없어 여기서 흐름이 끝나므로 슬롯을 바로 돌려준다. 안 그러면 이미 볼일이
      // 끝난 사람이 12분간 자리를 묶어 뒷사람 유입을 늦춘다.
      releaseWaitingSlot(id);
      setDoneInfo({ visitDate: advanceVisitDate, typeLabel: reservationTypeLabels.ADVANCE, entryQrToken: created.entryQrToken });
      setPhase("done");
    } catch (err) {
      // 대기열에 막힌 경우(429/R022)는 실패가 아니라 "순서를 기다려야 한다"는 뜻이다.
      // 대기 화면으로 넘기고, 통과하면 이 요청을 그대로 재시도한다.
      if (err instanceof ApiError && err.code === WAITING_ROOM_REQUIRED_CODE) {
        setPhase("waiting");
        return;
      }
      // R001 행사없음 / R002 날짜불가 / R003 접수아님 / R004 마감 / R005 중복 / R006 약관
      // 여기까지 온 건 재시도해도 결과가 같은 실패들이다. 쓰지 않을 슬롯은 돌려준다.
      releaseWaitingSlot(id);
      setSubmitError(err instanceof ApiError ? err.message : "예약에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleOnsite() {
    const proceed = await confirm({
      title: "현장예매 할까요?",
      description: `${fairName} · 오늘 방문으로 현장예매해요. 현장예매는 취소·환불이 불가능해요.`,
      confirmLabel: "현장예매 하기",
    });
    if (!proceed) return;

    setSubmitError(null);
    setSubmitting(true);
    try {
      const created = await createOnsiteReservation(id, {
        reservationTermsAgreed: true,
        reservationTermsVersion: ONSITE_TERMS_VERSION,
        ...(petIds.length > 0 ? { petIds } : {}),
      });
      if (created.paymentRequired) {
        // 현장예매는 이미 예약을 만들고 있었으므로 결제 단계로 넘길 값만 채운다.
        setPaymentInfo({
          reservationId: created.reservationId,
          visitDate: created.visitDate,
          typeLabel: reservationTypeLabels.ONSITE_DIRECT,
          amount: created.amount,
          paymentExpiresAt: created.paymentExpiresAt,
        });
        setPayError(null);
        setNow(Date.now());
        setPaymentOpen(true);
      } else {
        setDoneInfo({ visitDate: created.visitDate, typeLabel: reservationTypeLabels.ONSITE_DIRECT, entryQrToken: created.entryQrToken });
        setPhase("done");
      }
    } catch (err) {
      // R007 접수아님/시간지남 / R008 일시중지 / R005 활성 예약 중복
      setSubmitError(err instanceof ApiError ? err.message : "현장예매에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  /**
   * 결제 생성 → 토스 결제창. 정상 흐름이면 결제창이 뜨고 그 뒤 /payments/success 로
   * 리다이렉트되므로, 이 함수가 끝까지 진행되면 페이지는 곧 사라진다.
   */
  async function handlePay() {
    if (!paymentInfo || paying || expired) return;
    if (!user) {
      setPayError("로그인이 풀렸어요. 다시 로그인한 뒤 내 예약 목록에서 결제를 이어가 주세요.");
      return;
    }

    setPayError(null);
    setPaying(true);
    try {
      const created = await createReservationDepositPayment(paymentInfo.reservationId, user.userId);
      await requestReservationPayment({
        paymentId: created.paymentId,
        reservationId: paymentInfo.reservationId,
        // 결제 실패로 되돌아왔을 때 대기 슬롯을 반납할 수 있게 같이 실어 보낸다.
        fairId: id,
        // 서버가 만든 orderId·amount를 가공 없이 넘긴다 - 다르면 토스가 승인을 거절한다.
        orderId: created.orderId,
        amount: created.amount,
        orderName: `${fairName} 예약금`,
        method: paymentMethod,
      });
      // 리다이렉트가 시작됐으므로 paying을 되돌리지 않는다(버튼이 다시 눌리면 안 된다).
    } catch (err) {
      // 결제창을 사용자가 닫은 경우도 여기로 온다.
      setPayError(err instanceof ApiError ? err.message : "결제를 시작하지 못했어요. 잠시 후 다시 시도해 주세요.");
      setPaying(false);
    }
  }

  /**
   * 결제 팝업을 닫으려 할 때(X·배경 클릭·ESC) 부른다. 예매는 결제까지 마쳐야 완료되므로 그냥
   * 닫지 않고 "아직 완료되지 않았다"고 확인한다. 나가기를 택하면 예약은 PENDING_PAYMENT로 남아
   * 내 예약 목록에서 제한시간(약 10분) 안에 결제할 수 있고, 안 하면 자동으로 만료된다.
   */
  async function attemptClosePayment() {
    // 결제창을 여는 중(결제 생성 API 응답 대기 + 토스 SDK 호출)에는 닫기를 받지 않는다.
    // 여기서 나가버리면 화면은 내 예약 목록으로 떠난 뒤에 진행 중이던 요청이 뒤늦게 끝나면서
    // 사용자가 그만두기로 한 결제창을 띄운다. 결제 버튼도 이 구간에는 이미 비활성이다.
    if (paying) return;
    const leave = await confirm({
      title: "예약이 아직 완료되지 않았어요",
      description: "결제를 마치지 않고 나가면 예약이 완료되지 않아요. 이 예약은 '내 예약 목록'에 "
        + "약 10분간 결제 대기로 남아 있다가, 결제하지 않으면 자동으로 만료돼요. 그래도 나갈까요?",
      confirmLabel: "나가기",
    });
    if (!leave) return; // 계속 결제
    setPaymentOpen(false);
    navigate("/reservations/me");
  }

  // 대기 화면. 통과하면 기다리게 만든 그 예약 요청을 그대로 이어서 보낸다.
  if (phase === "waiting") {
    return (
      <WaitingRoomPanel
        fairId={id}
        fairName={fairName}
        onAdmitted={() => {
          setPhase("form");
          void submitAdvance();
        }}
        onCancel={() => setPhase("form")}
      />
    );
  }

  // 완료 화면
  if (phase === "done" && doneInfo) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <Card className="grid place-items-center p-8 text-center">
          <div className="mb-4 grid size-14 place-items-center rounded-full bg-leaf-soft text-ink">
            <QrCode size={28} />
          </div>
          <h2 className="text-lg font-extrabold">예약이 확정됐어요</h2>
          <p className="mt-2 text-sm leading-6 text-muted">
            {fairName} · {formatVisitDateDow(doneInfo.visitDate)} 방문 · {doneInfo.typeLabel}
            <br />
            예약이 바로 확정됐어요. 아래 입장 QR을 현장 스캐너에 보여주세요.
          </p>
          <div className="my-5 grid place-items-center">
            {doneInfo.entryQrToken ? (
              <QrCanvas value={doneInfo.entryQrToken} size={176} />
            ) : (
              <p className="text-sm text-muted">입장 QR은 내 예약 목록에서 확인할 수 있어요.</p>
            )}
          </div>
          <div className="flex gap-2">
            <Link
              to="/reservations/me"
              className="inline-flex min-h-11 items-center rounded-button bg-primary-strong px-4 text-sm font-bold text-white hover:opacity-90"
            >
              내 예약 목록으로
            </Link>
            <Link
              to="/fairs/upcoming"
              className="inline-flex min-h-11 items-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page"
            >
              다른 행사 보기
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  // 사전예약·현장예매 어느 쪽도 지금은 불가능하면(접수 전/후, 매진, 행사 종료 등) 폼 대신 안내를 띄운다.
  if (!advanceAvailable && !onsiteAvailable) {
    // 날짜는 내려왔는데 전부 잔여석이 없는 경우와, 애초에 접수 기간이 아닌 경우를 구분해 안내한다.
    const allSoldOut = availability.dates.length > 0;
    return (
      <div className="mx-auto max-w-3xl py-2">
        <EmptyState
          title={allSoldOut ? "예매 가능한 방문일이 없어요." : "지금은 예매할 수 없어요."}
          description={
            allSoldOut
              ? "모든 방문일이 마감됐어요. 예매 가능한 다른 행사를 확인해 주세요."
              : "예매 접수가 마감되었거나 아직 시작되지 않았어요. 예매 가능한 다른 행사를 확인해 주세요."
          }
          actionTo="/fairs/upcoming"
          actionLabel="예매 가능한 행사 보기"
        />
      </div>
    );
  }

  // 폼 (기본)
  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader eyebrow="티켓 예매" title="예매하기" description="예약 유형과 방문일을 고르고 예약을 진행해요." />

      {/* 행사 정보 */}
      <Card className="mb-6 p-5">
        <h2 className="text-lg font-extrabold text-ink">{fairName}</h2>
        <div className="mt-2 flex flex-col gap-1.5 text-sm text-muted">
          {fair?.placeName && (
            <span className="flex items-center gap-2">
              <MapPin size={16} className="shrink-0" />
              {fair.placeName}
            </span>
          )}
          {period && (
            <span className="flex items-center gap-2">
              <CalendarDays size={16} className="shrink-0" />
              {period}
            </span>
          )}
        </div>
      </Card>

      {/* 예약 유형 선택: 사전예약·현장예매가 모두 가능한 기간에만 고르게 한다. */}
      {bothTypes && (
        <>
          <h3 className="mb-3 text-sm font-bold text-ink">예약 유형</h3>
          <div className="mb-6 grid grid-cols-2 gap-3">
            <button
              type="button"
              onClick={() => switchType("ADVANCE")}
              className={`rounded-card border p-4 text-left transition ${
                activeType === "ADVANCE" ? "border-primary-strong bg-primary-soft" : "border-line bg-card hover:bg-page"
              }`}
            >
              <p className="font-bold text-ink">사전예약</p>
              <p className="mt-1 text-xs text-muted">방문일을 골라 미리 예약</p>
            </button>
            <button
              type="button"
              onClick={() => switchType("ONSITE")}
              className={`rounded-card border p-4 text-left transition ${
                activeType === "ONSITE" ? "border-primary-strong bg-primary-soft" : "border-line bg-card hover:bg-page"
              }`}
            >
              <p className="font-bold text-ink">현장예매</p>
              <p className="mt-1 text-xs text-muted">오늘 방문, 현장에서 바로 입장</p>
            </button>
          </div>
        </>
      )}

      {/* 유형별 내용 */}
      {activeType === "ADVANCE" ? (
        <>
          <h3 className="mb-3 text-sm font-bold text-ink">
            방문일 선택<span className="ml-1 text-primary-strong">*</span>
          </h3>
          {/* 사전예약을 그리는 시점엔 예약 가능한 날짜가 반드시 하나는 있다(advanceAvailable).
              아래 빈 목록 분기는 판정이 바뀌었을 때를 대비한 안전망으로만 남겨둔다. */}
          {availability.dates.length === 0 ? (
            <Card className="mb-6 p-4 text-sm text-muted">지금 예매할 수 있는 방문일이 없어요.</Card>
          ) : (
            <div className="mb-6 grid gap-3 sm:grid-cols-3">
              {availability.dates.map((date) => {
                const soldOut = !isReservableDate(date);
                const selected = date.visitDate === selectedVisitDate;
                return (
                  <button
                    key={date.visitDate}
                    type="button"
                    disabled={soldOut}
                    onClick={() => setSelectedVisitDate(date.visitDate)}
                    className={`rounded-card border p-4 text-left transition ${
                      selected ? "border-primary-strong bg-primary-soft" : "border-line bg-card hover:bg-page"
                    } ${soldOut ? "cursor-not-allowed opacity-50 hover:bg-card" : ""}`}
                  >
                    <p className="font-bold text-ink">{formatVisitDateDow(date.visitDate)}</p>
                    <p className="mt-1 text-xs text-muted">
                      {formatEntryTime(date.entryStartTime)} ~ {formatEntryTime(date.entryEndTime)}
                    </p>
                    <p className="mt-2 text-xs font-bold">
                      {soldOut ? (
                        <span className="text-muted">마감</span>
                      ) : (
                        <span className="text-ink">잔여 {date.remainingCapacity}석</span>
                      )}
                    </p>
                  </button>
                );
              })}
            </div>
          )}

          {/* 동반 반려동물. 동반 금지 행사에서는 이 블록이 아예 그려지지 않는다(정책 P2). */}
          <PetCompanionPicker
            petAllowed={availability.petAllowed}
            value={petIds}
            onChange={setPetIds}
            disabled={submitting}
          />

          {/* 금액 */}
          <Card className="mb-6 p-5">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted">결제 금액</span>
              <span className="text-lg font-extrabold text-ink">
                {isPaid ? `${price.toLocaleString()}원` : "무료"}
              </span>
            </div>
          </Card>

          {/* 약관 동의: 유료일 때만 */}
          {isPaid && (
            <label className="mb-6 flex cursor-pointer items-start gap-2.5 text-sm text-ink">
              <input
                type="checkbox"
                checked={agreed}
                onChange={(event) => setAgreed(event.target.checked)}
                className="mt-0.5 size-4 accent-primary-strong"
              />
              <span>예약 및 취소·환불 규정을 확인했고 이에 동의해요.</span>
            </label>
          )}

          {isPaid && (
            <p className="mb-4 text-sm text-muted">
              ※ 다음 화면에서 결제를 마쳐야 예약이 확정돼요. 예약을 잡아둔 뒤 10분 안에 결제해 주세요.
            </p>
          )}

          {submitError && <p className="mb-4 text-sm font-bold text-primary-strong">{submitError}</p>}

          <div className="flex justify-end">
            <Button disabled={!canProceedAdvance} onClick={handleAdvance}>
              {submitting ? "예약 중…" : isPaid ? "결제하기" : "예약 완료하기"}
            </Button>
          </div>
        </>
      ) : (
        <>
          <h3 className="mb-3 text-sm font-bold text-ink">현장예매</h3>
          <Card className="mb-6 p-5 text-sm leading-6 text-muted">
            <p className="font-bold text-ink">오늘 방문 · 현장에서 바로 입장</p>
            <p className="mt-1">
              현장예매는 오늘 방문만 가능하고, 예매 후 바로 입장할 수 있어요. 판매 상태·가격은 행사마다
              달라서 예매를 시도하면 확인돼요.
            </p>
            <p className="mt-1 font-bold text-primary-strong">현장예매는 취소·환불이 불가능해요.</p>
          </Card>

          <PetCompanionPicker
            petAllowed={availability.petAllowed}
            value={petIds}
            onChange={setPetIds}
            disabled={submitting}
          />

          <label className="mb-6 flex cursor-pointer items-start gap-2.5 text-sm text-ink">
            <input
              type="checkbox"
              checked={onsiteAgreed}
              onChange={(event) => setOnsiteAgreed(event.target.checked)}
              className="mt-0.5 size-4 accent-primary-strong"
            />
            <span>현장예매 취소·환불 불가 규정을 확인했고 이에 동의해요.</span>
          </label>

          {submitError && <p className="mb-4 text-sm font-bold text-primary-strong">{submitError}</p>}

          <div className="flex justify-end">
            <Button disabled={!canProceedOnsite} onClick={handleOnsite}>
              {submitting ? "처리 중…" : "현장예매 하기"}
            </Button>
          </div>
        </>
      )}

      {/* 예약금 결제 팝업. 예매는 결제까지 마쳐야 완료된다. 닫으려 하면 완료 안 됨을 경고한다. */}
      {paymentInfo && (
        <Dialog open={paymentOpen} onClose={attemptClosePayment} title="예약금 결제">
          <div className="space-y-4">
            <div className="surface flex items-center justify-between gap-4 p-4">
              <div className="min-w-0">
                <p className="truncate font-bold text-ink">{fairName}</p>
                <p className="mt-1 text-sm text-muted">
                  {formatVisitDateDow(paymentInfo.visitDate)} 방문
                  {paymentInfo.typeLabel ? ` · ${paymentInfo.typeLabel}` : ""}
                </p>
              </div>
              <p className="shrink-0 text-lg font-extrabold text-ink">{paymentInfo.amount.toLocaleString()}원</p>
            </div>

            {expiresAt && (
              <div className="flex items-center gap-2 text-sm">
                <Clock size={16} className="shrink-0 text-muted" />
                {expired ? (
                  <span className="font-bold text-primary-strong">
                    결제 제한시각이 지났어요. 이 예약은 곧 만료되니 다시 예매해 주세요.
                  </span>
                ) : (
                  <span className="text-muted">
                    결제 제한시각까지 <b className="text-ink">{remaining}</b> 남았어요.
                  </span>
                )}
              </div>
            )}

            {tossReady ? (
              <div>
                <div className="mb-2 flex items-center gap-2 text-sm font-bold text-ink">
                  <CreditCard size={16} />
                  결제 수단
                </div>
                <PaymentMethodPicker
                  value={paymentMethod}
                  onChange={setPaymentMethod}
                  options={RESERVATION_PAYMENT_METHODS}
                  disabled={paying}
                />
              </div>
            ) : (
              <div className="grid place-items-center gap-1 rounded-button border border-dashed border-line bg-page py-8 text-center text-sm text-muted">
                <p className="font-bold text-ink">결제 설정이 없어요</p>
                <p>결제 클라이언트 키가 주입되지 않았어요. 관리자에게 문의해 주세요.</p>
              </div>
            )}

            {payError && <p className="text-sm font-bold text-primary-strong">{payError}</p>}

            <div className="flex justify-end pt-1">
              <Button disabled={!tossReady || expired || paying} onClick={handlePay}>
                {paying ? "결제창을 여는 중…" : "결제하기"}
              </Button>
            </div>

            {/* 이 구간에는 닫기(X·배경·ESC)가 막혀 있어서, 눌러도 아무 일이 없는 것처럼 보이지 않게 안내한다. */}
            {paying && (
              <p className="text-right text-xs text-muted">결제창을 여는 중에는 창을 닫을 수 없어요. 잠시만 기다려 주세요.</p>
            )}
          </div>
        </Dialog>
      )}

      {confirmDialog}
    </div>
  );
}
