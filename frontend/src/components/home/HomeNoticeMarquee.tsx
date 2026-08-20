import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getNotices, type NoticeListItem } from "../../api/notice";

/** 띠에 흘릴 최대 개수. 너무 많으면 한 바퀴가 길어져 뒤쪽 문구는 사실상 안 보인다. */
const MAX_ITEMS = 5;

function NoticeRow({ items, hidden = false }: { items: NoticeListItem[]; hidden?: boolean }) {
  return (
    <div className="flex shrink-0 items-center" aria-hidden={hidden || undefined}>
      {items.map((item) => (
        <span key={`${item.category}-${item.id}`} className="flex items-center gap-10 pl-10 text-sm font-medium text-white">
          <span className="text-white/40">▶▶</span>
          <Link to={item.linkPath} tabIndex={hidden ? -1 : undefined} className="transition hover:underline">
            {item.title}
          </Link>
        </span>
      ))}
    </div>
  );
}

/**
 * 히어로 배너 아래 검정 띠. 상단 고정(📌)한 공지를 앞에 두고 최신 소식으로 채워 흘린다.
 *
 * <p>보여줄 소식이 하나도 없으면 띠 자체를 그리지 않는다 - 빈 검정 줄만 남으면 화면이
 * 고장 난 것처럼 보이기 때문이다.
 */
export function HomeNoticeMarquee() {
  const [items, setItems] = useState<NoticeListItem[] | null>(null);

  useEffect(() => {
    let ignore = false;
    getNotices()
      // 서버가 이미 "고정 먼저, 그다음 최신순"으로 내려주므로 앞에서부터 잘라 쓰면 된다.
      // 고정 공지만 흘리면 1~2건일 때 같은 문구가 화면에 두 번 겹쳐 보여서, 최신 소식으로 채운다.
      .then((data) => { if (!ignore) setItems(data.slice(0, MAX_ITEMS)); })
      .catch(() => { if (!ignore) setItems([]); });
    return () => { ignore = true; };
  }, []);

  if (items === null || items.length === 0) return null;

  return (
    <div className="overflow-hidden bg-ink py-3" aria-label="공지 안내">
      <div className="flex w-max animate-[marquee_40s_linear_infinite] hover:[animation-play-state:paused] motion-reduce:animate-none">
        <NoticeRow items={items} />
        <NoticeRow items={items} hidden />
      </div>
    </div>
  );
}
