import { useState } from "react";
import { Dialog } from "../ui/Dialog";

interface BusinessTermsAgreementProps {
  agreed: boolean;
  onAgreedChange: (checked: boolean) => void;
}

// 법무 검토 전 QA용 초안이다. 운영 배포 전 실제 정책에 맞춰 검토·확정해야 한다.
function BusinessTermsContent() {
  return (
    <div className="max-h-[60vh] space-y-5 overflow-y-auto pr-2 text-sm leading-6 text-muted">
      <section>
        <h3 className="font-extrabold text-ink">제1조 목적</h3>
        <p className="mt-1">
          이 약관은 PETOPIA(이하 "회사")가 운영하는 서비스에 참가업체(사업자)로 등록하려는 이용자(이하 "신청자")와
          회사 간의 사업자 등록·심사·자격 유지에 관한 권리, 의무 및 절차를 정함을 목적으로 합니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제2조 등록 신청 및 진위확인</h3>
        <p className="mt-1">
          신청자는 사업자등록번호, 대표자명, 개업일자 등 정확한 정보를 제출해야 하며, 회사는 국세청
          사업자등록정보 진위확인 API를 통해 이를 검증합니다. 제출한 정보가 국세청 데이터와 일치하지 않는 경우
          등록 신청 자체가 거부됩니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제3조 첨부서류 제출 의무</h3>
        <p className="mt-1">
          신청자는 사업자등록증 원본(또는 그 사본)을 첨부해야 하며, 위조·변조되었거나 타인의 서류를 도용한
          경우 등록이 반려되거나, 승인 이후 발각 시 제6조에 따라 등록이 취소될 수 있습니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제4조 관리자 심사 및 승인</h3>
        <p className="mt-1">
          진위확인을 통과한 신청 건은 즉시 승인되지 않으며, 회사 관리자의 서류 검토를 거쳐 승인됩니다. 승인이
          완료된 시점부터 참가업체(VENDOR) 권한이 부여되며, 행사 부스 참가 신청이 가능합니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제5조 반려 및 재신청</h3>
        <p className="mt-1">
          심사 결과 반려된 경우 회사는 반려 사유를 신청자에게 통지합니다. 신청자는 반려 사유를 보완한 뒤 동일한
          사업자등록번호로 다시 등록을 신청할 수 있습니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제6조 승인 후 취소 및 자격 박탈</h3>
        <p className="mt-1">승인 이후라도 다음 각 호에 해당하는 사실이 확인되는 경우, 회사는 사업자 등록을 취소할 수 있습니다.</p>
        <ul className="mt-1 list-disc space-y-1 pl-5">
          <li>제출 서류가 위조·변조되었거나 타인의 정보를 도용한 경우</li>
          <li>사업자등록 정보가 사실과 다른 것으로 확인된 경우</li>
          <li>그 밖에 회사의 서비스 운영을 저해하는 부정한 방법으로 등록한 경우</li>
        </ul>
        <p className="mt-2"><strong>등록이 취소되는 경우 다음과 같이 처리됩니다.</strong></p>
        <ul className="mt-1 list-disc space-y-1 pl-5">
          <li>
            해당 사업자로 진행 중인 참가 신청 중 <strong>아직 운영이 시작되지 않은 행사</strong> 건은 자동으로
            취소되며, 결제된 금액이 있는 경우 환불됩니다.
          </li>
          <li>
            <strong>이미 운영이 시작된 행사</strong>의 참가 신청 건은 자동으로 취소·환불되지 않으며, 회사가
            별도로 확인 후 처리합니다.
          </li>
          <li>신청자에게 승인된 다른 사업자가 없는 경우, 참가업체(VENDOR) 권한도 함께 회수됩니다.</li>
        </ul>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제7조 통지</h3>
        <p className="mt-1">
          사업자 등록의 승인, 반려, 취소 결과는 이메일 및 서비스 내 알림을 통해 신청자에게 통지됩니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제8조 등록 정보 변경 의무</h3>
        <p className="mt-1">
          신청자는 등록한 사업자 정보(사업장 주소, 연락처 등)에 변경이 발생한 경우 PETOPIA
          문의 채널을 통해 지체 없이 갱신을 요청해야 하며, 요청하지 않아 발생하는 불이익에
          대해 회사는 책임지지 않습니다.
        </p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제9조 책임의 한계</h3>
        <p className="mt-1">
          회사는 국세청 공개 정보 및 신청자가 제출한 서류를 근거로 사업자 등록을 심사합니다.
          신청자가 위조·변조된 서류를 제출하거나 허위 정보를 기재하여 발생한 손해에 대한
          책임은 해당 신청자에게 있으며, 회사는 관련 법령이 정하는 범위에서만 책임을 부담합니다.
        </p>
      </section>
      <p className="border-t border-line pt-4 text-xs">
        시행 예정일: 2026년 8월 20일 · 본 내용은 QA용 초안이며 운영 배포 전 검토가 필요합니다.
      </p>
    </div>
  );
}

export function BusinessTermsAgreement({ agreed, onAgreedChange }: BusinessTermsAgreementProps) {
  const [open, setOpen] = useState(false);

  return (
    <div className="space-y-3 border-t border-line pt-5">
      <div className="flex items-center gap-2">
        <input type="checkbox" checked={agreed} onChange={(event) => onAgreedChange(event.target.checked)} />
        <span className="text-sm text-ink">
          사업자 등록 이용약관에 동의합니다. <span className="text-primary-strong">*</span>
        </span>
        <button
          type="button"
          className="text-xs font-bold text-muted underline underline-offset-4 hover:text-primary-strong"
          onClick={() => setOpen(true)}
        >
          내용 보기
        </button>
      </div>

      <Dialog open={open} onClose={() => setOpen(false)} title="사업자 등록 이용약관">
        <BusinessTermsContent />
      </Dialog>
    </div>
  );
}