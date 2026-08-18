import { Construction } from "lucide-react";
import { PageContainer } from "../components/common/PageContainer";

interface NotImplementedPageProps {
  /** 화면 한가운데 "미구현" 아래에 작게 보여줄 이름(선택). */
  title?: string;
  /** 관리자 콘솔 안에서 쓸 때는 콘솔이 이미 바깥 여백을 갖고 있어 컨테이너를 줄인다. */
  admin?: boolean;
}

/**
 * 아직 만들지 않은 화면의 자리표시. 화면 한가운데 "미구현"만 크게 띄운다.
 * 라우팅·메뉴는 미리 연결해 두되, 내용이 비어 있음을 분명히 보여주는 용도다.
 */
export function NotImplementedPage({ title, admin = false }: NotImplementedPageProps) {
  const body = (
    <div className="surface grid min-h-[60vh] place-items-center p-8 text-center">
      <div>
        <Construction className="mx-auto mb-4 text-muted" size={40} strokeWidth={1.5} />
        <p className="text-6xl font-black tracking-tight text-ink sm:text-7xl">미구현</p>
        {title && <p className="mt-4 text-sm font-bold text-muted">{title}</p>}
      </div>
    </div>
  );
  return admin ? <div className="mx-auto max-w-6xl py-2">{body}</div> : <PageContainer className="py-10">{body}</PageContainer>;
}
