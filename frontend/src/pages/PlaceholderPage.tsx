import { ArrowLeft, Construction } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { EmptyState } from "../components/common/EmptyState";
import { PageContainer } from "../components/common/PageContainer";
import { PageHeader } from "../components/common/PageHeader";
import { Button } from "../components/ui/Button";

interface PlaceholderPageProps { title: string; admin?: boolean; }
export function PlaceholderPage({ title, admin = false }: PlaceholderPageProps) { const navigate = useNavigate(); const content = <><PageHeader eyebrow={admin ? "관리자 공통 화면" : "PETOPIA"} title={title} description="이 메뉴는 공통 화면 뼈대에 연결되어 있습니다." action={<Button variant="outline" onClick={() => navigate(-1)}><ArrowLeft size={16} />이전 페이지</Button>} /><EmptyState title="기능 개발 예정입니다." description="현재는 화면 연결과 공통 UI 검증을 위한 페이지입니다. 곧 더 유용한 기능으로 찾아올게요." /></>; return admin ? <div className="mx-auto max-w-6xl py-2">{content}</div> : <PageContainer className="py-10">{content}</PageContainer>; }

export function NotFoundPage() { return <PageContainer className="py-16"><div className="surface grid min-h-80 place-items-center p-8 text-center"><div><Construction className="mx-auto mb-4 text-primary" size={38} /><h1 className="text-2xl font-black">페이지를 찾을 수 없어요.</h1><p className="mt-2 text-sm text-muted">주소를 다시 확인하거나 PETOPIA 홈으로 돌아가 보세요.</p><Button className="mt-6" onClick={() => window.location.assign("/")}>홈으로 이동</Button></div></div></PageContainer>; }
