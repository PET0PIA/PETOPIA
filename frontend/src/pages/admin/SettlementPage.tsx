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
  calculateFairSettlement,
  confirmFairSettlement,
  getFairSettlement,
  recalculateFairSettlement,
  reopenFairSettlement,
  type FairSettlementResponse,
} from "../../api/fairSettlement";
import {
  getCommissionRate,
  setCommissionRate,
  type CommissionRateResponse,
  type CommissionRateScope,
} from "../../api/commissionRate";
import { getAuditLogs, type AuditLogRow } from "../../api/audit";
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

  // ── 정산 조회/계산(업체별 정산, 구) ──
  // 2026-08-22: 화면에서 이 블록 전부 제거하고 아래 "행사비 조회·정산·확정(신규)" 블록으로
  // 대체함 — "업체가 100개면 확정을 100번 해야 하냐"는 지적으로 정산 단위를 업체별에서
  // 행사별로 바꾸기로 함. 이 블록(상태·핸들러)과 뒤에 이어지는 handleCalcSubmit~toggleDetail은
  // 백엔드(settlement 도메인)까지 완전히 지우지는 않기로 한 팀 결정 때문에 코드에 그대로
  // 남겨둠 - 아무 데서도 렌더링되지 않는다.
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
      setListError("행사 ID를 입력해 주세요.");
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

  // ── 행사비 조회·정산·확정(신규, 2026-08-22) ──
  // 업체 구분 없이 행사 하나당 최종정산 1건 - 그 행사에 참가한 모든 업체의 참가비를 합쳐
  // 계산·재계산·확정·되돌리기까지 한 카드에서 처리한다(플랫폼 ↔ 행사 정산).
  const [finalFairIdInput, setFinalFairIdInput] = useState("");
  const [finalSearchedFairId, setFinalSearchedFairId] = useState<number | null>(null);
  // undefined = 아직 조회 안 함, null = 조회했지만 계산된 정산 없음, 객체 = 정산 있음.
  const [fairSettlement, setFairSettlement] = useState<FairSettlementResponse | null | undefined>(undefined);
  const [finalLoading, setFinalLoading] = useState(false);
  const [finalError, setFinalError] = useState<string | null>(null);
  const [finalCalcSubmitting, setFinalCalcSubmitting] = useState(false);
  const [finalActioning, setFinalActioning] = useState(false);
  // "상세" 클릭 시 확정 시각·확정한 관리자를 그 행 바로 아래에 펼쳐 보여준다(구 업체별
  // 정산 목록의 상세 토글과 동일한 상호작용, 2026-08-22 재구현).
  const [finalDetailExpanded, setFinalDetailExpanded] = useState(false);
  // 수수료율 변경이력(재계산 시 요율이 실제로 바뀐 것만 감사로그에 남는다, SETTLEMENT_RATE_CHANGED
  // 참고) - 상세를 펼칠 때만 조회한다(2026-08-22).
  const [rateHistory, setRateHistory] = useState<AuditLogRow[] | null>(null);
  const [rateHistoryLoading, setRateHistoryLoading] = useState(false);

  const finalFairVersionRef = useRef(0);

  useEffect(() => {
    if (!finalDetailExpanded || !fairSettlement) return;
    let alive = true;
    setRateHistoryLoading(true);
    getAuditLogs({
      targetType: "FAIR_SETTLEMENT",
      targetId: fairSettlement.fairSettlementId,
      actionType: "SETTLEMENT_RATE_CHANGED",
      size: 20,
    })
      .then((res) => {
        if (alive) setRateHistory(res.items);
      })
      .catch(() => {
        if (alive) setRateHistory(null);
      })
      .finally(() => {
        if (alive) setRateHistoryLoading(false);
      });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [finalDetailExpanded, fairSettlement?.fairSettlementId]);

  async function loadFairSettlement(fairId: number) {
    const version = ++finalFairVersionRef.current;
    setFinalLoading(true);
    setFinalError(null);
    setFinalDetailExpanded(false);
    try {
      const data = await getFairSettlement(fairId);
      if (finalFairVersionRef.current !== version) return;
      setFairSettlement(data ?? null);
      setFinalSearchedFairId(fairId);
    } catch (error) {
      if (finalFairVersionRef.current !== version) return;
      setFairSettlement(undefined);
      setFinalSearchedFairId(null);
      setFinalError(errorMessage(error, "정산을 불러오지 못했어요."));
    } finally {
      if (finalFairVersionRef.current === version) setFinalLoading(false);
    }
  }

  function handleFinalLoadSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(finalFairIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setFinalError("행사 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    loadFairSettlement(parsed);
  }

  async function handleFinalCalculate() {
    if (finalSearchedFairId === null) return;
    const version = finalFairVersionRef.current;
    setFinalCalcSubmitting(true);
    setFinalError(null);
    try {
      const created = await calculateFairSettlement(finalSearchedFairId);
      if (finalFairVersionRef.current !== version) return;
      setFairSettlement(created);
    } catch (error) {
      if (finalFairVersionRef.current !== version) return;
      setFinalError(errorMessage(error, "정산 계산에 실패했어요."));
    } finally {
      if (finalFairVersionRef.current === version) setFinalCalcSubmitting(false);
    }
  }

  async function handleFinalConfirm() {
    if (!fairSettlement) return;
    const version = finalFairVersionRef.current;
    const ok = await confirm({
      title: "정산 확정",
      description: `행사 #${fairSettlement.fairId} 정산(${formatWon(fairSettlement.netAmount)})을 확정할까요?\n확정 이후에는 금액을 되돌릴 수 없어요.`,
      confirmLabel: "확정",
    });
    if (!ok || finalFairVersionRef.current !== version) return;

    setFinalError(null);
    setFinalActioning(true);
    try {
      const result = await confirmFairSettlement(fairSettlement.fairSettlementId);
      if (finalFairVersionRef.current !== version) return;
      setFairSettlement(result);
    } catch (error) {
      if (finalFairVersionRef.current !== version) return;
      if (error instanceof ApiError && error.code === "ST005") {
        setFinalError('계산 이후 환불이 반영되지 않았어요. 먼저 "재계산"을 눌러 주세요.');
      } else {
        setFinalError(errorMessage(error, "정산 확정에 실패했어요."));
      }
    } finally {
      setFinalActioning(false);
    }
  }

  async function handleFinalRecalculate() {
    if (!fairSettlement) return;
    const version = finalFairVersionRef.current;
    setFinalError(null);
    setFinalActioning(true);
    try {
      const result = await recalculateFairSettlement(fairSettlement.fairSettlementId);
      if (finalFairVersionRef.current !== version) return;
      setFairSettlement(result);
    } catch (error) {
      if (finalFairVersionRef.current !== version) return;
      setFinalError(errorMessage(error, "정산 재계산에 실패했어요."));
    } finally {
      setFinalActioning(false);
    }
  }

  async function handleFinalReopen() {
    if (!fairSettlement) return;
    const version = finalFairVersionRef.current;
    const ok = await confirm({
      title: "정산 확정 되돌리기",
      description: `행사 #${fairSettlement.fairId} 정산(${formatWon(fairSettlement.netAmount)})을 확정 전 상태로 되돌릴까요?\n되돌린 뒤엔 재계산으로 최신 금액을 반영하고 다시 확정해야 해요.`,
      confirmLabel: "되돌리기",
    });
    if (!ok || finalFairVersionRef.current !== version) return;

    setFinalError(null);
    setFinalActioning(true);
    try {
      const result = await reopenFairSettlement(fairSettlement.fairSettlementId);
      if (finalFairVersionRef.current !== version) return;
      setFairSettlement(result);
    } catch (error) {
      if (finalFairVersionRef.current !== version) return;
      setFinalError(errorMessage(error, "정산 되돌리기에 실패했어요."));
    } finally {
      setFinalActioning(false);
    }
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
        <SectionHeader title="행사비 조회·정산·확정" description="행사 ID를 입력하면 그 행사의 최종정산(참가업체 전체 참가비 합산, 플랫폼↔행사)을 확인·계산·확정할 수 있어요." />

        <form onSubmit={handleFinalLoadSubmit} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
          <div className="flex-1">
            <label htmlFor="final-fair-id" className="mb-1.5 block text-sm font-bold text-ink">행사 ID</label>
            <Input id="final-fair-id" className="input-no-spinner" type="number" min={1} value={finalFairIdInput} onChange={(event) => setFinalFairIdInput(event.target.value)} placeholder="예: 1" />
          </div>
          <Button type="submit" variant="outline" disabled={finalLoading}>
            <Search size={16} />
            조회
          </Button>
        </form>

        {finalError && (
          <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <p>{finalError}</p>
          </div>
        )}

        {finalSearchedFairId === null && !finalLoading && !finalError && (
          <EmptyState title="행사 ID를 먼저 조회해 주세요" description="정산을 확인·계산할 행사 ID를 입력하고 조회하면 결과가 표시돼요." />
        )}

        {finalLoading && <div className="surface grid min-h-32 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

        {finalSearchedFairId !== null && !finalLoading && (
          <div className="space-y-4">
            {fairSettlement === null && (
              <Card className="p-5">
                <h3 className="mb-3 text-sm font-extrabold text-muted">행사 #{finalSearchedFairId} 정산 계산</h3>
                <p className="mb-3 text-sm text-muted">아직 계산된 정산이 없어요. 그 행사에 참가한 모든 업체의 완료된 참가비를 합산해서 계산해요.</p>
                <Button type="button" variant="outline" onClick={handleFinalCalculate} disabled={finalCalcSubmitting}>
                  <Calculator size={16} />
                  {finalCalcSubmitting ? "계산 중..." : "정산 계산"}
                </Button>
              </Card>
            )}

            {fairSettlement && (
              <Table>
                <thead>
                  <tr className="border-b border-line bg-page text-xs font-bold text-muted">
                    <th className="px-4 py-3">행사</th>
                    <th className="px-4 py-3">총 참가비</th>
                    <th className="px-4 py-3">수수료</th>
                    <th className="px-4 py-3">지급액</th>
                    <th className="px-4 py-3">상태</th>
                    <th className="px-4 py-3">동작</th>
                    <th className="px-4 py-3" />
                  </tr>
                </thead>
                <tbody>
                  <Fragment>
                    <tr className="border-b border-line last:border-b-0">
                      <td className="whitespace-nowrap px-4 py-3 text-ink">#{fairSettlement.fairId}</td>
                      {/* "총 참가비"는 환불 차감 전 원본 합계(grossAmount)가 아니라 환불액을 뺀
                          값으로 보여준다 - 환불액은 상세 펼치면 따로 확인 가능. */}
                      <td className="whitespace-nowrap px-4 py-3 text-ink">{formatWon(fairSettlement.grossAmount - fairSettlement.refundAmount)}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-ink">
                        {formatWon(fairSettlement.commissionAmount)} <span className="text-muted">({formatRatePercent(fairSettlement.commissionRate)})</span>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 font-bold text-ink">{formatWon(fairSettlement.netAmount)}</td>
                      <td className="whitespace-nowrap px-4 py-3">
                        <Badge tone={statusTones[fairSettlement.status]}>{statusLabels[fairSettlement.status]}</Badge>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3">
                        {fairSettlement.status === "PENDING" ? (
                          <div className="flex gap-2">
                            <Button variant="outline" onClick={handleFinalRecalculate} disabled={finalActioning}>
                              <RefreshCw size={14} />
                              재계산
                            </Button>
                            <Button onClick={handleFinalConfirm} disabled={finalActioning}>
                              <Check size={14} />
                              {finalActioning ? "처리 중..." : "확정"}
                            </Button>
                          </div>
                        ) : fairSettlement.status === "CONFIRMED" ? (
                          <div className="flex flex-col items-start gap-1.5">
                            <span className="text-sm text-muted">
                              {fairSettlement.confirmedAt ? `${formatDateTime(fairSettlement.confirmedAt)} 확정` : "-"}
                            </span>
                            <Button variant="outline" onClick={handleFinalReopen} disabled={finalActioning}>
                              <RotateCcw size={14} />
                              {finalActioning ? "처리 중..." : "되돌리기"}
                            </Button>
                          </div>
                        ) : (
                          <span className="text-sm text-muted">
                            {fairSettlement.confirmedAt ? `${formatDateTime(fairSettlement.confirmedAt)} 확정` : "-"}
                          </span>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3">
                        <button type="button" onClick={() => setFinalDetailExpanded((current) => !current)} className="flex items-center gap-1 text-sm font-bold text-primary-strong hover:underline">
                          {finalDetailExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          {finalDetailExpanded ? "접기" : "상세"}
                        </button>
                      </td>
                    </tr>
                    {finalDetailExpanded && (
                      <tr className="border-b border-line bg-page last:border-b-0">
                        <td colSpan={7} className="px-4 py-3">
                          <dl className="grid gap-4 sm:grid-cols-3">
                            <div>
                              <dt className="text-xs font-bold text-muted">환불액</dt>
                              <dd className="mt-1 text-sm text-ink">{formatWon(fairSettlement.refundAmount)}</dd>
                            </div>
                            <div>
                              <dt className="text-xs font-bold text-muted">확정 시각</dt>
                              <dd className="mt-1 text-sm text-ink">{fairSettlement.confirmedAt ? formatDateTime(fairSettlement.confirmedAt) : "-"}</dd>
                            </div>
                            <div>
                              <dt className="text-xs font-bold text-muted">확정한 관리자</dt>
                              <dd className="mt-1 text-sm text-ink">{fairSettlement.confirmedByUserId !== null ? `#${fairSettlement.confirmedByUserId}` : "-"}</dd>
                            </div>
                          </dl>
                          <div className="mt-4 border-t border-line pt-3">
                            <p className="text-xs font-bold text-muted">수수료율 변경이력</p>
                            {rateHistoryLoading ? (
                              <p className="mt-1 text-sm text-muted">불러오는 중...</p>
                            ) : !rateHistory || rateHistory.length === 0 ? (
                              <p className="mt-1 text-sm text-muted">변경 이력이 없어요.</p>
                            ) : (
                              <ul className="mt-1 space-y-1">
                                {rateHistory.map((log) => {
                                  let beforeRate: number | null = null;
                                  let afterRate: number | null = null;
                                  try {
                                    beforeRate = log.beforeValue ? JSON.parse(log.beforeValue).commissionRate : null;
                                    afterRate = log.afterValue ? JSON.parse(log.afterValue).commissionRate : null;
                                  } catch {
                                    // 파싱 실패 시 원문 없이 시각만 보여준다.
                                  }
                                  return (
                                    <li key={log.auditId} className="text-sm text-ink">
                                      {formatDateTime(log.occurredAt)} · {beforeRate !== null ? formatRatePercent(beforeRate) : "-"} →{" "}
                                      {afterRate !== null ? formatRatePercent(afterRate) : "-"}
                                    </li>
                                  );
                                })}
                              </ul>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
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
