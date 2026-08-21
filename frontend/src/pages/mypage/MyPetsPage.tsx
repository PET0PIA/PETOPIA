import { Heart, IdCard } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Table } from "../../components/ui/Table";
import { ApiError } from "../../api/client";
import { getMyPets, type Pet } from "../../api/pet";

/** 반려동물 정보 - 예전 마이페이지 본문에 있던 반려동물 표를 그대로 옮긴 화면.
 * 조회·등록·상세 흐름은 그대로다(추가된 API 없음). */
export function MyPetsPage() {
  const [pets, setPets] = useState<Pet[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // 재시도 버튼이 이 값을 올리면 아래 이펙트가 다시 돈다(이펙트 본문에서 setState를 하지 않으려고
  // loading/error 초기화는 버튼 핸들러에서 한다).
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let alive = true;
    getMyPets()
      .then((result) => { if (alive) setPets(result); })
      .catch((err: unknown) => {
        if (alive) setError(err instanceof ApiError ? err.message : "반려동물 목록을 불러오지 못했어요.");
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [reloadKey]);

  function handleRetry() {
    setLoading(true);
    setError(null);
    setReloadKey((value) => value + 1);
  }

  return (
    <div>
      <PageHeader
        eyebrow="마이페이지"
        title="반려동물 정보"
        description="등록한 반려동물을 관리해요. 이름을 누르면 동물등록증을 볼 수 있어요."
        action={
          <Link to="/mypage/pets/new">
            <Button variant="outline" className="shadow-sm">반려동물 등록</Button>
          </Link>
        }
      />

      {loading ? (
        <p className="py-16 text-center text-sm text-muted">불러오는 중이에요…</p>
      ) : error ? (
        <div className="surface grid min-h-72 place-items-center p-8 text-center">
          <div>
            <h2 className="text-lg font-extrabold">반려동물 목록을 불러오지 못했어요.</h2>
            <p className="mt-2 max-w-sm text-sm leading-6 text-muted">{error}</p>
            <Button className="mt-5" variant="outline" onClick={handleRetry}>다시 시도</Button>
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
        <Table>
          <thead>
            <tr className="border-b border-line bg-surface-alt text-xs font-bold text-ink">
              <th className="px-6 py-4">이름</th>
              <th className="px-6 py-4">종</th>
              <th className="px-6 py-4">생년월일</th>
              <th className="px-6 py-4" aria-label="동물등록증" />
            </tr>
          </thead>
          <tbody>
            {pets.map((pet) => (
              <tr key={pet.petId} className="border-b border-line last:border-0 hover:bg-surface-alt">
                <td className="px-6 py-4">
                  <Link
                    to={`/mypage/pets/${pet.petId}`}
                    className="inline-flex items-center gap-2 font-bold text-ink hover:underline"
                  >
                    <Heart size={13} className="shrink-0 fill-primary-strong text-primary-strong" aria-hidden="true" />
                    {pet.name}
                  </Link>
                </td>
                <td className="px-6 py-4 text-muted">{pet.species}</td>
                <td className="px-6 py-4 text-muted">{pet.birthDate ?? "미입력"}</td>
                <td className="px-6 py-4 text-right">
                  <Link
                    to={`/mypage/pets/${pet.petId}`}
                    aria-label={`${pet.name} 동물등록증 보기`}
                    className="inline-flex items-center gap-1.5 rounded-button border border-line px-3 py-1.5 text-xs font-bold text-ink hover:bg-surface-alt"
                  >
                    <IdCard size={14} aria-hidden="true" />
                    동물등록증
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
}
