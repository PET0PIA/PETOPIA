import { CheckCircle2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useLocation, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getBusiness, type Business, type BusinessVerifyStatus } from "../../api/business";
import { useAuth } from "../../contexts/AuthContext";

const verifyStatusLabels: Record<BusinessVerifyStatus, string> = {
  PENDING: "확인 대기",
  VERIFIED: "확인 완료",
  INVALID: "확인 실패",
  RETRY_NEEDED: "재확인 필요",
};
const verifyStatusTones: Record<BusinessVerifyStatus, "primary" | "sun" | "leaf" | "neutral"> = {
  PENDING: "sun",
  VERIFIED: "leaf",
  INVALID: "primary",
  RETRY_NEEDED: "primary",
};

function row(label: string, value: string) {
  return (
    <div className="flex gap-4 border-b border-line py-3 text-sm last:border-0">
      <span className="w-28 shrink-0 text-muted">{label}</span>
      <span className="font-bold text-ink">{value}</span>
    </div>
  );
}

export function BusinessDetailPage() {
  const { businessId } = useParams<{ businessId: string }>();
  const location = useLocation();
  const { user } = useAuth();
  const justRegistered = Boolean((location.state as { justRegistered?: boolean } | null)?.justRegistered);

  const [business, setBusiness] = useState<Business | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!businessId || !user) return;
    let ignore = false;

    getBusiness(Number(businessId), user.userId)
      .then((data) => { if (!ignore) setBusiness(data); })
      .catch((error) => {
        if (ignore) return;
        setLoadError(error instanceof ApiError ? error.message : "사업자 정보를 불러오지 못했어요.");
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [businessId, user]);

  if (loading) {
    return <PageContainer className="py-10"><p className="text-sm text-muted">불러오는 중...</p></PageContainer>;
  }

  if (loadError || !business) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="사업자 정보를 찾을 수 없어요" description={loadError ?? undefined} />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="참여 업체" title={business.name} description="국세청 진위확인 결과와 등록 정보예요." />

      {justRegistered && (
        <div className="surface mb-6 flex items-center gap-3 border-leaf/40 bg-leaf-soft p-4 text-sm text-ink">
          <CheckCircle2 size={18} className="shrink-0" />
          <p>사업자 등록이 완료됐어요. 이제 부스 참가 신청을 진행할 수 있어요.</p>
        </div>
      )}

      <Card className="p-6">
        <div className="mb-4 flex items-center justify-between">
          <span className="text-sm font-bold text-muted">진위확인 상태</span>
          <Badge tone={verifyStatusTones[business.verifyStatus]}>{verifyStatusLabels[business.verifyStatus]}</Badge>
        </div>
        {row("대표자명", business.ceoName)}
        {row("사업자등록번호", business.bizRegNo)}
        {row("개업일자", business.startDate)}
        {row("연락처", business.phone)}
        {row("사업장 주소", business.address)}
        {row("웹사이트", business.website ?? "-")}
      </Card>
    </PageContainer>
  );
}