import { PawPrint } from "lucide-react";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";

interface SignupWelcomeCardProps {
  onRegisterPet: () => void;
  onLater: () => void;
  registerLabel?: string;
  laterLabel?: string;
}

export function SignupWelcomeCard({
  onRegisterPet,
  onLater,
  registerLabel = "반려동물 등록하기",
  laterLabel = "나중에 하기",
}: SignupWelcomeCardProps) {
  return (
    <Card className="mx-auto max-w-md p-8 text-center">
      <div className="mx-auto mb-4 grid size-14 place-items-center rounded-full bg-leaf-soft text-ink">
        <PawPrint size={26} aria-hidden="true" />
      </div>
      <h1 className="text-xl font-extrabold">PETOPIA 가입을 환영해요!</h1>
      <p className="mt-3 text-sm leading-6 text-muted">
        반려동물 정보를 등록하면 마이페이지에서 편리하게 관리하고,
        <br className="hidden sm:block" /> 박람회 예약 시 반려동물 정보를 활용할 수 있어요.
      </p>
      <p className="mt-3 text-sm font-bold text-ink">지금 등록하시겠어요?</p>
      <div className="mt-6 space-y-2">
        <Button className="w-full" onClick={onRegisterPet}>{registerLabel}</Button>
        <Button className="w-full" variant="outline" onClick={onLater}>{laterLabel}</Button>
      </div>
    </Card>
  );
}
