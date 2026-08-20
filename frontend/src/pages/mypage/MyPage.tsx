import { CalendarDays, Heart, IdCard, PawPrint, Star, Ticket, User } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Table } from "../../components/ui/Table";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import { getMyPets, type Pet } from "../../api/pet";
import { getMe, withdraw, type UserMe } from "../../api/user";
import { useAuth } from "../../contexts/AuthContext";

/** 마이페이지 - 내 정보 요약, 반려동물 목록, 다른 도메인 바로가기를 모아둔 허브.
 * "내 예약 목록"/"내 행사 신청 목록"은 이미 각자 페이지가 있어 여기서 데이터를 새로
 * 가져오지 않고 링크만 연결한다 - 중복 구현을 피한다. */
export function MyPage() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const { confirm, confirmDialog } = useConfirm();
  const [me, setMe] = useState<UserMe | null>(null);
  const [pets, setPets] = useState<Pet[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [petsLoading, setPetsLoading] = useState(true);
  const [petsError, setPetsError] = useState<string | null>(null);
  const [withdrawing, setWithdrawing] = useState(false);
  const [withdrawError, setWithdrawError] = useState<string | null>(null);
  const aliveRef = useRef(true);

  // 반려동물 목록만 따로 불러온다. loading/error를 리셋하지 않고 요청만 보낸다 - 이펙트
  // 본문에서 곧바로(동기적으로) setState를 호출하면 안 되기 때문에(react-hooks/set-state-in-effect),
  // "리셋"은 이펙트가 아니라 재시도 버튼의 이벤트 핸들러(handleRetryPets)에서 한다.
  const fetchPets = useCallback(() => {
    getMyPets()
      .then((result) => {
        if (aliveRef.current) setPets(result);
      })
      .catch((err: unknown) => {
        if (!aliveRef.current) return;
        setPetsError(err instanceof ApiError ? err.message : "반려동물 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (aliveRef.current) setPetsLoading(false);
      });
  }, []);

  function handleRetryPets() {
    setPetsLoading(true);
    setPetsError(null);
    fetchPets();
  }

  async function handleWithdraw() {
    const confirmed = await confirm({
      title: "정말 탈퇴하시겠어요?",
      description: "탈퇴하면 되돌릴 수 없어요. 진행 중인 예약이나 결제가 있으면 먼저 처리한 뒤 다시 시도해 주세요.",
      confirmLabel: "탈퇴",
    });
    if (!confirmed) return;

    setWithdrawing(true);
    setWithdrawError(null);
    try {
      await withdraw();
      // 백엔드가 이미 refresh token/access token을 무효화했지만, 프론트 세션 상태(user/status)도
      // 지워야 로그인한 것처럼 보이는 화면이 안 남는다. logout()은 실패해도 finally에서
      // 클라이언트 상태를 정리하므로(AuthContext 참고) 여기선 결과를 신경 쓰지 않아도 된다.
      await logout();
      navigate("/");
    } catch (err) {
      setWithdrawError(err instanceof ApiError ? err.message : "탈퇴에 실패했어요. 잠시 후 다시 시도해 주세요.");
      setWithdrawing(false);
    }
  }

  useEffect(() => {
    aliveRef.current = true;
    // getMe()는 화면 전체(닉네임/이메일 등)를 구성하는 필수 데이터라 실패하면 페이지 전체를
    // 에러로 처리한다. getMyPets()는 반려동물 섹션만의 데이터라 따로 불러온다 - 하나로 묶으면
    // (Promise.all) 반려동물 조회만 실패해도 이미 받아온 내 정보까지 화면에 못 띄우게 된다.
    getMe()
      .then((result) => {
        if (aliveRef.current) setMe(result);
      })
      .catch((err: unknown) => {
        if (!aliveRef.current) return;
        setError(err instanceof ApiError ? err.message : "마이페이지를 불러오지 못했어요.");
      })
      .finally(() => {
        if (aliveRef.current) setLoading(false);
      });

    fetchPets();

    return () => {
      aliveRef.current = false;
    };
  }, [fetchPets]);

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

      <section className="mb-8">
        <SectionHeader title="내 정보" icon={<User size={20} className="shrink-0 text-primary-strong" aria-hidden="true" />} />
        <Card className="p-8">
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
              {me.passwordChangeAvailable && (
                <Link to="/mypage/password">
                  <Button variant="outline" className="shadow-sm hover:bg-[#EFEFED]!">비밀번호 변경</Button>
                </Link>
              )}
              <Link to="/mypage/edit">
                <Button variant="outline" className="shadow-sm hover:bg-[#EFEFED]!">내 정보 수정</Button>
              </Link>
            </div>
          </div>
        </Card>
      </section>

      <section className="mb-8">
        <SectionHeader
          title="내 반려동물"
          description="등록한 반려동물을 관리해요."
          icon={<PawPrint size={20} className="shrink-0 text-primary-strong" aria-hidden="true" />}
        />
        {petsLoading ? (
          <p className="py-10 text-center text-sm text-muted">불러오는 중이에요…</p>
        ) : petsError ? (
          <div className="surface grid min-h-72 place-items-center p-8 text-center">
            <div>
              <h2 className="text-lg font-extrabold">반려동물 목록을 불러오지 못했어요.</h2>
              <p className="mt-2 max-w-sm text-sm leading-6 text-muted">{petsError}</p>
              <Button className="mt-5" variant="outline" onClick={handleRetryPets}>
                다시 시도
              </Button>
            </div>
          </div>
        ) : pets.length === 0 ? (
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
                <tr className="border-b border-line bg-[#FAF7F2] text-xs font-bold text-ink">
                  <th className="px-8 py-4">이름</th>
                  <th className="px-8 py-4">종</th>
                  <th className="px-8 py-4">생년월일</th>
                  <th className="px-8 py-4" aria-label="동물등록증" />
                </tr>
              </thead>
              <tbody>
                {pets.map((pet) => (
                  <tr
                    key={pet.petId}
                    className="border-b border-line last:border-0 hover:bg-[#FCFAF7]"
                  >
                    <td className="px-8 py-4">
                      <Link to={`/mypage/pets/${pet.petId}`} className="inline-flex items-center gap-2 font-bold text-ink hover:underline">
                        <Heart size={13} className="shrink-0 fill-primary-strong text-primary-strong" aria-hidden="true" />
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
                <Button variant="outline" className="shadow-sm hover:bg-[#EFEFED]!">반려동물 등록</Button>
              </Link>
            </div>
          </>
        )}
      </section>

      <section>
        <SectionHeader title="바로가기" />
        <div className="grid gap-3 sm:grid-cols-2">
          <Link to="/reservations/me" className="surface flex items-center gap-3 p-5 hover:bg-[#FCFAF7]">
            <Ticket size={20} className="shrink-0 text-primary-strong" />
            <div>
              <p className="font-bold text-ink">내 예약 목록</p>
              <p className="text-sm text-muted">예매한 행사와 입장 QR을 확인해요.</p>
            </div>
          </Link>
          <Link to="/fair-applications/me" className="surface flex items-center gap-3 p-5 hover:bg-[#FCFAF7]">
            <CalendarDays size={20} className="shrink-0 text-primary-strong" />
            <div>
              <p className="font-bold text-ink">내 행사 신청 목록</p>
              <p className="text-sm text-muted">신청한 행사의 심사 현황을 확인해요.</p>
            </div>
          </Link>
          <Link to="/mypage/reviews" className="surface flex items-center gap-3 p-5 hover:bg-[#FCFAF7]">
            <Star size={20} className="shrink-0 text-primary-strong" />
            <div>
              <p className="font-bold text-ink">내 리뷰</p>
              <p className="text-sm text-muted">내가 남긴 리뷰를 확인하고 수정·삭제해요.</p>
            </div>
          </Link>
        </div>
      </section>

      {/* 위험한 액션이라 다른 버튼들과 나란히 두지 않고 페이지 맨 아래에 따로 둔다(오클릭 방지). */}
      <section className="mt-12 border-t border-line pt-6 text-right">
        {withdrawError && <p className="mb-2 text-sm font-bold text-primary-strong">{withdrawError}</p>}
        <Button className="text-red-600 hover:bg-red-50 hover:text-red-700" variant="ghost" onClick={handleWithdraw} disabled={withdrawing}>
          {withdrawing ? "탈퇴 처리 중…" : "회원 탈퇴"}
        </Button>
      </section>

      {confirmDialog}
    </PageContainer>
  );
}
