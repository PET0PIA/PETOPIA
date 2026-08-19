import { useState } from "react";
import { Dialog } from "../ui/Dialog";

type AgreementKind = "terms" | "privacy";

interface SignupAgreementsProps {
  agreedTerms: boolean;
  agreedPrivacy: boolean;
  onTermsChange: (checked: boolean) => void;
  onPrivacyChange: (checked: boolean) => void;
}

interface AgreementRowProps {
  checked: boolean;
  label: string;
  onChange: (checked: boolean) => void;
  onOpen: () => void;
}

function AgreementRow({ checked, label, onChange, onOpen }: AgreementRowProps) {
  return (
    <div className="flex items-center justify-between gap-4">
      <label className="flex min-w-0 items-center gap-2 text-sm text-ink">
        <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
        <span>{label} <span className="text-primary-strong">*</span></span>
      </label>
      <button
        type="button"
        className="shrink-0 text-xs font-bold text-muted underline underline-offset-4 hover:text-primary-strong"
        onClick={onOpen}
      >
        내용 보기
      </button>
    </div>
  );
}

// 법무 검토 전 QA용 초안이다. 운영 배포 전 실제 사업자 정보와 정책에 맞춰 검토·확정해야 한다.
function TermsContent() {
  return (
    <div className="max-h-[60vh] space-y-5 overflow-y-auto pr-2 text-sm leading-6 text-muted">
      <section>
        <h3 className="font-extrabold text-ink">제1조 목적</h3>
        <p className="mt-1">본 약관은 PETOPIA가 제공하는 펫페어 정보, 예약, 참가 신청 및 관련 서비스의 이용 조건과 회원과 서비스 간의 권리·의무를 정하는 것을 목적으로 합니다.</p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제2조 회원가입과 계정 관리</h3>
        <ul className="mt-1 list-disc space-y-1 pl-5">
          <li>회원은 정확한 정보를 제공하고 변경된 정보는 최신 상태로 유지해야 합니다.</li>
          <li>계정과 인증정보는 회원 본인만 사용할 수 있으며 타인에게 양도하거나 공유할 수 없습니다.</li>
          <li>타인의 정보를 도용하거나 서비스 운영을 방해한 경우 이용이 제한될 수 있습니다.</li>
        </ul>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제3조 서비스 이용</h3>
        <p className="mt-1">회원은 박람회 조회·예약, 참가 신청, 반려동물 정보 관리 등 제공되는 기능을 서비스 안내와 관련 법령에 따라 이용해야 합니다. 행사 일정, 참가 조건 및 현장 운영 방식은 행사별 안내에 따라 달라질 수 있습니다.</p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제4조 예약·결제·취소</h3>
        <p className="mt-1">유료 서비스의 금액, 결제 방법, 취소 및 환불 조건은 결제 전 화면과 행사별 정책에 표시합니다. 회원은 결제 전에 해당 내용을 확인해야 합니다.</p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제5조 서비스 변경 및 중단</h3>
        <p className="mt-1">점검, 장애, 천재지변 또는 운영상 필요한 사유가 있으면 서비스의 전부 또는 일부가 변경·중단될 수 있습니다. 가능한 경우 서비스 화면을 통해 사전에 안내합니다.</p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제6조 탈퇴 및 이용 제한</h3>
        <p className="mt-1">회원은 마이페이지에서 탈퇴를 요청할 수 있습니다. 진행 중인 예약, 결제, 환불 또는 정산이 있으면 처리가 끝날 때까지 탈퇴가 제한될 수 있습니다.</p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">제7조 책임</h3>
        <p className="mt-1">서비스는 고의 또는 과실로 회원에게 발생한 손해에 대해 관련 법령이 정하는 범위에서 책임을 부담합니다. 회원의 귀책사유나 불가항력으로 발생한 손해는 책임 범위에서 제외될 수 있습니다.</p>
      </section>
      <p className="border-t border-line pt-4 text-xs">시행 예정일: 2026년 8월 20일 · 본 내용은 QA용 초안이며 운영 배포 전 검토가 필요합니다.</p>
    </div>
  );
}

