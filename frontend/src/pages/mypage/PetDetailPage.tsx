import { toPng } from "html-to-image";
import { Download, PawPrint } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { useConfirm } from "../../components/ui/useConfirm";
import { PetForm } from "../../components/mypage/PetForm";
import { toPetFormValues } from "../../components/mypage/petFormValues";
import { ApiError } from "../../api/client";
import { deletePet, getPet, updatePet, type Pet, type PetRequest } from "../../api/pet";
import { getMe } from "../../api/user";
import petopiaHeaderLogo from "../../assets/logo/petopiaLOGO.png";

const genderLabels: Record<string, string> = { MALE: "남", FEMALE: "여" };

/* 카드 우하단 구석에 배경으로 깔리는 큼직한 발바닥 장식. lucide PawPrint 아이콘의 도형을
   그대로 가져다 하나만(반복 없이) 구석에 앉힌다 - 별도 이미지 에셋 없이 순수 CSS 배경으로 처리. */
const PAW_CORNER_URL =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='120' height='120'%3E%3Cg fill='%23FF6B6B' fill-opacity='0.16'%3E%3Ccircle cx='11' cy='4' r='2'/%3E%3Ccircle cx='18' cy='8' r='2'/%3E%3Ccircle cx='20' cy='16' r='2'/%3E%3Cpath d='M9 10a5 5 0 0 1 5 5v3.5a3.5 3.5 0 0 1-6.84 1.045Q6.52 17.48 4.46 16.84A3.5 3.5 0 0 1 5.5 10Z'/%3E%3C/g%3E%3C/svg%3E";

function formatIsNeutered(value: boolean): string {
  return value ? "완료" : "안 함";
}

/*
 * 알레르기 표시 문구. "있음"인데 항목이 비어 있을 수 있다(여부만 답하고 항목은 아직
 * 안 고른 상태) - 그때 빈칸으로 두면 등록증이 잘못 만들어진 것처럼 보이므로 여부만 적는다.
 * '기타' 같은 직접 입력 항목은 라벨 대신 사용자가 적은 내용을 보여주는 게 정보량이 많다.
 */
function formatAllergies(pet: Pet): string {
  if (pet.hasAllergy === false) return "없음";
  const allergies = pet.allergies ?? [];
  if (allergies.length === 0) return "있음";
  return allergies
    .map((allergy) => (allergy.requiresText && allergy.otherText ? `${allergy.label}(${allergy.otherText})` : allergy.label))
    .join(", ");
}

