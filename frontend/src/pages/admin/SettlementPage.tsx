import { AlertCircle, Calculator, Check, RefreshCw, Search } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  calculateSettlement,
  confirmSettlement,
  getSettlementsByFair,
  getVendorSettlement,
  recalculateSettlement,
  type SettlementResponse,
  type SettlementStatus,
} from "../../api/settlement";
import {
  getCommissionRate,
  setCommissionRate,
  type CommissionRateResponse,
  type CommissionRateScope,
} from "../../api/commissionRate";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { useConfirm } from "../../components/ui/useConfirm";

const statusLabels: Record<SettlementStatus, string> = {
  PENDING: "대기 중",
  CONFIRMED: "확정됨",
  PAID: "지급 완료",
};

const statusTones: Record<SettlementStatus, "sun" | "leaf" | "primary"> = {
  PENDING: "sun",
  CONFIRMED: "leaf",
  PAID: "primary",
};

function formatWon(value: number) {
  return `${value.toLocaleString("ko-KR")}원`;
}

function formatRatePercent(rate: number) {
  return `${(rate * 100).toFixed(2)}%`;
}

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

export function SettlementPage() {
  const { confirm, confirmDialog } = useConfirm();

  // ── 수수료율 관리 ──
  const [globalRate, setGlobalRate] = useState<CommissionRateResponse | null>(null);
  const [rateLoading, setRateLoading] = useState(true);
  const [rateLoadError, setRateLoadError] = useState<string | null>(null);

  const [rateScope, setRateScope] = useState<CommissionRateScope>("GLOBAL");
  const [rateFairIdInput, setRateFairIdInput] = useState("");
  const [ratePercentInput, setRatePercentInput] = useState("");
  const [rateSubmitting, setRateSubmitting] = useState(false);
  const [rateFormError, setRateFormError] = useState<string | null>(null);
  const [rateFormSuccess, setRateFormSuccess] = useState<string | null>(null);

  // 최초 조회(GET)가 끝나기 전에 저장(PUT)이 먼저 성공할 수 있어서, 조회가 나중에 도착한
  // 응답으로 방금 저장한 값을 덮어쓰지 않도록 버전을 추적한다. 저장이 성공하면 버전을 올려서
  // 그 전에 시작된 조회 응답은 무시되게 한다.
  const globalRateVersionRef = useRef(0);

  useEffect(() => {
    const version = globalRateVersionRef.current;
    let ignore = false;
    getCommissionRate()
      .then((data) => { if (!ignore && globalRateVersionRef.current === version) setGlobalRate(data); })
      .catch((error) => { if (!ignore && globalRateVersionRef.current === version) setRateLoadError(errorMessage(error, "수수료율을 불러오지 못했어요.")); })
      .finally(() => { if (!ignore) setRateLoading(false); });
    return () => { ignore = true; };
  }, []);

  async function handleRateSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setRateFormError(null);
    setRateFormSuccess(null);

    if (ratePercentInput.trim() === "") {
      setRateFormError("수수료율은 0~100 사이의 숫자(%)로 입력해 주세요.");
      return;
    }
    const percent = Number(ratePercentInput);
    if (!Number.isFinite(percent) || percent < 0 || percent > 100) {
      setRateFormError("수수료율은 0~100 사이의 숫자(%)로 입력해 주세요.");
      return;
    }

    // 저장 요청이 응답을 기다리는 동안 사용자가 rateScope를 바꿀 수 있어서, 제출 시점 값을
    // 로컬 변수로 캡처해두고 응답 처리에는 이 값만 쓴다(await 뒤에 상태를 다시 읽지 않는다).
    const submittedScope = rateScope;
    let fairId: number | undefined;
    if (submittedScope === "FAIR") {
      const parsed = Number(rateFairIdInput);
      if (!Number.isInteger(parsed) || parsed <= 0) {
        setRateFormError("행사별 수수료율은 행사 ID를 1 이상의 숫자로 입력해 주세요.");
        return;
      }
      fairId = parsed;
    }

    setRateSubmitting(true);
    try {
      const result = await setCommissionRate({ scope: submittedScope, fairId, rate: percent / 100 });
      setRateFormSuccess(
        submittedScope === "GLOBAL"
          ? `전역 기본 수수료율이 ${formatRatePercent(result.rate)}로 설정됐어요.`
          : `행사 #${result.fairId} 전용 수수료율이 ${formatRatePercent(result.rate)}로 설정됐어요.`,
      );
      if (submittedScope === "GLOBAL") {
        globalRateVersionRef.current += 1; // 아직 안 끝난 초기 조회 응답을 무효화한다.
        setGlobalRate(result);
        setRateLoadError(null); // 이전 초기 조회 실패 메시지가 남아있었다면 같이 지운다.
      }
      setRatePercentInput("");
      setRateFairIdInput("");
    } catch (error) {
      setRateFormError(errorMessage(error, "수수료율 설정에 실패했어요."));
    } finally {
      setRateSubmitting(false);
    }
  }

  // ── 정산 조회/계산 ──
  const [fairIdInput, setFairIdInput] = useState("");
  const [loadedFairId, setLoadedFairId] = useState<number | null>(null);
  const [settlements, setSettlements] = useState<SettlementResponse[] | null>(null);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);

  const [calcBusinessIdInput, setCalcBusinessIdInput] = useState("");
  const [calcSubmitting, setCalcSubmitting] = useState(false);
  const [calcError, setCalcError] = useState<string | null>(null);

  const [actionError, setActionError] = useState<string | null>(null);
  // 행마다 독립적으로 처리 중인지 추적한다(값 하나만 저장하면 동시에 다른 행을 처리할 때
  // 서로 상태를 덮어써서 버튼이 실제 완료 전에 풀리거나 중복 요청이 나갈 수 있다).
  const [actioningSettlementIds, setActioningSettlementIds] = useState<Set<number>>(new Set());

  function markActioning(settlementId: number, actioning: boolean) {
    setActioningSettlementIds((current) => {
      const next = new Set(current);
      if (actioning) next.add(settlementId); else next.delete(settlementId);
      return next;
    });
  }

  // 목록 조회·정산 계산이 서로 다른 행사 컨텍스트에서 겹쳐 실행될 수 있어서(예: A 조회 중
  // B로 전환), 먼저 시작했지만 나중에 끝난 요청이 지금 보고 있는 행사 목록에 잘못
  // 반영되지 않도록 버전을 추적한다.
  const fairContextVersionRef = useRef(0);

  async function loadSettlements(fairId: number) {
    const version = ++fairContextVersionRef.current;
    setListLoading(true);
    setListError(null);
    setActionError(null);
    setCalcError(null);
    try {
      const data = await getSettlementsByFair(fairId);
      if (fairContextVersionRef.current !== version) return;
      setSettlements(data);
      setLoadedFairId(fairId);
    } catch (error) {
      if (fairContextVersionRef.current !== version) return;
      setSettlements(null);
      setListError(errorMessage(error, "정산 목록을 불러오지 못했어요."));
    } finally {
      if (fairContextVersionRef.current === version) setListLoading(false);
    }
  }

  function handleLoadSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(fairIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setListError("행사 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    loadSettlements(parsed);
  }

  async function handleCalcSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (loadedFairId === null) return;

    const parsed = Number(calcBusinessIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setCalcError("업체 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    const version = fairContextVersionRef.current;
    setCalcSubmitting(true);
    setCalcError(null);
    try {
      const created = await calculateSettlement(loadedFairId, parsed);
      if (fairContextVersionRef.current !== version) return;
      setSettlements((current) => (current ? [...current, created] : [created]));
      setCalcBusinessIdInput("");
    } catch (error) {
      if (fairContextVersionRef.current !== version) return;
      setCalcError(errorMessage(error, "정산 계산에 실패했어요."));
    } finally {
      if (fairContextVersionRef.current === version) setCalcSubmitting(false);
    }
  }

  function updateSettlementInList(updated: SettlementResponse) {
    setSettlements((current) =>
      current ? current.map((row) => (row.settlementId === updated.settlementId ? updated : row)) : current,
    );
  }

  async function handleConfirm(settlement: SettlementResponse) {
    // 확인 대화상자를 띄워둔 채로도 사용자가 다른 행사로 전환할 수 있어서, 대화상자 대기
    // 전후와 API 응답 처리 전에 행사 컨텍스트가 그대로인지 확인한다.
    const version = fairContextVersionRef.current;
    const ok = await confirm({
      title: "정산 확정",
      description: `업체 #${settlement.businessId} 정산(${formatWon(settlement.netAmount)})을 확정할까요?\n확정 이후에는 금액을 되돌릴 수 없어요.`,
      confirmLabel: "확정",
    });
    if (!ok || fairContextVersionRef.current !== version) return;

    setActionError(null);
    markActioning(settlement.settlementId, true);
    try {
      const result = await confirmSettlement(settlement.settlementId);
      if (fairContextVersionRef.current !== version) return;
      updateSettlementInList(result);
    } catch (error) {
      if (fairContextVersionRef.current !== version) return;
      if (error instanceof ApiError && error.code === "ST005") {
        setActionError(`정산 #${settlement.settlementId}: 계산 이후 환불이 반영되지 않았어요. 먼저 "재계산"을 눌러 주세요.`);
      } else {
        setActionError(errorMessage(error, "정산 확정에 실패했어요."));
      }
    } finally {
      markActioning(settlement.settlementId, false);
    }
  }

  async function handleRecalculate(settlement: SettlementResponse) {
    const version = fairContextVersionRef.current;
    setActionError(null);
    markActioning(settlement.settlementId, true);
    try {
      const result = await recalculateSettlement(settlement.settlementId);
      if (fairContextVersionRef.current !== version) return;
      updateSettlementInList(result);
    } catch (error) {
      if (fairContextVersionRef.current !== version) return;
      setActionError(errorMessage(error, "정산 재계산에 실패했어요."));
    } finally {
      markActioning(settlement.settlementId, false);
    }
  }

  // ── 업체별 정산 상세 단건 조회 ──
  const [vendorFairIdInput, setVendorFairIdInput] = useState("");
  const [vendorBusinessIdInput, setVendorBusinessIdInput] = useState("");
  const [vendorSettlement, setVendorSettlement] = useState<SettlementResponse | null>(null);
  const [vendorLookupLoading, setVendorLookupLoading] = useState(false);
  const [vendorLookupError, setVendorLookupError] = useState<string | null>(null);

  async function handleVendorLookupSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fairId = Number(vendorFairIdInput);
    const businessId = Number(vendorBusinessIdInput);
    if (!Number.isInteger(fairId) || fairId <= 0 || !Number.isInteger(businessId) || businessId <= 0) {
      setVendorLookupError("행사 ID와 업체 ID 모두 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    setVendorLookupLoading(true);
    setVendorLookupError(null);
    try {
      const data = await getVendorSettlement(fairId, businessId);
      setVendorSettlement(data);
    } catch (error) {
      setVendorSettlement(null);
      setVendorLookupError(errorMessage(error, "정산 상세를 불러오지 못했어요."));
    } finally {
      setVendorLookupLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="전체 운영" title="정산·수수료율" description="참가업체 정산을 계산·확정하고 플랫폼 수수료율을 관리해요." />

      <section className="mb-10">
        <SectionHeader title="수수료율 관리" description="모든 행사에 공통으로 적용되는 기본 수수료율을 설정하거나, 특정 행사에만 다른 수수료율을 따로 지정할 수 있어요. 변경한 수수료율은 설정 이후에 새로 계산되는 정산부터 반영돼요." />

        <Card className="mb-4 p-6">
          <h3 className="mb-2 text-sm font-extrabold text-muted">현재 전체 기본 수수료율</h3>
          {rateLoading && <p className="text-sm text-muted">불러오는 중이에요...</p>}
          {!rateLoading && rateLoadError && (
            <p className="text-sm text-primary-strong">{rateLoadError}</p>
          )}
          {!rateLoading && !rateLoadError && globalRate && (
            <div>
              <p className="text-2xl font-extrabold text-ink">{formatRatePercent(globalRate.rate)}</p>
              <p className="mt-1 text-sm text-muted">
                {globalRate.updatedAt && globalRate.updatedByUserId !== null
                  ? `${formatDateTime(globalRate.updatedAt)} · 관리자 #${globalRate.updatedByUserId} 설정`
                  : "아직 설정된 적 없어서 기본값이 적용되고 있어요."}
              </p>
            </div>
          )}
        </Card>

        <Card className="p-6">
          <h3 className="mb-4 text-sm font-extrabold text-muted">새 수수료율 설정</h3>
          <form onSubmit={handleRateSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="w-full sm:w-40">
              <label htmlFor="rate-scope" className="mb-1.5 block text-sm font-bold text-ink">적용 범위</label>
              <Select id="rate-scope" value={rateScope} disabled={rateSubmitting} onChange={(event) => setRateScope(event.target.value as CommissionRateScope)}>
                <option value="GLOBAL">기본 수수료율</option>
                <option value="FAIR">특정 행사</option>
              </Select>
            </div>
            {rateScope === "FAIR" && (
              <div className="w-full sm:w-40">
                <label htmlFor="rate-fair-id" className="mb-1.5 block text-sm font-bold text-ink">행사 ID</label>
                <Input id="rate-fair-id" className="input-no-spinner" type="number" min={1} value={rateFairIdInput} disabled={rateSubmitting} onChange={(event) => setRateFairIdInput(event.target.value)} placeholder="예: test1" />
              </div>
            )}
            <div className="w-full sm:w-32">
              <label htmlFor="rate-percent" className="mb-1.5 block text-sm font-bold text-ink">수수료율(%)</label>
              <Input id="rate-percent" type="number" min={0} max={100} step={0.01} value={ratePercentInput} disabled={rateSubmitting} onChange={(event) => setRatePercentInput(event.target.value)} placeholder="예: 5" />
            </div>
            <Button type="submit" disabled={rateSubmitting}>{rateSubmitting ? "저장 중..." : "저장"}</Button>
          </form>

          {rateFormError && (
            <p className="mt-3 flex items-start gap-2 text-sm text-primary-strong">
              <AlertCircle size={16} className="mt-0.5 shrink-0" />
              {rateFormError}
            </p>
          )}
          {rateFormSuccess && <p className="mt-3 text-sm text-leaf">{rateFormSuccess}</p>}
        </Card>
      </section>

      <section>
        <SectionHeader title="정산 조회·계산" description="행사 ID로 정산 목록을 조회하고, 업체별 정산을 계산·확정해요." />

        <form onSubmit={handleLoadSubmit} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label htmlFor="fair-id" className="mb-1.5 block text-sm font-bold text-ink">행사 ID</label>
            <Input id="fair-id" className="input-no-spinner" type="number" min={1} value={fairIdInput} onChange={(event) => setFairIdInput(event.target.value)} placeholder="예: test1" />
          </div>
          <Button type="submit" variant="outline" disabled={listLoading}>
            <Search size={16} />
            조회
          </Button>
        </form>

        {listError && (
          <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <p>{listError}</p>
          </div>
        )}

        {!loadedFairId && !listLoading && (
          <EmptyState title="행사 ID를 먼저 조회해 주세요" description="정산을 확인·계산할 행사 ID를 입력하고 조회하면 목록이 표시돼요." />
        )}

        {listLoading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

        {loadedFairId !== null && !listLoading && settlements && (
          <div className="space-y-4">
            <Card className="p-5">
              <h3 className="mb-3 text-sm font-extrabold text-muted">행사 #{loadedFairId} 새 정산 계산</h3>
              <form onSubmit={handleCalcSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
                <div className="flex-1">
                  <label htmlFor="calc-business-id" className="mb-1.5 block text-sm font-bold text-ink">업체 ID</label>
                  <Input id="calc-business-id" className="input-no-spinner" type="number" min={1} value={calcBusinessIdInput} onChange={(event) => setCalcBusinessIdInput(event.target.value)} placeholder="예: 20" />
                </div>
                <Button type="submit" variant="outline" disabled={calcSubmitting}>
                  <Calculator size={16} />
                  {calcSubmitting ? "계산 중..." : "정산 계산"}
                </Button>
              </form>
              {calcError && <p className="mt-3 text-sm text-primary-strong">{calcError}</p>}
            </Card>

            {actionError && (
              <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
                <AlertCircle size={18} className="mt-0.5 shrink-0" />
                <p>{actionError}</p>
              </div>
            )}

            {settlements.length === 0 ? (
              <EmptyState title="계산된 정산이 없어요" description="위 폼에서 업체 ID를 입력해 정산을 계산해 보세요." />
            ) : (
              <Table>
                <thead>
                  <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                    <th className="px-4 py-3">업체</th>
                    <th className="px-4 py-3">총 참가비</th>
                    <th className="px-4 py-3">환불액</th>
                    <th className="px-4 py-3">수수료</th>
                    <th className="px-4 py-3">지급액</th>
                    <th className="px-4 py-3">상태</th>
                    <th className="px-4 py-3">동작</th>
                  </tr>
                </thead>
                <tbody>
                  {settlements.map((row) => {
                    const isActioning = actioningSettlementIds.has(row.settlementId);
                    return (
                      <tr key={row.settlementId} className="border-b border-line last:border-b-0">
                        <td className="whitespace-nowrap px-4 py-3 text-ink">#{row.businessId}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-ink">{formatWon(row.grossAmount)}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-ink">{formatWon(row.refundAmount)}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-ink">
                          {formatWon(row.commissionAmount)} <span className="text-muted">({formatRatePercent(row.commissionRate)})</span>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatWon(row.netAmount)}</td>
                        <td className="whitespace-nowrap px-4 py-3">
                          <Badge tone={statusTones[row.status]}>{statusLabels[row.status]}</Badge>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">
                          {row.status === "PENDING" ? (
                            <div className="flex gap-2">
                              <Button variant="outline" onClick={() => handleRecalculate(row)} disabled={isActioning}>
                                <RefreshCw size={14} />
                                재계산
                              </Button>
                              <Button onClick={() => handleConfirm(row)} disabled={isActioning}>
                                <Check size={14} />
                                {isActioning ? "처리 중..." : "확정"}
                              </Button>
                            </div>
                          ) : (
                            <span className="text-sm text-muted">
                              {row.confirmedAt ? `${formatDateTime(row.confirmedAt)} 확정` : "-"}
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
            )}
          </div>
        )}
      </section>

      <section className="mt-10">
        <SectionHeader title="업체별 정산 상세 조회" description="행사 ID·업체 ID 조합으로 그 업체의 정산 단건을 바로 조회해요(참가업체 본인 조회용 API)." />

        <form onSubmit={handleVendorLookupSubmit} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label htmlFor="vendor-fair-id" className="mb-1.5 block text-sm font-bold text-ink">행사 ID</label>
            <Input id="vendor-fair-id" className="input-no-spinner" type="number" min={1} value={vendorFairIdInput} onChange={(event) => setVendorFairIdInput(event.target.value)} placeholder="예: test1" />
          </div>
          <div className="flex-1">
            <label htmlFor="vendor-business-id" className="mb-1.5 block text-sm font-bold text-ink">업체 ID</label>
            <Input id="vendor-business-id" className="input-no-spinner" type="number" min={1} value={vendorBusinessIdInput} onChange={(event) => setVendorBusinessIdInput(event.target.value)} placeholder="예: test1" />
          </div>
          <Button type="submit" variant="outline" disabled={vendorLookupLoading}>
            <Search size={16} />
            조회
          </Button>
        </form>

        {vendorLookupError && (
          <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <p>{vendorLookupError}</p>
          </div>
        )}

        {vendorLookupLoading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

        {vendorSettlement && !vendorLookupLoading && (
          <Card className="space-y-4 p-6">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-lg font-extrabold text-ink">정산 #{vendorSettlement.settlementId} · 업체 #{vendorSettlement.businessId}</h3>
              <Badge tone={statusTones[vendorSettlement.status]}>{statusLabels[vendorSettlement.status]}</Badge>
            </div>
            <dl className="grid gap-4 sm:grid-cols-3">
              <div>
                <dt className="text-xs font-bold text-muted">총 참가비</dt>
                <dd className="mt-1 text-sm text-ink">{formatWon(vendorSettlement.grossAmount)}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold text-muted">환불액</dt>
                <dd className="mt-1 text-sm text-ink">{formatWon(vendorSettlement.refundAmount)}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold text-muted">수수료</dt>
                <dd className="mt-1 text-sm text-ink">{formatWon(vendorSettlement.commissionAmount)} ({formatRatePercent(vendorSettlement.commissionRate)})</dd>
              </div>
              <div>
                <dt className="text-xs font-bold text-muted">지급액</dt>
                <dd className="mt-1 text-sm font-bold text-ink">{formatWon(vendorSettlement.netAmount)}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold text-muted">확정 시각</dt>
                <dd className="mt-1 text-sm text-ink">{vendorSettlement.confirmedAt ? formatDateTime(vendorSettlement.confirmedAt) : "-"}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold text-muted">확정한 관리자</dt>
                <dd className="mt-1 text-sm text-ink">{vendorSettlement.confirmedByUserId !== null ? `#${vendorSettlement.confirmedByUserId}` : "-"}</dd>
              </div>
            </dl>
          </Card>
        )}
      </section>

      {confirmDialog}
    </div>
  );
}
