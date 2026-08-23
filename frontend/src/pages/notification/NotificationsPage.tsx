import { Bell, CheckCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { ApiError } from "../../api/client";
import {
  getMyNotifications,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  type NotificationListItem,
} from "../../api/notification";

function formatCreatedAt(value: string) {
  return value.replace("T", " ").slice(0, 16);
}

export function NotificationsPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<NotificationListItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const response = await getMyNotifications(0);
      setItems(response.items);
      setPage(response.page);
      setHasNext(response.hasNext);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "알림을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }

  // "더 보기" - 다음 페이지를 이어붙인다(기존 목록 교체 아님).
  async function loadMore() {
    setLoadingMore(true);
    try {
      const response = await getMyNotifications(page + 1);
      setItems((previous) => [...previous, ...response.items]);
      setPage(response.page);
      setHasNext(response.hasNext);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "이전 알림을 더 불러오지 못했어요.");
    } finally {
      setLoadingMore(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleItemClick(item: NotificationListItem) {
    if (!item.isRead) {
      try {
        await markNotificationAsRead(item.notificationId);
        setItems((previous) =>
          previous.map((row) => (row.notificationId === item.notificationId ? { ...row, isRead: true } : row)),
        );
      } catch {
        // 읽음 처리 실패는 화면 진입을 막지 않는다.
      }
    }
    if (item.linkUrl) navigate(item.linkUrl);
  }

  async function handleMarkAllAsRead() {
    try {
      await markAllNotificationsAsRead();
      setItems((previous) => previous.map((row) => ({ ...row, isRead: true })));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "모두 읽음 처리에 실패했어요.");
    }
  }

  const hasUnread = items.some((item) => !item.isRead);

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="PETOPIA"
        title="알림"
        description="새로운 소식과 처리 결과를 확인해요."
        action={
          <Button variant="outline" onClick={handleMarkAllAsRead} disabled={!hasUnread}>
            <CheckCheck size={16} />
            모두 읽음 처리
          </Button>
        }
      />

      {error && (
        <div className="surface mb-6 flex items-center justify-between border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          {error}
          <button type="button" className="ml-4 shrink-0 underline" onClick={load}>다시 시도</button>
        </div>
      )}

      {loading ? (
        <div className="surface grid min-h-72 place-items-center p-8 text-sm text-muted">불러오는 중이에요...</div>
      ) : !error && items.length === 0 ? (
        <EmptyState title="아직 알림이 없어요." description="새 소식이 도착하면 이곳에서 확인할 수 있어요." />
      ) : (
        <ul className="space-y-3">
          {items.map((item) => (
            <li key={item.notificationId}>
              <Card
                className={`flex w-full cursor-pointer items-start gap-4 p-5 text-left transition hover:bg-page ${
                  item.isRead ? "" : "border-primary-strong/40 bg-primary-soft/40"
                }`}
                role="button"
                tabIndex={0}
                onClick={() => handleItemClick(item)}
                onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); handleItemClick(item); } }}
              >
                <div className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-full bg-sun-soft text-ink">
                  <Bell size={16} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="truncate font-bold text-ink">{item.title}</p>
                    {!item.isRead && <span className="size-2 shrink-0 rounded-full bg-primary" />}
                  </div>
                  <p className="mt-1 text-sm leading-6 text-muted">{item.body}</p>
                  <p className="mt-2 text-xs text-muted">{formatCreatedAt(item.createdAt)}</p>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}

      {!loading && hasNext && (
        <div className="mt-6 flex justify-center">
          <Button variant="outline" onClick={loadMore} disabled={loadingMore}>
            {loadingMore ? "불러오는 중..." : "이전 알림 더 보기"}
          </Button>
        </div>
      )}
    </PageContainer>
  );
}
