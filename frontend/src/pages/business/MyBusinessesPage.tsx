import { Building2, ChevronRight, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { ApiError } from "../../api/client";
import { getMyBusinesses, type Business, type BusinessVerifyStatus } from "../../api/business";
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

export function MyBusinessesPage() {
  const { user } = useAuth();
  const [businesses, setBusinesses] = useState<Business[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) return;
    let ignore = false;

    getMyBusinesses(user.userId)
      .then((data) => { if (!ignore) setBusinesses(data); })
      .catch((error) => {
        if (ignore) return;
        setLoadError(error instanceof ApiError ? error.message : "사업자 목록을 불러오지 못했어요.");
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [user]);

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="참여 업체"
        title="내 사업자 목록"
        action={
          <Link to="/businesses/new" className="inline-flex min-h-11 items-center gap-2 rounded-button bg-primary-strong px-4 text-sm font-bold text-white hover:opacity-90">
            <Plus size={16} />
            사업자 등록
          </Link>
        }
      />

      {loading && <p className="text-sm text-muted">불러오는 중...</p>}

      {!loading && loadError && <EmptyState title="목록을 불러올 수 없어요" description={loadError} />}

      {!loading && !loadError && businesses.length === 0 && (
        <EmptyState title="등록된 사업자가 없어요" description="사업자를 등록하고 부스 참가 신청을 시작해 보세요." actionTo="/businesses/new" actionLabel="사업자 등록하러 가기" />
      )}

      {!loading && !loadError && businesses.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2">
          {businesses.map((business) => (
            <Link
              key={business.businessId}
              to={`/businesses/${business.businessId}`}
              className="surface flex items-center justify-between gap-3 p-5 transition hover:bg-page"
            >
              <div className="flex items-center gap-3">
                <div className="grid size-10 shrink-0 place-items-center rounded-full bg-primary-soft text-primary-strong">
                  <Building2 size={18} />
                </div>
                <div>
                  <p className="font-bold text-ink">{business.name}</p>
                  <p className="text-xs text-muted">{business.ceoName}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge tone={verifyStatusTones[business.verifyStatus]}>{verifyStatusLabels[business.verifyStatus]}</Badge>
                <ChevronRight size={16} className="text-muted" />
              </div>
            </Link>
          ))}
        </div>
      )}
    </PageContainer>
  );
}