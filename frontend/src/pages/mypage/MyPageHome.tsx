import { Building2, ChevronRight, FileText, Store, Ticket } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Badge } from "../../components/ui/Badge";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import { getMyBusinesses, type Business } from "../../api/business";
import { getMyFavoriteBooths } from "../../api/booth";
import { getMyApplications } from "../../api/fair";
import { getMyNotifications } from "../../api/notification";
import { getMyReservations } from "../../api/reservation";
import {
  formatEntryTime,
  formatVisitDateDow,
  reservationStatusLabels,
  reservationStatusTones,
} from "../reservation/reservationDisplay";

/** 마이페이지 첫 화면 - 내 예약·신청·즐겨찾기·알림을 "미리보기 + 전체 보기"로만 모아 보여준다.
 * 목록 로직은 각 화면이 그대로 갖고 있고, 여기서는 앞 몇 건만 읽어 온다(중복 화면을 만들지 않는다). */

// useCardData의 의존성으로 들어가므로 모듈 최상단에 둬서 참조가 매 렌더 바뀌지 않게 한다.
const loadReservations = () => getMyReservations(0, 5);
const loadNotifications = () => getMyNotifications(0, 5);
const loadFavorites = () => getMyFavoriteBooths();
const loadApplications = () => getMyApplications();
const loadBusinesses = () => getMyBusinesses();

const RESERVATION_PREVIEW = 2;
const LIST_PREVIEW = 3;

