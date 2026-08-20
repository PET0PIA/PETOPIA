import { CreditCard, Landmark, Wallet } from "lucide-react";
import type { PaymentMethodOption } from "../../payments/toss";

const METHOD_OPTIONS: { value: PaymentMethodOption; label: string; icon: typeof CreditCard }[] = [
  // CARD는 토스 통합 결제창이라 카드와 간편결제(카카오페이 등)를 함께 고를 수 있다.
  // NAVER_PAY는 네이버페이 자체창을 바로 여는 선택지라, 라벨도 제공사 이름 그대로 쓴다.
  { value: "CARD", label: "카드·간편결제", icon: CreditCard },
  { value: "NAVER_PAY", label: "네이버페이", icon: Wallet },
  { value: "VIRTUAL_ACCOUNT", label: "가상계좌", icon: Landmark },
];

/**
 * 결제수단 선택 탭(카드·네이버페이·가상계좌). 예약금·참가비·개설비 3개 결제 화면이 공통으로 쓴다.
 *
 * 아래 두 한계는 해소됨(PaymentService 참고): 간편결제 제공사는 payment.easy_pay_provider 컬럼에
 * 저장되고, 가상계좌는 승인 응답이 WAITING_FOR_DEPOSIT이면 COMPLETED로 바로 확정하지 않고 그
 * 상태로 저장했다가 입금 웹훅(/webhooks/toss/deposit-callback)으로 COMPLETED 전환한다.
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
