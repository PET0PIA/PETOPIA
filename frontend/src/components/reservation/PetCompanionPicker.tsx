import { PawPrint } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../../api/client";
import { createPet, getMyPets, type Pet, type PetRequest } from "../../api/pet";
import { Button } from "../ui/Button";
import { Dialog } from "../ui/Dialog";
import { PetForm } from "../mypage/PetForm";

interface PetCompanionPickerProps {
  /** 행사의 동반 허용 여부. false면 아무것도 그리지 않는다(정책 P2). */
  petAllowed: boolean;
  /** 현재 고른 petId들. */
  value: number[];
  onChange: (petIds: number[]) => void;
  disabled?: boolean;
  /**
   * "예/아니오"를 처음에 어느 쪽으로 열어둘지. 예약 생성에서는 아무것도 안 고른 상태로
   * 시작하고(null), 예약 수정에서는 이미 동반 중이면 "예"로 열어야 한다.
   */
  initialCompanion?: boolean | null;
}

/**
 * "반려동물과 함께 가시나요?" 선택 UI.
 *
 * 예매 화면(사전예약·현장예매)과 예약 상세의 방문일 변경 다이얼로그가 같은 컴포넌트를 쓴다 -
 * 화면마다 규칙이 갈라지면 어디선 되고 어디선 안 되는 상태가 생긴다.
 *
 * <p>등록된 반려동물이 없으면 이 자리에서 모달로 등록하게 한다. 마이페이지의 등록 화면
 * (PetFormPage)은 저장 후 /mypage/pets/{id}로 이동하도록 하드코딩돼 있어, 예약 도중 그쪽으로
 * 보내면 예약 흐름이 끊긴다. 입력 폼(PetForm)이 이미 분리돼 있어 모달로 재사용할 수 있다.
 *
 * <p>등록을 취소하면 동반 선택을 해제하고 예약은 그대로 진행할 수 있다(정책 P3) - 등록이
 * 번거로워 그만둔 사람도 혼자서는 갈 수 있어야 한다.
 */
