import { CreditCard, Landmark, Wallet } from "lucide-react";
import type { PaymentMethodOption } from "../../payments/toss";

// CARD는 토스 통합 결제창이라 카드와 간편결제(카카오페이 등)를 함께 고를 수 있다.
// NAVER_PAY는 네이버페이 자체창을 바로 여는 선택지라, 라벨도 제공사 이름 그대로 쓴다.
const METHOD_META: Record<PaymentMethodOption, { label: string; icon: typeof CreditCard }> = {
  CARD: { label: "카드·간편결제", icon: CreditCard },
  NAVER_PAY: { label: "네이버페이", icon: Wallet },
  VIRTUAL_ACCOUNT: { label: "가상계좌", icon: Landmark },
};

// 테일윈드는 클래스명을 문자열 그대로 훑어서 CSS를 만들기 때문에 `grid-cols-${n}`처럼
// 조립하면 그 클래스가 빌드 결과에 없다. 쓸 값만 미리 적어둔다.
const COLUMN_CLASS: Record<number, string> = {
  1: "grid-cols-1",
  2: "grid-cols-2",
  3: "grid-cols-3",
};

/**
 * 결제수단 선택 탭. 예약금·참가비·개설비 결제 화면이 공통으로 쓴다.
 *
 * 어떤 수단을 보여줄지는 화면마다 options로 명시한다 — 예약금은 RESERVATION_PAYMENT_METHODS
 * (가상계좌 제외), 참가비·개설비는 ALL_PAYMENT_METHODS(payments/toss.ts에 정의).
 * 기본값을 두지 않은 건 새 결제 화면이 생겼을 때 "무엇을 허용할지"를 그냥 넘기고 지나치지
 * 못하게 하려는 것이다.
 *
 * 백엔드 연동 상태(PaymentService 참고): 간편결제 제공사는 payment.easy_pay_provider 컬럼에
 * 저장되고, 가상계좌는 승인 응답이 WAITING_FOR_DEPOSIT이면 COMPLETED로 바로 확정하지 않고 그
 * 상태로 저장했다가 입금 웹훅(/webhooks/toss/deposit-callback)으로 COMPLETED 전환한다.
 * 단 예약금 결제는 그 승인 자체를 거절한다(PaymentService.rejectVirtualAccountForReservationDeposit).
 */
export function PaymentMethodPicker<T extends PaymentMethodOption>({
  value,
  onChange,
  options,
  disabled,
}: {
  value: T;
  onChange: (method: T) => void;
  /** 이 화면에서 고를 수 있는 결제수단. 보여주는 순서도 이 배열 순서를 따른다. */
  options: readonly T[];
  disabled?: boolean;
}) {
  return (
    <div
      className={`grid gap-2 ${COLUMN_CLASS[options.length] ?? "grid-cols-3"}`}
      role="radiogroup"
      aria-label="결제 수단"
    >
      {options.map((option) => {
        const { label, icon: Icon } = METHOD_META[option];
        const active = value === option;
        return (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={active}
            disabled={disabled}
            onClick={() => onChange(option)}
            className={`flex min-h-16 flex-col items-center justify-center gap-1 break-keep rounded-button border px-2 py-3 text-center text-xs font-bold leading-tight transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${
              active
                ? "border-primary-strong bg-primary-soft text-primary-strong"
                : "border-line bg-card text-muted hover:bg-page"
            }`}
          >
            <Icon size={18} />
            {label}
          </button>
        );
      })}
    </div>
  );
}
