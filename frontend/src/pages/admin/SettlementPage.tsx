import { AlertCircle, Calculator, Check, ChevronDown, ChevronUp, Download, RefreshCw, RotateCcw, Search } from "lucide-react";
import { Fragment, useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  calculateSettlement,
  confirmSettlement,
  downloadFairRevenueSummaryExcel,
  downloadSettlementsExcel,
  getFairRevenueSummaries,
  getSettlementsByFilter,
  recalculateSettlement,
  reopenSettlement,
  type FairRevenueSummaryResponse,
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

  // ── 행사별 매출 요약(티켓예매+참가비 합산, WBS 5.6) ──
  const [revenueSummaries, setRevenueSummaries] = useState<FairRevenueSummaryResponse[] | null>(null);
  const [revenueLoading, setRevenueLoading] = useState(true);
  const [revenueError, setRevenueError] = useState<string | null>(null);
  const [revenueExporting, setRevenueExporting] = useState(false);
  const [revenueExportError, setRevenueExportError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    getFairRevenueSummaries()
      .then((data) => { if (!ignore) setRevenueSummaries(data); })
      .catch((error) => { if (!ignore) setRevenueError(errorMessage(error, "행사별 매출 요약을 불러오지 못했어요.")); })
      .finally(() => { if (!ignore) setRevenueLoading(false); });
    return () => { ignore = true; };
  }, []);

  async function handleRevenueExport() {
    setRevenueExporting(true);
    setRevenueExportError(null);
    try {
      await downloadFairRevenueSummaryExcel();
    } catch (error) {
      setRevenueExportError(errorMessage(error, "엑셀 파일을 내려받지 못했어요."));
    } finally {
      setRevenueExporting(false);
    }
  }

  // ── 정산 조회/계산 ──
  // fairId·businessId 둘 다 선택적 — 하나만 채우면 그 조건 전체가, 둘 다 채우면 그 조합
  // 하나만 나온다(getSettlementsByFilter, 2026-08-21 통합검색으로 개편).
  const [fairIdInput, setFairIdInput] = useState("");
  const [businessIdInput, setBusinessIdInput] = useState("");
  const [searchedFairId, setSearchedFairId] = useState<number | null>(null);
  const [searchedBusinessId, setSearchedBusinessId] = useState<number | null>(null);
  const [settlements, setSettlements] = useState<SettlementResponse[] | null>(null);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);

  const [calcBusinessIdInput, setCalcBusinessIdInput] = useState("");
  const [calcSubmitting, setCalcSubmitting] = useState(false);
  const [calcError, setCalcError] = useState<string | null>(null);

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

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

  async function loadSettlements(fairId?: number, businessId?: number) {
    const version = ++fairContextVersionRef.current;
    setListLoading(true);
    setListError(null);
    setActionError(null);
    setCalcError(null);
    try {
      const data = await getSettlementsByFilter(fairId, businessId);
      if (fairContextVersionRef.current !== version) return;
      setSettlements(data);
      setSearchedFairId(fairId ?? null);
      setSearchedBusinessId(businessId ?? null);
    } catch (error) {
      if (fairContextVersionRef.current !== version) return;
      setSettlements(null);
      setSearchedFairId(null);
      setSearchedBusinessId(null);
      setListError(errorMessage(error, "정산 목록을 불러오지 못했어요."));
    } finally {
      if (fairContextVersionRef.current === version) setListLoading(false);
    }
  }

  async function handleExport() {
    if (searchedFairId === null) return;
    setExporting(true);
    setExportError(null);
    try {
      await downloadSettlementsExcel(searchedFairId);
    } catch (error) {
      setExportError(errorMessage(error, "엑셀 파일을 내려받지 못했어요."));
    } finally {
      setExporting(false);
    }
  }

  /** 문자열 ID 입력을 파싱한다. 빈 칸이면 undefined(필터 안 검), 잘못된 값이면 에러 메시지. */
  function parseOptionalId(raw: string, label: string): { value?: number; error?: string } {
    const trimmed = raw.trim();
    if (trimmed === "") return {};
    const parsed = Number(trimmed);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      return { error: `${label}는 1 이상의 숫자로 입력해 주세요.` };
    }
    return { value: parsed };
  }

  function handleLoadSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fair = parseOptionalId(fairIdInput, "행사 ID");
    if (fair.error) {
      setListError(fair.error);
      return;
    }
    const business = parseOptionalId(businessIdInput, "업체 ID");
    if (business.error) {
      setListError(business.error);
      return;
    }
    if (fair.value === undefined && business.value === undefined) {
      setListError("행사 ID 또는 업체 ID 중 하나는 입력해 주세요.");
      return;
    }
    loadSettlements(fair.value, business.value);
  }

  async function handleCalcSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (searchedFairId === null) return;

    const parsed = Number(calcBusinessIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setCalcError("업체 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }

    const version = fairContextVersionRef.current;
    setCalcSubmitting(true);
    setCalcError(null);
    try {
      const created = await calculateSettlement(searchedFairId, parsed);
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

  async function handleReopen(settlement: SettlementResponse) {
    const version = fairContextVersionRef.current;
    const ok = await confirm({
      title: "정산 확정 되돌리기",
      description: `업체 #${settlement.businessId} 정산(${formatWon(settlement.netAmount)})을 확정 전 상태로 되돌릴까요?\n되돌린 뒤엔 재계산으로 최신 금액을 반영하고 다시 확정해야 해요.`,
      confirmLabel: "되돌리기",
    });
    if (!ok || fairContextVersionRef.current !== version) return;

    setActionError(null);
    markActioning(settlement.settlementId, true);
    try {
      const result = await reopenSettlement(settlement.settlementId);
      if (fairContextVersionRef.current !== version) return;
      updateSettlementInList(result);
    } catch (error) {
      if (fairContextVersionRef.current !== version) return;
      setActionError(errorMessage(error, "정산 되돌리기에 실패했어요."));
    } finally {
      markActioning(settlement.settlementId, false);
    }
  }

  // "상세" 클릭 시 그 행 바로 아래에 확정 시각·확정한 관리자를 펼쳐 보여준다.
  // 목록 응답(SettlementResponse)에 이미 다 포함된 값이라 별도 API 호출은 필요 없다.
  const [expandedSettlementId, setExpandedSettlementId] = useState<number | null>(null);

  function toggleDetail(settlementId: number) {
    setExpandedSettlementId((current) => (current === settlementId ? null : settlementId));
  }

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="전체 운영" title="정산·수수료율" description="참가업체 정산을 계산·확정하고 플랫폼 수수료율을 관리해요." />

      <section className="mb-10">
        <div className="mb-4 flex items-center justify-between gap-2">
          <SectionHeader title="행사별 매출 요약" description="행사마다 티켓예매 수익과 참가비 수익을 합산해서, 지금 적용 중인 수수료율대로 행사업체 몫과 플랫폼 몫을 나눠 보여줘요." />
          <Button type="button" variant="outline" onClick={handleRevenueExport} disabled={revenueExporting || revenueLoading || !revenueSummaries?.length}>
            <Download size={16} />
            {revenueExporting ? "내보내는 중..." : "엑셀로 내보내기"}
          </Button>
        </div>
        {revenueExportError && <p className="mb-3 text-sm text-primary-strong">{revenueExportError}</p>}

        {revenueLoading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

        {!revenueLoading && revenueError && (
          <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <p>{revenueError}</p>
          </div>
        )}

        {!revenueLoading && !revenueError && revenueSummaries && revenueSummaries.length === 0 && (
          <EmptyState title="등록된 행사가 없어요" description="행사가 만들어지면 여기에 매출 요약이 표시돼요." />
        )}

        {!revenueLoading && !revenueError && revenueSummaries && revenueSummaries.length > 0 && (
          <Table>
            <thead>
              <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                <th className="px-4 py-3">행사</th>
                <th className="px-4 py-3">티켓예매 총금액</th>
                <th className="px-4 py-3">참가비용 총금액</th>
                <th className="px-4 py-3">전체금액</th>
                <th className="px-4 py-3">행사업체금액</th>
                <th className="px-4 py-3">플랫폼금액</th>
              </tr>
            </thead>
            <tbody>
              {revenueSummaries.map((row) => (
                <tr key={row.fairId} className="border-b border-line last:border-b-0">
                  <td className="whitespace-nowrap px-4 py-3 text-ink">{row.fairName} <span className="text-muted">#{row.fairId}</span></td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">{formatWon(row.ticketAmount)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">{formatWon(row.vendorFeeAmount)}</td>
                  <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatWon(row.grossAmount)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">
                    {formatWon(row.businessAmount)}
                    <span className="ml-1 text-xs text-muted">({formatRatePercent(1 - row.commissionRate)})</span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-ink">
                    {formatWon(row.platformAmount)}
                    <span className="ml-1 text-xs text-muted">({formatRatePercent(row.commissionRate)})</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </section>

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
        <SectionHeader title="정산 조회·계산" description="행사 ID·업체 ID 중 하나만 입력해도 조회돼요 - 행사 ID만 넣으면 그 행사 업체 전체, 업체 ID만 넣으면 그 업체가 참가한 모든 행사, 둘 다 넣으면 그 조합 하나만 나와요." />

        <form onSubmit={handleLoadSubmit} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label htmlFor="fair-id" className="mb-1.5 block text-sm font-bold text-ink">행사 ID</label>
            <Input id="fair-id" className="input-no-spinner" type="number" min={1} value={fairIdInput} onChange={(event) => setFairIdInput(event.target.value)} placeholder="예: 1 (비워도 돼요)" />
          </div>
          <div className="flex-1">
            <label htmlFor="search-business-id" className="mb-1.5 block text-sm font-bold text-ink">업체 ID</label>
            <Input id="search-business-id" className="input-no-spinner" type="number" min={1} value={businessIdInput} onChange={(event) => setBusinessIdInput(event.target.value)} placeholder="예: 20 (비워도 돼요)" />
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

        {settlements === null && !listLoading && !listError && (
          <EmptyState title="행사 ID 또는 업체 ID를 먼저 조회해 주세요" description="정산을 확인·계산할 행사 ID나 업체 ID를 입력하고 조회하면 목록이 표시돼요." />
        )}

        {listLoading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

        {settlements !== null && !listLoading && (
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-2">
              <p className="text-sm font-bold text-muted">
                {searchedFairId !== null && searchedBusinessId !== null
                  ? `행사 #${searchedFairId} · 업체 #${searchedBusinessId} 정산`
                  : searchedFairId !== null
                    ? `행사 #${searchedFairId} 정산 목록`
                    : `업체 #${searchedBusinessId} 정산 목록`}
              </p>
              {searchedFairId !== null && (
                <Button type="button" variant="outline" onClick={handleExport} disabled={exporting}>
                  <Download size={16} />
                  {exporting ? "내보내는 중..." : "엑셀로 내보내기"}
                </Button>
              )}
            </div>
            {exportError && <p className="text-sm text-primary-strong">{exportError}</p>}

            {searchedFairId !== null && (
              <Card className="p-5">
                <h3 className="mb-3 text-sm font-extrabold text-muted">행사 #{searchedFairId} 새 정산 계산</h3>
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
            )}

            {actionError && (
              <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
                <AlertCircle size={18} className="mt-0.5 shrink-0" />
                <p>{actionError}</p>
              </div>
            )}

            {settlements.length === 0 ? (
              <EmptyState title="조회된 정산이 없어요" description={searchedFairId !== null ? "위 폼에서 업체 ID를 입력해 정산을 계산해 보세요." : "다른 행사 ID·업체 ID로 다시 조회해 보세요."} />
            ) : (
              <Table>
                <thead>
                  <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                    <th className="px-4 py-3">행사</th>
                    <th className="px-4 py-3">업체</th>
                    <th className="px-4 py-3">총 참가비</th>
                    <th className="px-4 py-3">환불액</th>
                    <th className="px-4 py-3">수수료</th>
                    <th className="px-4 py-3">지급액</th>
                    <th className="px-4 py-3">상태</th>
                    <th className="px-4 py-3">동작</th>
                    <th className="px-4 py-3" />
                  </tr>
                </thead>
                <tbody>
                  {settlements.map((row) => {
                    const isActioning = actioningSettlementIds.has(row.settlementId);
                    const isExpanded = expandedSettlementId === row.settlementId;
                    return (
                      <Fragment key={row.settlementId}>
                        <tr className="border-b border-line last:border-b-0">
                          <td className="whitespace-nowrap px-4 py-3 text-ink">#{row.fairId}</td>
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
                            ) : row.status === "CONFIRMED" ? (
                              <div className="flex flex-col items-start gap-1.5">
                                <span className="text-sm text-muted">
                                  {row.confirmedAt ? `${formatDateTime(row.confirmedAt)} 확정` : "-"}
                                </span>
                                <Button variant="outline" onClick={() => handleReopen(row)} disabled={isActioning}>
                                  <RotateCcw size={14} />
                                  {isActioning ? "처리 중..." : "되돌리기"}
                                </Button>
                              </div>
                            ) : (
                              <span className="text-sm text-muted">
                                {row.confirmedAt ? `${formatDateTime(row.confirmedAt)} 확정` : "-"}
                              </span>
                            )}
                          </td>
                          <td className="whitespace-nowrap px-4 py-3">
                            <button type="button" onClick={() => toggleDetail(row.settlementId)} className="flex items-center gap-1 text-sm font-bold text-primary-strong hover:underline">
                              {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                              {isExpanded ? "접기" : "상세"}
                            </button>
                          </td>
                        </tr>
                        {isExpanded && (
                          <tr className="border-b border-line bg-page last:border-b-0">
                            <td colSpan={9} className="px-4 py-3">
                              <dl className="grid gap-4 sm:grid-cols-2">
                                <div>
                                  <dt className="text-xs font-bold text-muted">확정 시각</dt>
                                  <dd className="mt-1 text-sm text-ink">{row.confirmedAt ? formatDateTime(row.confirmedAt) : "-"}</dd>
                                </div>
                                <div>
                                  <dt className="text-xs font-bold text-muted">확정한 관리자</dt>
                                  <dd className="mt-1 text-sm text-ink">{row.confirmedByUserId !== null ? `#${row.confirmedByUserId}` : "-"}</dd>
                                </div>
                              </dl>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </Table>
            )}
          </div>
        )}
      </section>

      {confirmDialog}
    </div>
  );
}
