import { CalendarDays, IdCard, Ticket } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Table } from "../../components/ui/Table";
import { ApiError } from "../../api/client";
import { getMyPets, type Pet } from "../../api/pet";
import { getMe, type UserMe } from "../../api/user";

/** 마이페이지 - 내 정보 요약, 반려동물 목록, 다른 도메인 바로가기를 모아둔 허브.
 * "내 예약 목록"/"내 행사 신청 목록"은 이미 각자 페이지가 있어 여기서 데이터를 새로
 * 가져오지 않고 링크만 연결한다 - 중복 구현을 피한다. */
export function MyPage() {
  const [me, setMe] = useState<UserMe | null>(null);
  const [pets, setPets] = useState<Pet[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    Promise.all([getMe(), getMyPets()])
      .then(([meResult, petsResult]) => {
        if (!alive) return;
        setMe(meResult);
        setPets(petsResult);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof ApiError ? err.message : "마이페이지를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  if (loading) {
    return (
      <PageContainer className="py-10">
        <p className="py-16 text-center text-sm text-muted">불러오는 중이에요…</p>
      </PageContainer>
    );
  }

  if (error || !me) {
    return (
      <PageContainer className="py-10">
        <EmptyState title="마이페이지를 불러오지 못했어요." description={error ?? undefined} />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="마이페이지" title={`${me.nickname}님, 반가워요!`} description={me.email} />

      <Card className="mb-8 p-8">
        <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
          <dl className="flex flex-wrap gap-8 text-sm">
            <div>
              <dt className="font-bold text-muted">닉네임</dt>
              <dd className="mt-0.5 text-ink">{me.nickname}</dd>
            </div>
            <div>
              <dt className="font-bold text-muted">생년월일</dt>
              <dd className="mt-0.5 text-ink">{me.birthDate}</dd>
            </div>
            <div>
              <dt className="font-bold text-muted">휴대폰 번호</dt>
              <dd className="mt-0.5 text-ink">{me.phone}</dd>
            </div>
            <div>
              <dt className="font-bold text-muted">주소</dt>
              <dd className="mt-0.5 text-ink">{me.address}</dd>
            </div>
          </dl>
          <div className="flex shrink-0 gap-2">
            <Link to="/mypage/password">
              <Button variant="outline">비밀번호 변경</Button>
            </Link>
            <Link to="/mypage/edit">
              <Button variant="outline">내 정보 수정</Button>
            </Link>
          </div>
        </div>
      </Card>

      <section className="mb-8">
        <SectionHeader title="내 반려동물" description="등록한 반려동물을 관리해요." />
        {pets.length === 0 ? (
          <EmptyState
            title="아직 등록한 반려동물이 없어요."
            description="반려동물을 등록하면 이곳에서 확인할 수 있어요."
            actionTo="/mypage/pets/new"
            actionLabel="반려동물 등록하기"
          />
        ) : (
          <>
            <Table>
              <thead>
                <tr className="border-b border-line text-xs font-bold text-muted">
                  <th className="px-8 py-4">이름</th>
                  <th className="px-8 py-4">종</th>
                  <th className="px-8 py-4">생년월일</th>
                  <th className="px-8 py-4" aria-label="동물등록증" />
                </tr>
              </thead>
              <tbody>
                {pets.map((pet) => (
                  <tr key={pet.petId} className="border-b border-line last:border-0 hover:bg-page">
                    <td className="px-8 py-4">
                      <Link to={`/mypage/pets/${pet.petId}`} className="font-bold text-ink hover:underline">
                        {pet.name}
                      </Link>
                    </td>
                    <td className="px-8 py-4 text-muted">{pet.species}</td>
                    <td className="px-8 py-4 text-muted">{pet.birthDate ?? "미입력"}</td>
                    <td className="px-8 py-4 text-right">
                      <Link
                        to={`/mypage/pets/${pet.petId}`}
                        aria-label={`${pet.name} 동물등록증 보기`}
                        className="inline-flex items-center gap-1.5 rounded-button border border-line px-3 py-1.5 text-xs font-bold text-ink hover:bg-page"
                      >
                        <IdCard size={14} aria-hidden="true" />
                        동물등록증
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <div className="mt-4 text-right">
              <Link to="/mypage/pets/new">
                <Button variant="outline">반려동물 등록</Button>
              </Link>
            </div>
          </>
        )}
      </section>

      <section>
        <SectionHeader title="바로가기" />
        <div className="grid gap-3 sm:grid-cols-2">
          <Link to="/reservations/me" className="surface flex items-center gap-3 p-5 hover:bg-page">
            <Ticket size={20} className="shrink-0 text-primary-strong" />
            <div>
              <p className="font-bold text-ink">내 예약 목록</p>
              <p className="text-sm text-muted">예매한 행사와 입장 QR을 확인해요.</p>
            </div>
          </Link>
          <Link to="/fair-applications/me" className="surface flex items-center gap-3 p-5 hover:bg-page">
            <CalendarDays size={20} className="shrink-0 text-primary-strong" />
            <div>
              <p className="font-bold text-ink">내 행사 신청 목록</p>
              <p className="text-sm text-muted">신청한 행사의 심사 현황을 확인해요.</p>
            </div>
          </Link>
        </div>
      </section>
    </PageContainer>
  );
}
