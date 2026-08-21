import { useState } from "react";
import { Dialog } from "../ui/Dialog";
import { PRIVACY_TITLE, PrivacyContent, TERMS_TITLE, TermsContent } from "../legal/LegalDocuments";

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

export function SignupAgreements({ agreedTerms, agreedPrivacy, onTermsChange, onPrivacyChange }: SignupAgreementsProps) {
  const [openAgreement, setOpenAgreement] = useState<AgreementKind | null>(null);

  return (
    <>
      <div className="space-y-3 border-t border-line pt-5">
        <AgreementRow checked={agreedTerms} label="이용약관에 동의합니다." onChange={onTermsChange} onOpen={() => setOpenAgreement("terms")} />
        <AgreementRow checked={agreedPrivacy} label="개인정보 수집·이용에 동의합니다." onChange={onPrivacyChange} onOpen={() => setOpenAgreement("privacy")} />
      </div>

      {/* 본문은 legal/LegalDocuments의 원본을 그대로 쓰고, 팝업 높이 제한만 여기서 감싼다. */}
      <Dialog open={openAgreement === "terms"} onClose={() => setOpenAgreement(null)} title={TERMS_TITLE}>
        <div className="max-h-[60vh] overflow-y-auto pr-2">
          <TermsContent />
        </div>
      </Dialog>
      <Dialog open={openAgreement === "privacy"} onClose={() => setOpenAgreement(null)} title={PRIVACY_TITLE}>
        <div className="max-h-[60vh] overflow-y-auto pr-2">
          <PrivacyContent />
        </div>
      </Dialog>
    </>
  );
}