export function PetCompanionPicker({
  petAllowed,
  value,
  onChange,
  disabled = false,
  initialCompanion = null,
}: PetCompanionPickerProps) {
  const [companion, setCompanion] = useState<boolean | null>(initialCompanion);
  const [pets, setPets] = useState<Pet[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [registerError, setRegisterError] = useState<string | null>(null);

  const loadPets = useCallback(() => {
    return getMyPets()
      .then((result) => {
        setPets(result);
        setLoadError(null);
        return result;
      })
      .catch((error: unknown) => {
        setPets([]);
        setLoadError(error instanceof ApiError ? error.message : "반려동물 목록을 불러오지 못했어요.");
        return [] as Pet[];
      });
  }, []);

  useEffect(() => {
    if (!petAllowed) return;
    void loadPets();
  }, [petAllowed, loadPets]);

  if (!petAllowed) return null;

  function chooseCompanion(next: boolean) {
    setCompanion(next);
    // "아니오"로 되돌리면 고른 것을 비운다 - 화면에 안 보이는 선택이 그대로 서버에 나가면
    // 사용자가 취소했다고 생각한 동반이 예약에 남는다.
    if (!next) onChange([]);
  }

  function togglePet(petId: number) {
    onChange(value.includes(petId) ? value.filter((id) => id !== petId) : [...value, petId]);
  }

  async function handleRegister(payload: PetRequest) {
    setRegistering(true);
    setRegisterError(null);
    try {
      const created = await createPet(payload);
      await loadPets();
      // 방금 등록한 반려동물은 곧바로 선택된 상태로 둔다 - 등록하려던 이유가 그것이다.
      onChange([...value, created.petId]);
      setRegisterOpen(false);
    } catch (error) {
      setRegisterError(error instanceof ApiError ? error.message : "반려동물 등록에 실패했어요.");
    } finally {
      setRegistering(false);
    }
  }

  function cancelRegister() {
    setRegisterOpen(false);
    setRegisterError(null);
    // 등록을 그만뒀는데 "예"가 남아 있으면 고를 것이 없는 채로 진행 버튼이 막힌다.
    // 동반만 해제하고 예약 자체는 계속할 수 있게 한다(정책 P3).
    if (value.length === 0) chooseCompanion(false);
  }

  const petList = pets ?? [];
  const loading = pets === null;

  return (
    <section className="mb-6">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-bold text-ink">
        <PawPrint size={16} aria-hidden="true" />
        반려동물과 함께 가시나요?
      </h3>

      <div className="mb-3 grid grid-cols-2 gap-3">
        <button
          type="button"
          disabled={disabled}
          aria-pressed={companion === true}
          onClick={() => chooseCompanion(true)}
          className={`rounded-card border p-3 text-sm font-bold transition ${
            companion === true ? "border-primary-strong bg-primary-soft text-primary-strong" : "border-line bg-card hover:bg-page"
          } ${disabled ? "cursor-not-allowed opacity-60" : ""}`}
        >
          네, 함께 가요
        </button>
        <button
          type="button"
          disabled={disabled}
          aria-pressed={companion === false}
          onClick={() => chooseCompanion(false)}
          className={`rounded-card border p-3 text-sm font-bold transition ${
            companion === false ? "border-primary-strong bg-primary-soft text-primary-strong" : "border-line bg-card hover:bg-page"
          } ${disabled ? "cursor-not-allowed opacity-60" : ""}`}
        >
          아니요, 저만 가요
        </button>
      </div>

      {companion === true && (
        <div className="rounded-card border border-line bg-page p-4">
          {loading ? (
            <p className="text-sm text-muted">반려동물 목록을 불러오는 중이에요…</p>
          ) : petList.length === 0 ? (
            <div className="space-y-3">
              <p className="text-sm text-muted">
                {loadError ?? "등록된 반려동물이 없어요. 지금 등록하면 이 예약에 바로 담을 수 있어요."}
              </p>
              <Button type="button" variant="outline" disabled={disabled} onClick={() => setRegisterOpen(true)}>
                반려동물 등록하기
              </Button>
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-xs text-muted">함께 갈 반려동물을 골라 주세요. 여러 마리도 괜찮아요.</p>
              <div className="flex flex-wrap gap-2">
                {petList.map((pet) => {
                  const selected = value.includes(pet.petId);
                  return (
                    <button
                      key={pet.petId}
                      type="button"
                      disabled={disabled}
                      aria-pressed={selected}
                      onClick={() => togglePet(pet.petId)}
                      className={`rounded-full border px-3 py-1.5 text-xs font-bold transition ${
                        selected ? "border-primary-strong bg-primary-soft text-primary-strong" : "border-line bg-card text-muted hover:bg-card"
                      } ${disabled ? "cursor-not-allowed opacity-60" : ""}`}
                    >
                      {pet.name}
                      <span className="ml-1 font-normal">
                        {pet.species}
                        {pet.breed ? ` · ${pet.breed}` : ""}
                      </span>
                    </button>
                  );
                })}
              </div>
              <Button type="button" variant="outline" disabled={disabled} onClick={() => setRegisterOpen(true)}>
                새로 등록하기
              </Button>
              {value.length === 0 && (
                <p className="text-xs font-bold text-primary-strong">함께 갈 반려동물을 한 마리 이상 골라 주세요.</p>
              )}
            </div>
          )}
        </div>
      )}

      {/* 마이페이지로 나가지 않고 이 자리에서 등록한다 - 예약 흐름을 끊지 않기 위해서다. */}
      <Dialog open={registerOpen} onClose={cancelRegister} title="반려동물 등록" size="lg">
        <PetForm
          submitLabel="등록하고 이 예약에 담기"
          submitting={registering}
          submitError={registerError}
          onSubmit={handleRegister}
          onCancel={cancelRegister}
        />
      </Dialog>
    </section>
  );
}
