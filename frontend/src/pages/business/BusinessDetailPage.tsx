import { ArrowLeft, CheckCircle2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getBusiness, type Business } from "../../api/business";
import { useAuth } from "../../contexts/AuthContext";

const approvalStatusLabels: Record<Business["approvalStatus"], string> = {
  PENDING_REVIEW: "심사 대기중",
  APPROVED: "승인됨",
  REJECTED: "반려됨",
  REVOKED: "취소됨",
};
const approvalStatusTones: Record<Business["approvalStatus"], "primary" | "sun" | "leaf" | "neutral"> = {
  PENDING_REVIEW: "sun",
  APPROVED: "leaf",
  REJECTED: "primary",
  REVOKED: "primary",
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
    if (!businessId || !user) {
      setBusiness(null);
      setLoadError(null);
      setLoading(false);
      return;
    }
    let ignore = false;

    setLoading(true);
    getBusiness(Number(businessId))
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
      <Link to="/vendor/businesses" className="mb-4 inline-flex items-center gap-1 text-sm font-bold text-muted hover:text-ink">
        <ArrowLeft size={16} />사업자 목록으로
      </Link>
      <PageHeader eyebrow="참여 업체" title={business.name} description="심사 상태와 등록 정보예요." />

      {justRegistered && (
        <div className="surface mb-6 flex items-center gap-3 border-leaf/40 bg-leaf-soft p-4 text-sm text-ink">
          <CheckCircle2 size={18} className="shrink-0" />
          <p>사업자 등록 신청이 접수됐어요. 관리자 심사 후 승인되면 부스 참가 신청을 진행할 수 있어요.</p>
        </div>
      )}

      <Card className="p-6">
        <div className="mb-4 flex items-center justify-between">
          <span className="text-sm font-bold text-muted">심사 상태</span>
          <Badge tone={approvalStatusTones[business.approvalStatus]}>{approvalStatusLabels[business.approvalStatus]}</Badge>
        </div>
        {(business.approvalStatus === "REJECTED" || business.approvalStatus === "REVOKED") && business.rejectReason && (
          <div className="surface mb-4 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            {business.rejectReason}
          </div>
        )}
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