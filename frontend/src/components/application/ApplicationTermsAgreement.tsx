import { useState } from "react";
import { Dialog } from "../ui/Dialog";

type AgreementKind = "terms" | "privacy";

interface ApplicationTermsAgreementProps {
  agreedTerms: boolean;
  agreedPrivacy: boolean;
  onTermsChange: (checked: boolean) => void;
  onPrivacyChange: (checked: boolean) => void;
}

// 법무 검토 전 QA용 초안이다. 운영 배포 전 실제 정책에 맞춰 검토·확정해야 한다.
function ApplicationTermsContent() {
  return (
    <div className="max-h-[60vh] space-y-5 overflow-y-auto pr-2 text-sm leading-6 text-muted">
      <section>
        <h3 className="font-extrabold text-ink">제1조 목적</h3>
        <p className="mt-1">
          이 유의사항은 승인된 참가업체(사업자)가 PETOPIA 행사의 부스 참가를 신청할 때 지켜야 할
          절차와 회사와 신청자 간의 권리·의무를 정함을 목적으로 합니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제2조 신청 자격</h3>
        <p className="mt-1">
          부스 참가 신청은 심사를 통과해 승인된 사업자만 할 수 있습니다. 사업자 등록이 반려되었거나
          취소된 경우 해당 사업자로는 신청할 수 없습니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제3조 부스 선택 및 임시 선점</h3>
        <p className="mt-1">
          신청 과정에서 선택한 부스 슬롯은 다른 신청자가 동시에 선택하지 못하도록 일시적으로
          선점됩니다. 신청을 완료하지 않고 이탈하면 선점이 해제되어 다른 신청자가 선택할 수 있습니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제4조 심사 및 확정</h3>
        <p className="mt-1">
          제출된 신청서는 담당자 심사를 거쳐 승인 또는 반려됩니다. 승인 전까지는 부스 참가가
          확정되지 않으며, 반려된 경우 반려 사유가 통지됩니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제5조 취소 요청</h3>
        <p className="mt-1">
          신청 취소는 취소 요청 제출 후 담당자 승인을 거쳐 처리됩니다. 한 번 제출한 취소 요청은
          철회할 수 없으므로 신중하게 결정해야 합니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제6조 환불</h3>
        <p className="mt-1">
          결제가 완료된 신청 건의 환불은 취소 요청이 승인된 경우에 한해 진행되며, 환불 금액 및
          처리 절차는 결제 시 안내된 정책을 따릅니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제7조 사업자 자격 상실에 따른 처리</h3>
        <p className="mt-1">
          신청에 사용된 사업자의 등록이 취소되는 경우, 아직 운영이 시작되지 않은 행사에 대한 참가
          신청은 자동으로 취소되며 결제된 금액이 있으면 환불됩니다. 이미 운영이 시작된 행사의
          신청 건은 자동으로 취소·환불되지 않으며, 회사가 별도로 확인 후 처리합니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제8조 책임의 한계</h3>
        <p className="mt-1">
          신청서에 기재한 정보가 사실과 다르거나 허위인 경우 그에 따른 불이익은 신청자에게 있으며,
          회사는 관련 법령이 정하는 범위에서만 책임을 부담합니다.
        </p>
      </section>
      <p className="border-t border-line pt-4 text-xs">
        시행 예정일: 2026년 8월 20일 · 본 내용은 QA용 초안이며 운영 배포 전 검토가 필요합니다.
      </p>
    </div>
  );
}

// 법무 검토 전 QA용 초안이다. 운영 배포 전 실제 정책·수집 항목에 맞춰 검토·확정해야 한다.
function ApplicationPrivacyContent() {
  return (
    <div className="max-h-[60vh] space-y-5 overflow-y-auto pr-2 text-sm leading-6 text-muted">
      <section>
        <h3 className="font-extrabold text-ink">1. 수집하는 개인정보</h3>
        <p className="mt-1">
          신청 담당자 이름, 연락처, 이메일, 참가 목적, 판매·전시 품목, 첨부파일(선택)을 수집합니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">2. 수집·이용 목적</h3>
        <ul className="mt-1 list-disc space-y-1 pl-5">
          <li>부스 참가 신청 심사 및 승인 여부 통지</li>
          <li>참가비 결제 안내 및 신청 관련 연락</li>
          <li>취소 요청 처리 및 환불 관련 안내</li>
        </ul>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">3. 보유 및 이용 기간</h3>
        <p className="mt-1">
          신청이 종료(취소·반려·행사 종료 등)되거나 관련 계정이 탈퇴할 때까지 보유하며, 목적이 달성되면
          지체 없이 파기합니다. 다만 관계 법령에 따라 보존할 필요가 있는 결제·환불 기록은 해당 법령이
          정한 기간 동안 별도로 보관할 수 있습니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">4. 동의 거부 권리</h3>
        <p className="mt-1">
          개인정보 수집·이용 동의를 거부할 수 있습니다. 다만 필수 정보 수집에 동의하지 않으면 부스 참가
          신청을 진행할 수 없습니다.
        </p>
      </section>
      <p className="border-t border-line pt-4 text-xs">
        시행 예정일: 2026년 8월 20일 · 본 내용은 QA용 초안이며 운영 배포 전 검토가 필요합니다.
      </p>
    </div>
  );
}

export function ApplicationTermsAgreement({ agreedTerms, agreedPrivacy, onTermsChange, onPrivacyChange }: ApplicationTermsAgreementProps) {
  const [openAgreement, setOpenAgreement] = useState<AgreementKind | null>(null);

  return (
    <div className="space-y-2">
      <label className="flex items-center gap-2 text-sm text-ink">
        <input
          type="checkbox"
          checked={agreedTerms}
          onChange={(event) => onTermsChange(event.target.checked)}
          className="mt-0.5"
        />
        <span>
          이용약관 및 참가 신청 유의사항에 동의합니다. <span className="text-primary-strong">*</span>
        </span>
      </label>
      <button
        type="button"
        className="text-xs font-bold text-muted underline underline-offset-4 hover:text-primary-strong"
        onClick={() => setOpenAgreement("terms")}
      >
        내용 보기
      </button>

      <label className="flex items-center gap-2 text-sm text-ink">
        <input
          type="checkbox"
          checked={agreedPrivacy}
          onChange={(event) => onPrivacyChange(event.target.checked)}
          className="mt-0.5"
        />
        <span>
          개인정보 수집·이용에 동의합니다. <span className="text-primary-strong">*</span>
        </span>
      </label>
      <button
        type="button"
        className="text-xs font-bold text-muted underline underline-offset-4 hover:text-primary-strong"
        onClick={() => setOpenAgreement("privacy")}
      >
        내용 보기
      </button>

      <Dialog open={openAgreement === "terms"} onClose={() => setOpenAgreement(null)} title="이용약관 및 참가 신청 유의사항">
        <ApplicationTermsContent />
      </Dialog>
      <Dialog open={openAgreement === "privacy"} onClose={() => setOpenAgreement(null)} title="개인정보 수집·이용 안내">
        <ApplicationPrivacyContent />
      </Dialog>
    </div>
  );
}