/** 카드 한 장이 쓸 조회 상태. 카드마다 따로 불러와서, 하나가 실패해도 나머지 카드는 그대로 뜬다. */
function useCardData<T>(load: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    load()
      .then((result) => { if (alive) setData(result); })
      .catch((err: unknown) => {
        if (alive) setError(err instanceof ApiError ? err.message : "불러오지 못했어요.");
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [load]);

  return { data, loading, error };
}

/** 알림 페이지와 같은 형식이되(2026-08-20 14:30), 카드가 좁아 연도만 뺀다. */
function formatNotifiedAt(value: string) {
  return value.replace("T", " ").slice(5, 16);
}

function CardSection({ title, to, children }: { title: string; to?: string; children: ReactNode }) {
  return (
    <Card className="p-6">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-base font-extrabold tracking-tight text-ink">{title}</h2>
        {to && (
          <Link to={to} className="inline-flex shrink-0 items-center rounded-button px-2 py-1 text-sm font-bold text-muted transition hover:bg-surface-alt hover:text-ink">
            전체 보기
            <ChevronRight size={15} aria-hidden="true" />
          </Link>
        )}
      </div>
      {children}
    </Card>
  );
}

/** 카드 안의 한 줄 안내(불러오는 중·실패·비어 있음). */
function CardNote({ children }: { children: ReactNode }) {
  return <p className="py-7 text-center text-sm text-muted">{children}</p>;
}

function ReservationCard() {
  const { data, loading, error } = useCardData(loadReservations);
  // 만료 예약은 서버가 목록에서 빼주므로 받은 순서대로 앞에서 두 건만 쓴다.
  const items = (data?.items ?? []).slice(0, RESERVATION_PREVIEW);

  return (
    <CardSection title="예약 내역" to="/mypage/reservations">
      {loading ? (
        <CardNote>불러오는 중이에요…</CardNote>
      ) : error ? (
        <CardNote>{error}</CardNote>
      ) : items.length === 0 ? (
        <CardNote>
          아직 예약한 행사가 없어요.{" "}
          <Link to="/fairs/upcoming" className="rounded-button font-bold text-ink underline transition hover:bg-surface-alt">티켓 예매하러 가기</Link>
        </CardNote>
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((item) => (
            <li key={item.reservationId}>
              <Link
                to={`/reservations/me/${item.reservationId}`}
                aria-label={`${item.fairName} 예약 상세 보기`}
                className="flex items-center gap-4 rounded-card border border-line p-3 transition-colors hover:border-muted hover:bg-surface-alt"
              >
                {item.fairPosterImageUrl ? (
                  <img src={item.fairPosterImageUrl} alt="" className="h-20 w-15 shrink-0 rounded-card object-cover" />
                ) : (
                  <div className="grid h-20 w-15 shrink-0 place-items-center rounded-card bg-surface-alt text-muted">
                    <Ticket size={20} aria-hidden="true" />
                  </div>
                )}
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <p className="truncate font-bold text-ink">{item.fairName}</p>
                  <p className="text-sm text-muted">
                    {formatVisitDateDow(item.visitDate)} · {formatEntryTime(item.entryStartTime)}~{formatEntryTime(item.entryEndTime)}
                  </p>
                  <Badge tone={reservationStatusTones[item.reservationStatus]} className="self-start">
                    {reservationStatusLabels[item.reservationStatus]}
                  </Badge>
                </div>
                <ChevronRight size={18} className="shrink-0 text-muted" aria-hidden="true" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </CardSection>
  );
}

function ApplicationTile({
  icon: Icon,
  label,
  value,
  to,
}: { icon: typeof FileText; label: string; value: ReactNode; to: string }) {
  return (
    <Link
      to={to}
      className="flex items-center gap-3 rounded-card border border-line p-4 transition-colors hover:border-muted hover:bg-surface-alt"
    >
      <span className="grid size-9 shrink-0 place-items-center rounded-full bg-surface-alt text-ink">
        <Icon size={17} aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm text-muted">{label}</span>
        <span className="mt-0.5 block font-bold text-ink">{value}</span>
      </span>
      <ChevronRight size={17} className="shrink-0 text-muted" aria-hidden="true" />
    </Link>
  );
}

const businessStatusLabels: Record<Business["approvalStatus"], string> = {
  PENDING_REVIEW: "심사 대기중",
  APPROVED: "승인됨",
  REJECTED: "반려됨",
  REVOKED: "취소됨",
};

function ApplicationCard() {
  const applications = useCardData(loadApplications);
  const businesses = useCardData(loadBusinesses);

  // 사업자는 신청한 적이 있을 때만 칸을 만든다(사이드바 메뉴와 같은 규칙).
  // 부스 참가 신청은 사업자 콘솔이 갖고 있어 여기서 세지 않는다.
  const latestBusiness = [...(businesses.data ?? [])]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];

  return (
    <CardSection title="신청 내역">
      <div className="grid gap-3 sm:grid-cols-2">
        <ApplicationTile
          icon={FileText}
          label="행사 신청 내역"
          value={
            applications.loading ? "확인 중…"
              : applications.error ? "확인 실패"
              : `${applications.data?.length ?? 0}건`
          }
          to="/mypage/fair-applications"
        />
        {latestBusiness && (
          <ApplicationTile
            icon={Building2}
            label="사업자 등록 신청"
            value={businessStatusLabels[latestBusiness.approvalStatus]}
            to="/mypage/businesses"
          />
        )}
      </div>
    </CardSection>
  );
}

function FavoriteCard() {
  const { data, loading, error } = useCardData(loadFavorites);
  const items = (data ?? []).slice(0, LIST_PREVIEW);

  return (
    <CardSection title="즐겨찾기 부스" to="/mypage/favorites">
      {loading ? (
        <CardNote>불러오는 중이에요…</CardNote>
      ) : error ? (
        <CardNote>{error}</CardNote>
      ) : items.length === 0 ? (
        <CardNote>즐겨찾기한 부스가 없어요.</CardNote>
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((booth) => (
            <li key={booth.boothId}>
              <Link to={`/booths/${booth.boothId}`} className="flex items-center gap-3 rounded-card p-2 transition-colors hover:bg-surface-alt hover:text-primary-strong">
                {booth.imageUrl ? (
                  <img src={booth.imageUrl} alt="" className="size-10 shrink-0 rounded-full object-cover" />
                ) : (
                  <span className="grid size-10 shrink-0 place-items-center rounded-full bg-surface-alt text-muted">
                    <Store size={16} aria-hidden="true" />
                  </span>
                )}
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-bold text-ink">{booth.name}</span>
                  <span className="block truncate text-sm text-muted">{booth.fairName}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </CardSection>
  );
}

function NotificationCard() {
  const { data, loading, error } = useCardData(loadNotifications);
  const items = (data?.items ?? []).slice(0, LIST_PREVIEW);

  return (
    <CardSection title="최근 알림" to="/notifications">
      {loading ? (
        <CardNote>불러오는 중이에요…</CardNote>
      ) : error ? (
        <CardNote>{error}</CardNote>
      ) : items.length === 0 ? (
        <CardNote>받은 알림이 없어요.</CardNote>
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((item) => (
            <li key={item.notificationId} className="flex items-start justify-between gap-3">
              <span className="min-w-0 flex-1">
                <span className={`block truncate text-sm ${item.isRead ? "text-muted" : "font-bold text-ink"}`}>
                  {item.title}
                </span>
                <span className="mt-0.5 block truncate text-sm text-muted">{item.body}</span>
              </span>
              <span className="shrink-0 text-xs text-muted">{formatNotifiedAt(item.createdAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </CardSection>
  );
}

export function MyPageHome() {
  return (
    <div>
      {/* 화면 제목은 프로필 카드(레이아웃)가 대신하므로, 제목 계층만 읽는 사람을 위해 숨겨서 둔다. */}
      <h1 className="sr-only">마이페이지</h1>
      <div className="grid items-start gap-6 xl:grid-cols-[1.6fr_1fr]">
        <div className="flex flex-col gap-6">
          <ReservationCard />
          <ApplicationCard />
        </div>
        <div className="flex flex-col gap-6">
          <FavoriteCard />
          <NotificationCard />
        </div>
      </div>
    </div>
  );
}
