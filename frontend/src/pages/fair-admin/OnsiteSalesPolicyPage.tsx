import { AlertCircle, Settings2 } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../api/client";
import { getFairDates, type FairDate } from "../../api/fair";
import {
  getOnsiteSalesPolicy,
  saveOnsiteSalesPolicy,
  type OnsiteSalesPolicy,
  type OnsiteSalesStatus,
} from "../../api/reservation";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Input } from "../../components/ui/Input";
import { Table } from "../../components/ui/Table";
import { useFairSelector } from "../../contexts/FairSelectorContext";

const statusLabels: Record<OnsiteSalesStatus, string> = {
  OPEN: "판매중",
  PAUSED: "일시중지",
  CLOSED: "마감",
};
const statusTones: Record<OnsiteSalesStatus, "primary" | "leaf" | "neutral"> = {
  OPEN: "leaf",
  PAUSED: "primary",
  CLOSED: "neutral",
};

// 운영일 + 그 운영일의 현장예매 정책을 한 줄로 묶는다.
interface PolicyRow {
  fairDate: FairDate;
  policy: OnsiteSalesPolicy;
}

function formatPrice(price: number) {
  return price === 0 ? "무료" : `${price.toLocaleString()}원`;
}

export function OnsiteSalesPolicyPage() {
  // 콘솔 상단 바의 "관리 행사" 선택기가 현재 행사를 정한다.
  const { fairId } = useFairSelector();

  const [rows, setRows] = useState<PolicyRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 정책 편집 다이얼로그.
  const [editing, setEditing] = useState<PolicyRow | null>(null);
  const [formPrice, setFormPrice] = useState("");
  const [formStatus, setFormStatus] = useState<OnsiteSalesStatus>("CLOSED");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // 요청 세대 번호. 늦게 도착한 이전 조회 응답이 최신 화면을 덮어쓰지 못하게 막는다.
  const loadSeq = useRef(0);

  // 상단 선택기의 행사가 바뀌면 그 행사의 운영일 + 운영일별 현장예매 정책을 다시 불러온다.
  // loadSeq로 늦게 도착한 이전 조회가 최신 화면을 덮어쓰지 못하게 막는다.
  useEffect(() => {
    if (fairId === null) return;
    const currentFairId = fairId;
    const seq = ++loadSeq.current;
    setLoading(true);
    setLoadError(null);
    (async () => {
      try {
        const dates = await getFairDates(currentFairId);
        // 운영일마다 현장예매 정책을 조회한다(미설정이면 서버가 기본값을 준다).
        const policies = await Promise.all(
          dates.map((date) => getOnsiteSalesPolicy(currentFairId, date.fairDateId)),
        );
        if (seq !== loadSeq.current) return; // 더 최신 조회가 있으면 이 응답은 버린다
        setRows(dates.map((fairDate, index) => ({ fairDate, policy: policies[index] })));
      } catch (error) {
        if (seq !== loadSeq.current) return;
        setRows([]);
        setLoadError(error instanceof ApiError ? error.message : "현장예매 정책을 불러오지 못했어요.");
      } finally {
        if (seq === loadSeq.current) setLoading(false);
      }
    })();
  }, [fairId]);

  function openDialog(row: PolicyRow) {
    setEditing(row);
    setFormPrice(String(row.policy.price));
    setFormStatus(row.policy.status);
    setSaveError(null);
  }

  async function handleSave() {
    if (fairId === null || editing === null) return;
    const price = Number(formPrice);
    if (formPrice === "" || !Number.isInteger(price) || price < 0) {
      setSaveError("가격은 0 이상의 숫자로 입력해 주세요.");
      return;
    }

    setSaving(true);
    setSaveError(null);
    try {
      const saved = await saveOnsiteSalesPolicy(fairId, editing.fairDate.fairDateId, {
        price,
        status: formStatus,
        // 조회 때 받은 version을 그대로 보낸다. 신규(미설정)면 0.
        expectedVersion: editing.policy.version,
      });
      // 저장된 정책으로 해당 행을 갱신한다(새 version 포함).
      setRows((previous) =>
        previous.map((row) =>
          row.fairDate.fairDateId === editing.fairDate.fairDateId ? { ...row, policy: saved } : row,
        ),
      );
      setEditing(null);
    } catch (error) {
      // R009: 다른 관리자가 먼저 변경 → version 불일치
      setSaveError(
        error instanceof ApiError
          ? error.message + (error.code === "R009" ? " 목록을 다시 불러와 주세요." : "")
          : "정책을 저장하지 못했어요.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader
        eyebrow="박람회 관리자"
        title="현장예매 설정"
        description="운영일마다 현장 직접예매의 가격과 판매 상태(판매중·일시중지·마감)를 관리해요. 판매중이 아니면 현장예매를 받지 않아요."
      />

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {fairId === null && !loadError && (
        <EmptyState
          title="관리할 행사가 없어요."
          description="상단 바에서 행사를 선택하면 운영일별 현장예매 정책이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요."
        />
      )}

      {fairId !== null && loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">
          현장예매 정책을 불러오는 중이에요...
        </div>
      )}

      {fairId !== null && !loading && rows.length === 0 && !loadError && (
        <EmptyState
          title="등록된 운영일이 없어요."
          description="먼저 운영일·정원 관리에서 운영일을 등록해 주세요."
        />
      )}

      {fairId !== null && !loading && rows.length > 0 && (
        <Table>
          <thead>
            <tr className="border-b border-line text-xs font-bold text-muted">
              <th className="px-4 py-3">운영 날짜</th>
              <th className="px-4 py-3">현장예매 가격</th>
              <th className="px-4 py-3">판매 상태</th>
              <th className="px-4 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.fairDate.fairDateId} className="border-b border-line last:border-0">
                <td className="px-4 py-3 font-bold">{row.fairDate.operationDate}</td>
                <td className="px-4 py-3 text-muted">{formatPrice(row.policy.price)}</td>
                <td className="px-4 py-3">
                  <Badge tone={statusTones[row.policy.status]}>{statusLabels[row.policy.status]}</Badge>
                </td>
                <td className="px-4 py-3">
                  <div className="flex justify-end">
                    <Button variant="outline" onClick={() => openDialog(row)}>
                      <Settings2 size={16} />
                      설정
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <Dialog
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing ? `현장예매 설정 · ${editing.fairDate.operationDate}` : "현장예매 설정"}
      >
        <div className="space-y-4">
          {saveError && <p className="text-sm font-bold text-primary-strong">{saveError}</p>}

          <div>
            <span className="mb-1.5 block text-sm font-bold text-ink">현장예매 가격</span>
            <Input
              type="number"
              min={0}
              value={formPrice}
              onChange={(event) => setFormPrice(event.target.value)}
              placeholder="예: 10000 (0이면 무료)"
            />
          </div>

          <div>
            <span className="mb-1.5 block text-sm font-bold text-ink">판매 상태</span>
            <div className="grid grid-cols-3 gap-2">
              {(["OPEN", "PAUSED", "CLOSED"] as OnsiteSalesStatus[]).map((status) => (
                <button
                  key={status}
                  type="button"
                  onClick={() => setFormStatus(status)}
                  className={`rounded-button border p-3 text-sm font-bold transition ${
                    formStatus === status
                      ? "border-primary-strong bg-primary-soft text-ink"
                      : "border-line bg-card text-muted hover:bg-page"
                  }`}
                >
                  {statusLabels[status]}
                </button>
              ))}
            </div>
            <p className="mt-2 text-xs text-muted">
              판매중(OPEN)일 때만 관람객이 현장예매를 할 수 있어요.
            </p>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setEditing(null)}>
              취소
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? "저장 중..." : "저장"}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
