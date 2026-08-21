import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Card } from "../../components/ui/Card";
import { PetForm } from "../../components/mypage/PetForm";
import { ApiError } from "../../api/client";
import { createPet, type PetRequest } from "../../api/pet";

export function PetFormPage() {
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  async function handleSubmit(payload: PetRequest) {
    setSubmitting(true);
    setSubmitError(null);
    try {
      const pet = await createPet(payload);
      navigate(`/mypage/pets/${pet.petId}`);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "반려동물 등록에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="마이페이지" title="반려동물 등록" />
      <Card className="mx-auto max-w-lg p-8">
        <PetForm
          submitLabel="등록"
          submitting={submitting}
          submitError={submitError}
          onSubmit={handleSubmit}
          onCancel={() => navigate("/mypage/pets")}
        />
      </Card>
    </PageContainer>
  );
}
