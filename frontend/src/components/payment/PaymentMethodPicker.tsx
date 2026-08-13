import { CreditCard, Landmark, Wallet } from "lucide-react";
import type { PaymentMethodOption } from "../../payments/toss";

const METHOD_OPTIONS: { value: PaymentMethodOption; label: string; icon: typeof CreditCard }[] = [
  { value: "CARD", label: "카드", icon: CreditCard },
  { value: "NAVER_PAY", label: "네이버페이", icon: Wallet },
  { value: "VIRTUAL_ACCOUNT", label: "가상계좌", icon: Landmark },
];

/**
 * 결제수단 선택 탭(카드·네이버페이·가상계좌). 예약금·참가비·개설비 3개 결제 화면이 공통으로 쓴다.
 *
 * 백엔드는 아직 이 선택을 완전히 따라가지 못한다 - method는 저장되지만(PaymentService.confirmPayment
 * 가 tossResponse.method()를 그대로 씀) 간편결제 제공사(NAVERPAY)는 별도 컬럼이 없어 구분 표시가
 * 안 되고, 가상계좌는 승인 응답이 WAITING_FOR_DEPOSIT으로 와도 지금 코드가 무조건 COMPLETED로
 * 확정해버린다(2차 예정, 실제 입금 전에 완료 처리되는 셈). 결제 자체는 되니 프론트를 먼저 붙인다.
 */
export function PaymentMethodPicker({
  value,
  onChange,
  disabled,
}: {
  value: PaymentMethodOption;
  onChange: (method: PaymentMethodOption) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="결제 수단">
      {METHOD_OPTIONS.map(({ value: option, label, icon: Icon }) => {
        const active = value === option;
        return (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={active}
            disabled={disabled}
            onClick={() => onChange(option)}
            className={`flex min-h-16 flex-col items-center justify-center gap-1 rounded-button border px-2 py-3 text-xs font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${
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