function PrivacyContent() {
  return (
    <div className="max-h-[60vh] space-y-5 overflow-y-auto pr-2 text-sm leading-6 text-muted">
      <section>
        <h3 className="font-extrabold text-ink">1. 수집하는 개인정보</h3>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full min-w-[420px] border-collapse text-left text-xs">
            <thead><tr className="border-y border-line bg-page text-ink"><th className="px-3 py-2">구분</th><th className="px-3 py-2">수집 항목</th></tr></thead>
            <tbody>
              <tr className="border-b border-line"><td className="px-3 py-2 font-bold text-ink">이메일 가입</td><td className="px-3 py-2">이메일, 비밀번호, 닉네임, 생년월일, 휴대폰 번호, 성별, 주소</td></tr>
              <tr className="border-b border-line"><td className="px-3 py-2 font-bold text-ink">소셜 가입</td><td className="px-3 py-2">이메일, 소셜 제공자와 식별값, 닉네임, 생년월일, 휴대폰 번호, 성별, 주소</td></tr>
              <tr className="border-b border-line"><td className="px-3 py-2 font-bold text-ink">서비스 이용</td><td className="px-3 py-2">접속 기록, 서비스 이용 기록, 예약·결제·환불 기록</td></tr>
              <tr><td className="px-3 py-2 font-bold text-ink">선택 등록</td><td className="px-3 py-2">반려동물 이름, 종류, 품종, 생년월일, 성별, 중성화 여부, 이미지</td></tr>
            </tbody>
          </table>
        </div>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">2. 수집·이용 목적</h3>
        <ul className="mt-1 list-disc space-y-1 pl-5">
          <li>회원 식별, 가입 의사 확인 및 계정 관리</li>
          <li>박람회 예약·참가 신청·결제·환불 등 서비스 제공</li>
          <li>공지 전달, 문의 대응 및 부정 이용 방지</li>
          <li>반려동물 정보 관리와 예약 과정에서의 정보 활용</li>
        </ul>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">3. 보유 및 이용 기간</h3>
        <p className="mt-1">회원정보는 원칙적으로 회원 탈퇴 시까지 보유하며, 목적이 달성되면 지체 없이 파기합니다. 다만 관계 법령에 따라 보존할 필요가 있는 거래·분쟁 처리 기록은 해당 법령에서 정한 기간 동안 별도로 보관할 수 있습니다.</p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">4. 동의 거부 권리</h3>
        <p className="mt-1">개인정보 수집·이용 동의를 거부할 수 있습니다. 다만 필수 정보 수집에 동의하지 않으면 회원가입과 회원 대상 서비스를 이용할 수 없습니다. 반려동물 정보는 선택적으로 등록할 수 있습니다.</p>
      </section>
      <section>
        <h3 className="font-extrabold text-ink">5. 개인정보 보호 문의</h3>
        <p className="mt-1">개인정보 열람·정정·삭제 및 처리 관련 문의는 PETOPIA 문의 채널을 통해 요청할 수 있습니다.</p>
      </section>
      <p className="border-t border-line pt-4 text-xs">시행 예정일: 2026년 8월 20일 · 본 내용은 QA용 초안이며 실제 운영 주체와 법무 검토 후 확정해야 합니다.</p>
    </div>
  );
}

export function SignupAgreements({ agreedTerms, agreedPrivacy, onTermsChange, onPrivacyChange }: SignupAgreementsProps) {
  const [openAgreement, setOpenAgreement] = useState<AgreementKind | null>(null);

  return (
    <>
      <div className="space-y-3 border-t border-line pt-5">
        <AgreementRow checked={agreedTerms} label="이용약관에 동의합니다." onChange={onTermsChange} onOpen={() => setOpenAgreement("terms")} />
        <AgreementRow checked={agreedPrivacy} label="개인정보 수집·이용에 동의합니다." onChange={onPrivacyChange} onOpen={() => setOpenAgreement("privacy")} />
      </div>

      <Dialog open={openAgreement === "terms"} onClose={() => setOpenAgreement(null)} title="PETOPIA 이용약관">
        <TermsContent />
      </Dialog>
      <Dialog open={openAgreement === "privacy"} onClose={() => setOpenAgreement(null)} title="개인정보 수집·이용 안내">
        <PrivacyContent />
      </Dialog>
    </>
  );
}