export function PetDetailPage() {
  const { petId } = useParams<{ petId: string }>();
  const navigate = useNavigate();
  const { confirm, confirmDialog } = useConfirm();

  const [pet, setPet] = useState<Pet | null>(null);
  const [ownerName, setOwnerName] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [savingImage, setSavingImage] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let alive = true;
    // petId가 바뀌는 경우는 실질적으로 이 화면에서 발생하지 않는다(항상 마이페이지에서
    // 새로 진입) - 그래서 로딩 상태를 여기서 다시 true로 되돌리지 않는다(첫 마운트에는
    // useState(true) 초기값으로 이미 충분하고, effect 안에서 동기적으로 setState를 하면
    // 불필요한 리렌더가 한 번 더 생긴다).
    // getPet은 화면에 꼭 있어야 하는 필수 데이터고, getMe는 "보호자" 표시용 부가 정보다.
    // 두 요청을 Promise.all로 묶으면 getMe만 실패해도(예: 프로필 조회 일시 장애) 화면 전체가
    // 에러로 막혀서 정작 있는 반려동물 정보/수정/삭제까지 다 못 하게 된다 - 그래서 따로 둔다.
    getPet(Number(petId))
      .then((petResult) => {
        if (!alive) return;
        setPet(petResult);
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "반려동물 정보를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });

    getMe()
      .then((me) => {
        if (alive) setOwnerName(me.nickname);
      })
      .catch(() => {
        if (alive) setOwnerName(null);
      });

    return () => {
      alive = false;
    };
  }, [petId]);

  async function handleUpdate(payload: PetRequest) {
    if (!pet) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const updated = await updatePet(pet.petId, payload);
      setPet(updated);
      setEditing(false);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "수정에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  /* 카드 DOM만 캡처해서 PNG로 저장한다(버튼 줄은 ref 바깥이라 안 찍힘). 픽셀 배율을 2배로
     둬서 저장한 이미지가 화면 크기 그대로 저장했을 때보다 흐릿하지 않게 한다. */
  async function handleSaveImage() {
    if (!cardRef.current || !pet) return;
    setSavingImage(true);
    try {
      const dataUrl = await toPng(cardRef.current, { pixelRatio: 2 });
      const link = document.createElement("a");
      link.download = `${pet.name}_동물등록증.png`;
      link.href = dataUrl;
      link.click();
    } catch {
      setSubmitError("이미지 저장에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSavingImage(false);
    }
  }

  async function handleDelete() {
    if (!pet) return;
    const confirmed = await confirm({
      title: "반려동물을 삭제할까요?",
      description: `${pet.name}을(를) 삭제하면 되돌릴 수 없어요.`,
    });
    if (!confirmed) return;

    try {
      await deletePet(pet.petId);
      navigate("/mypage");
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "삭제에 실패했어요. 잠시 후 다시 시도해 주세요.");
    }
  }

  if (loading) {
    return (
      <PageContainer className="py-10">
        <p className="py-16 text-center text-sm text-muted">불러오는 중이에요…</p>
      </PageContainer>
    );
  }

  if (loadError || !pet) {
    return (
      <PageContainer className="py-10">
        <EmptyState
          title="반려동물 정보를 불러오지 못했어요."
          description={loadError ?? undefined}
          actionTo="/mypage"
          actionLabel="마이페이지로 돌아가기"
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="마이페이지" title={pet.name} description={`${pet.species}${pet.breed ? ` · ${pet.breed}` : ""}`} />
      {editing ? (
        <Card className="mx-auto max-w-lg p-8">
          <PetForm
            initialValues={toPetFormValues(pet)}
            initialImageUrl={pet.imageUrl}
            submitLabel="저장"
            submitting={submitting}
            submitError={submitError}
            onSubmit={handleUpdate}
            onCancel={() => {
              setEditing(false);
              setSubmitError(null);
            }}
          />
        </Card>
      ) : (
        <div className="mx-auto max-w-lg space-y-4">
          {/* 실제 신분증 레이아웃(사진 + 항목별 행 + 하단 등록번호/발급일 띠)을 본떠서, 정보
              조회 화면이라기보다 "이 반려동물의 등록증"처럼 보이게 한다. */}
          <div
            ref={cardRef}
            className="relative overflow-hidden rounded-2xl border-2 border-ink/15 bg-card shadow-sm"
            style={{
              backgroundImage: `url("${PAW_CORNER_URL}")`,
              backgroundRepeat: "no-repeat",
              backgroundPosition: "right -6px bottom -3px",
            }}
          >
            <div className="flex items-center justify-between bg-[#FAF7F2] px-6 py-3">
              <div className="flex items-center gap-2">
                <img src={petopiaHeaderLogo} alt="" className="h-6 w-auto" aria-hidden="true" />
                <span className="text-base font-black tracking-tight text-ink">동물등록증</span>
              </div>
              <span className="text-xs font-bold tracking-widest text-primary-strong">PETOPIA</span>
            </div>

            <div className="flex gap-5 p-6">
              <div className="grid size-28 shrink-0 place-items-center overflow-hidden rounded-lg border border-line bg-page">
                {pet.imageUrl ? (
                  <img src={pet.imageUrl} alt={`${pet.name} 사진`} className="size-full object-cover" />
                ) : (
                  <PawPrint size={28} className="text-muted" aria-hidden="true" />
                )}
              </div>

              <dl className="flex-1 space-y-2.5 text-sm">
                <div className="flex items-baseline gap-3">
                  <dt className="w-16 shrink-0 font-bold text-muted">이름</dt>
                  <dd className="font-extrabold text-ink">{pet.name}</dd>
                </div>
                <div className="flex items-baseline gap-3">
                  <dt className="w-16 shrink-0 font-bold text-muted">종</dt>
                  <dd className="text-ink">{pet.species}{pet.breed ? ` · ${pet.breed}` : ""}</dd>
                </div>
                <div className="flex items-baseline gap-3">
                  <dt className="w-16 shrink-0 font-bold text-muted">생년월일</dt>
                  <dd className="text-ink">{pet.birthDate ?? "미입력"}</dd>
                </div>
                <div className="flex items-baseline gap-3">
                  <dt className="w-16 shrink-0 font-bold text-muted">성별</dt>
                  <dd className="text-ink">{pet.gender ? genderLabels[pet.gender] : "미입력"}</dd>
                </div>
                {/* 미선택(null)이면 값도 애매하고 사용자가 고른 적도 없는 정보라, "미입력"으로
                    띄우기보다 행 자체를 아예 숨긴다 - breed/birthDate/gender와는 다른 처리. */}
                {pet.isNeutered !== null && (
                  <div className="flex items-baseline gap-3">
                    <dt className="w-16 shrink-0 font-bold text-muted">중성화</dt>
                    <dd className="text-ink">{formatIsNeutered(pet.isNeutered)}</dd>
                  </div>
                )}
                {/* 중성화와 같은 이유로 미입력(null)이면 행 자체를 숨긴다. */}
                {pet.hasAllergy !== null && (
                  <div className="flex items-baseline gap-3">
                    <dt className="w-16 shrink-0 font-bold text-muted">알레르기</dt>
                    <dd className="text-ink">{formatAllergies(pet)}</dd>
                  </div>
                )}
              </dl>
            </div>

            <div className="flex items-center justify-between border-t border-dashed border-line px-6 py-3 text-xs text-muted">
              <span>보호자 {ownerName ?? "미확인"}</span>
              <span>등록일 {new Date(pet.createdAt).toLocaleDateString("ko-KR")}</span>
            </div>

            {/* 발급기관 직인을 흉내낸 도장 - 카드 우하단에 살짝 겹치게, 실제 관공서 직인/워터마크
                문구는 쓰지 않고 우리 브랜드명만 넣어 실제 문서로 오인될 요소를 피한다. */}
            <div
              className="pointer-events-none absolute bottom-11 right-5 flex size-16 rotate-[-14deg] flex-col items-center justify-center gap-0.5 rounded-full border-2 border-primary-strong/60 text-primary-strong/70"
              aria-hidden="true"
            >
              <PawPrint size={14} />
              <span className="text-[8px] font-black leading-none tracking-wider">PETOPIA</span>
              <span className="text-[8px] font-black leading-none">발행</span>
            </div>
          </div>

          {submitError && <p className="text-sm font-bold text-primary-strong">{submitError}</p>}

          <div className="flex items-center justify-between gap-2">
            <Button variant="outline" onClick={handleSaveImage} disabled={savingImage}>
              <Download size={16} aria-hidden="true" />
              {savingImage ? "저장 중…" : "이미지로 저장"}
            </Button>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => navigate("/mypage/pets")}>
                목록으로
              </Button>
              <Button variant="outline" onClick={() => setEditing(true)}>
                수정
              </Button>
              <Button variant="secondary" onClick={handleDelete}>
                삭제
              </Button>
            </div>
          </div>
        </div>
      )}
      {confirmDialog}
    </PageContainer>
  );
}
