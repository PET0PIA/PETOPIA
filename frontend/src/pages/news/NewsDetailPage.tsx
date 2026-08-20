import { ArrowLeft, Download, Eye, Pin } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { EmptyState } from "../../components/common/EmptyState";
import { RichTextView } from "../../components/common/RichTextView";
import { Badge } from "../../components/ui/Badge";
import { ApiError } from "../../api/client";
import { NOTICE_CATEGORY_LABELS, getNotice, type NoticeDetail } from "../../api/notice";
import { formatFileSize } from "../../utils/fileSize";
import { formatShortDate } from "../../utils/date";

export function NewsDetailPage() {
  const { noticeId } = useParams<{ noticeId: string }>();
  const currentId = Number(noticeId);
  // /news/abc 처럼 숫자가 아닌 주소면 요청 자체를 보내지 않는다. 이 가드가 없으면 currentId가
  // NaN이 되고, 아래 실패 상태 비교(NaN === NaN은 false)가 영원히 안 맞아 로딩 화면에 갇힌다.
  const idValid = Number.isInteger(currentId) && currentId > 0;
  const [loaded, setLoaded] = useState<NoticeDetail | null>(null);
  // 어느 글의 오류인지까지 들고 있어야, 다른 글로 이동했을 때 남의 오류를 보여주지 않는다.
  const [failure, setFailure] = useState<{ noticeId: number; message: string } | null>(null);

  useEffect(() => {
    if (!idValid) return;
    let ignore = false;
    getNotice(currentId)
      // 성공하면 이전 실패 기록을 비운다. 남겨두면 같은 글로 되돌아왔을 때(상세 -> 다른 상세 -> 원래 글)
      // 잘 받아오고도 아래 loadError가 살아 있어 오류 화면이 먼저 걸린다.
      .then((data) => { if (!ignore) { setLoaded(data); setFailure(null); } })
      .catch((error) => {
        if (ignore) return;
        // 비공개 글도 서버가 404로 돌려준다(있는지 없는지 알려주지 않는다).
        setFailure({ noticeId: currentId, message: error instanceof ApiError ? error.message : "공지를 불러오지 못했어요." });
      });
    return () => { ignore = true; };
  }, [currentId, idValid]);

  // 주소의 id와 들고 있는 값이 다르면 아직 이 글을 못 받은 것이다. 별도 loading 상태를 두는 대신
  // 이렇게 계산하면, 다른 글로 이동했을 때 이전 글이 잠깐 보이는 문제도 함께 사라진다.
  const notice = loaded?.noticeId === currentId ? loaded : null;
  const loadError = failure?.noticeId === currentId ? failure.message : null;
  const loading = idValid && !notice && !loadError;

  if (loading) {
    return (
      <PageContainer className="py-10">
        <div className="surface grid min-h-60 place-items-center text-sm text-muted">불러오는 중이에요...</div>
      </PageContainer>
    );
  }

  if (!idValid || loadError || !notice) {
    return (
      <PageContainer className="py-10">
        <EmptyState
          title="공지를 찾을 수 없어요"
          description={loadError ?? undefined}
          actionTo="/news"
          actionLabel="소식 목록으로"
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <Link to="/news" className="mb-6 inline-flex items-center gap-1.5 text-sm font-bold text-muted transition hover:text-ink">
        <ArrowLeft size={16} />
        소식·이벤트
      </Link>

      <article>
        <header className="border-b border-line pb-5">
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <Badge tone="ink">{NOTICE_CATEGORY_LABELS[notice.category]}</Badge>
            {notice.pinned && (
              <Badge tone="neutral" className="gap-1"><Pin size={11} />상단 고정</Badge>
            )}
            {/* 아직 전체공개 안 된 행사는 상세가 404라서 링크를 걸지 않고 이름만 보여준다.
                판정(fairPublic)은 서버가 공개 상세와 같은 기준으로 내려준다. */}
            {notice.fairId && notice.fairName && (
              notice.fairPublic ? (
                <Link to={`/fairs/${notice.fairId}`} className="rounded-full bg-surface-alt px-2.5 py-1 text-xs font-bold text-ink transition hover:bg-line">
                  {notice.fairName}
                </Link>
              ) : (
                <span className="rounded-full bg-surface-alt px-2.5 py-1 text-xs font-bold text-ink">{notice.fairName}</span>
              )
            )}
          </div>
          <h1 className="text-2xl font-extrabold leading-snug tracking-tight text-ink sm:text-3xl">{notice.title}</h1>
          <p className="mt-3 flex items-center gap-3 text-sm text-muted">
            <span>{formatShortDate(notice.createdAt)}</span>
            <span className="inline-flex items-center gap-1"><Eye size={14} />{notice.viewCount}</span>
          </p>
        </header>

        {/* 본문은 관리자가 에디터로 쓴 HTML이라 RichTextView(정화)를 반드시 거친다. */}
        <RichTextView html={notice.content} className="py-8 text-sm leading-7 text-ink sm:text-base" />

        {notice.attachments.length > 0 && (
          <section className="border-t border-line pt-6">
            <h2 className="mb-3 text-sm font-bold text-ink">첨부파일 {notice.attachments.length}개</h2>
            <ul className="space-y-2">
              {notice.attachments.map((attachment) => (
                <li key={attachment.attachmentId}>
                  <a
                    href={attachment.fileUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-2 rounded-button bg-surface-alt px-3 py-2.5 text-sm transition hover:bg-line"
                  >
                    <Download size={16} className="shrink-0 text-muted" />
                    <span className="min-w-0 flex-1 truncate font-bold text-ink">{attachment.originalName}</span>
                    <span className="shrink-0 text-xs text-muted">{formatFileSize(attachment.fileSize)}</span>
                  </a>
                </li>
              ))}
            </ul>
          </section>
        )}
      </article>
    </PageContainer>
  );
}
