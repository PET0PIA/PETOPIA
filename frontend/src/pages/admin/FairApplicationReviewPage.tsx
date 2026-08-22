import { AlertCircle, Check, Globe, X } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  getFairApplication,
  getFairApplications,
  publishFair,
  reviewFairApplication,
  type FairApplicationDetail,
  type FairApplicationSummary,
} from "../../api/fair";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Textarea } from "../../components/ui/Textarea";
import { useConfirm } from "../../components/ui/useConfirm";

const statusLabels: Record<string, string> = {
  RECEIVED: "심사 대기",
  REJECTED: "반려됨",
  EXPIRED: "만료됨",
  PAYMENT_PENDING: "개설비 결제 대기",
  PREPARING: "행사 준비중",
  IN_PROGRESS: "진행 중",
  ENDED: "종료",
};

// 공개(publish)는 개설비 결제가 끝난 이후(PREPARING~IN_PROGRESS) 상태에서만 가능하다
// (FairService.PUBLISHABLE_STATUSES와 동일한 목록을 FE에서도 미리 확인해 불필요한 요청을 막는다 -
// 최종 판단은 항상 백엔드가 한다). PAYMENT_PENDING(개설비 결제 대기 중)은 제외 - 개설비를
// 아직 내지 않은 행사를 공개해버리면 이후 결제 기한이 지나 EXPIRED로 자동 만료될 때 이미
// 들어온 예약을 정리해야 하는 문제가 생긴다.
const PUBLISHABLE_STATUSES = new Set(["PREPARING", "IN_PROGRESS"]);

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR");
}

function formatDate(value: string | null) {
  return value ?? "-";
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-bold text-muted">{label}</dt>
      <dd className="mt-1 text-sm text-ink">{value}</dd>
    </div>
  );
}

export function FairApplicationReviewPage() {
  const { confirm, confirmDialog } = useConfirm();

  const [queue, setQueue] = useState<FairApplicationSummary[]>([]);
  const [queueLoading, setQueueLoading] = useState(true);
  const [queueError, setQueueError] = useState<string | null>(null);

  // 행사 ID를 직접 타이핑하지 않고 이름으로 찾도록, 전체 신청서 목록(상태 무관)을 한 번
  // 받아와서 검색용 드롭다운을 채운다 - 이미 심사된(REJECTED/PREPARING 등) 행사도 포함된다.
  const [allApplications, setAllApplications] = useState<FairApplicationSummary[]>([]);
  const [allApplicationsError, setAllApplicationsError] = useState<string | null>(null);

  const [selectedFairId, setSelectedFairId] = useState<number | null>(null);
  const [detail, setDetail] = useState<FairApplicationDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [openingFeeAmountInput, setOpeningFeeAmountInput] = useState("");
  // 비워두면 서버 기본값(7일)을 쓴다 - 필수 입력이 아니다.
  const [paymentDueDaysInput, setPaymentDueDaysInput] = useState("");

  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reviewing, setReviewing] = useState(false);

  const [publishing, setPublishing] = useState(false);
  const [publishError, setPublishError] = useState<string | null>(null);

  // 행사를 빠르게 여러 번 바꿔 선택하면 먼저 보낸 요청의 응답이 나중에 도착할 수 있다 -
  // 그 응답으로 화면이 덮어써지면 지금 선택과 다른 행사의 상세가 보이게 된다. 매 요청마다
  // 번호를 매겨서, 응답이 왔을 때 그게 여전히 "지금 선택"에 대한 요청인지 확인한다.
  const detailRequestIdRef = useRef(0);

  // 승인/반려 후 큐를 새로고침할 때 재사용한다(그때는 이미 마운트된 상태라 setQueueLoading(true)를
  // 먼저 불러 로딩 표시를 다시 보여줘도 된다). 최초 마운트 시 큐를 받아오는 아래 useEffect는
  // queueLoading의 초기값이 이미 true라 이 함수 대신 별도로 fetch만 한다(react-hooks/set-state-in-effect
  // 회피 - effect 안에서 setState를 동기 호출하는 함수를 부르면 안 된다).
  async function loadQueue() {
    setQueueLoading(true);
    setQueueError(null);
    try {
      const data = await getFairApplications("RECEIVED");
      setQueue(data);
    } catch (error) {
      setQueue([]);
      setQueueError(error instanceof ApiError ? error.message : "심사 대기 목록을 불러오지 못했어요.");
    } finally {
      setQueueLoading(false);
    }
  }

  useEffect(() => {
    let alive = true;
    getFairApplications("RECEIVED")
      .then((data) => {
        if (alive) setQueue(data);
      })
      .catch((error: unknown) => {
        if (!alive) return;
        setQueue([]);
        setQueueError(error instanceof ApiError ? error.message : "심사 대기 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setQueueLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    let alive = true;
    getFairApplications()
      .then((data) => {
        if (alive) setAllApplications([...data].sort((a, b) => a.name.localeCompare(b.name, "ko")));
      })
      .catch((error: unknown) => {
        if (alive) setAllApplicationsError(error instanceof ApiError ? error.message : "행사 목록을 불러오지 못했어요.");
      });
    return () => {
      alive = false;
    };
  }, []);

  async function loadDetail(fairId: number) {
    const requestId = ++detailRequestIdRef.current;
    setLoading(true);
    setLoadError(null);
    setReviewError(null);
    setPublishError(null);
    try {
      const data = await getFairApplication(fairId);
      if (requestId !== detailRequestIdRef.current) return; // 그 사이 다른 행사를 선택했으면 이 응답은 버린다
      setDetail(data);
    } catch (error) {
      if (requestId !== detailRequestIdRef.current) return;
      setDetail(null);
      setLoadError(error instanceof ApiError ? error.message : "신청서를 불러오지 못했어요.");
    } finally {
      if (requestId === detailRequestIdRef.current) setLoading(false);
    }
  }

  function openFair(fairId: number) {
    setSelectedFairId(fairId);
    void loadDetail(fairId);
  }

  function handleSelectFair(value: string) {
    if (value === "") {
      detailRequestIdRef.current += 1; // 진행 중이던 조회가 있었다면 응답이 와도 버려지도록 무효화한다
      setSelectedFairId(null);
      setDetail(null);
      return;
    }
    openFair(Number(value));
  }

  // 승인/반려 직후 검색 드롭다운의 상태 라벨도 같이 갱신한다 - 안 하면 방금 승인한 행사가
  // 드롭다운에는 여전히 "심사 대기"로 남아 보인다.
  function patchAllApplicationsStatus(fairId: number, status: string) {
    setAllApplications((previous) => previous.map((item) => (item.fairId === fairId ? { ...item, status } : item)));
  }

  function openApproveDialog() {
    setReviewError(null);
    setOpeningFeeAmountInput("");
    setPaymentDueDaysInput("");
    setApproveDialogOpen(true);
  }

  function openRejectDialog() {
    setReviewError(null);
    setRejectReason("");
    setRejectDialogOpen(true);
  }

  // 승인은 되돌릴 수 없는 부수효과(행사 관리자 배정, 안내 메일)를 동반하므로, 금액을 입력받은
  // 뒤 곧바로 API를 부르지 않고 "정말 승인할까요?" 확인 모달을 한 번 더 거친다 - 잘못 눌러도
  // 취소할 기회를 준다.
  async function handleApproveSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!detail) return;
    const amount = Number(openingFeeAmountInput);
    if (!Number.isInteger(amount) || amount <= 0) {
      setReviewError("개설비 금액을 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    // 비워두면 서버 기본값(7일)을 쓴다 - undefined로 보내면 백엔드가 그렇게 처리한다.
    let dueDays: number | undefined;
    if (paymentDueDaysInput.trim() !== "") {
      dueDays = Number(paymentDueDaysInput);
      if (!Number.isInteger(dueDays) || dueDays <= 0 || dueDays > 365) {
        setReviewError("결제 기한은 1일 이상 365일 이하로 입력해 주세요.");
        return;
      }
    }

    const dueDaysLabel = dueDays != null ? `${dueDays}일` : "기본 기한(7일)";
    const proceed = await confirm({
      title: "행사를 승인할까요?",
      description: `개설비 ${amount.toLocaleString("ko-KR")}원, 결제 기한 ${dueDaysLabel}으로 승인해요. 승인하면 신청자의 기존 계정에 행사 관리 권한이 추가되고 결제 안내 메일이 발송돼요.`,
      confirmLabel: "승인",
      danger: false,
    });
    if (!proceed) return;

    setReviewing(true);
    setReviewError(null);
    try {
      const result = await reviewFairApplication(detail.fairId, { decision: "APPROVE", openingFeeAmount: amount, paymentDueDays: dueDays });
      setDetail({
        ...detail,
        status: result.status,
        reviewedAt: result.reviewedAt,
        openingFeeAmount: result.openingFeeAmount,
        paymentDueAt: result.paymentDueAt,
        rejectReason: result.rejectReason,
      });
      setApproveDialogOpen(false);
      setOpeningFeeAmountInput("");
      setPaymentDueDaysInput("");
      patchAllApplicationsStatus(detail.fairId, result.status);
      void loadQueue();
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "승인 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  async function handleReject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!detail) return;
    if (rejectReason.trim() === "") {
      setReviewError("반려 사유를 입력해 주세요.");
      return;
    }

    const proceed = await confirm({
      title: "신청을 반려할까요?",
      description: "반려하면 신청자에게 반려 사유가 담긴 안내 메일이 발송돼요.",
      confirmLabel: "반려",
    });
    if (!proceed) return;

    setReviewing(true);
    setReviewError(null);
    try {
      const result = await reviewFairApplication(detail.fairId, { decision: "REJECT", rejectReason: rejectReason.trim() });
      setDetail({
        ...detail,
        status: result.status,
        reviewedAt: result.reviewedAt,
        openingFeeAmount: result.openingFeeAmount,
        paymentDueAt: result.paymentDueAt,
        rejectReason: result.rejectReason,
      });
      setRejectDialogOpen(false);
      setRejectReason("");
      patchAllApplicationsStatus(detail.fairId, result.status);
      void loadQueue();
    } catch (error) {
      setReviewError(error instanceof ApiError ? error.message : "반려 처리에 실패했어요.");
    } finally {
      setReviewing(false);
    }
  }

  async function handlePublish() {
    if (!detail) return;
    const proceed = await confirm({
      title: "행사를 공개할까요?",
      description: "공개하면 즉시 티켓 예매 화면에 노출되고 관람객 예약을 받을 수 있어요.",
      confirmLabel: "공개",
    });
    if (!proceed) return;

    setPublishing(true);
    setPublishError(null);
    try {
      const result = await publishFair(detail.fairId);
      setDetail({ ...detail, publishedAt: result.publishedAt });
    } catch (error) {
      setPublishError(error instanceof ApiError ? error.message : "공개 처리에 실패했어요.");
    } finally {
      setPublishing(false);
    }
  }

  const isPendingReview = detail?.status === "RECEIVED";
  const canPublish = detail !== null && !detail.canceledAt && !detail.publishedAt && PUBLISHABLE_STATUSES.has(detail.status);

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader eyebrow="전체 운영" title="행사 등록 신청 검토" description="신청서를 확인하고 승인 또는 반려해요." />

      <div className="mb-6">
        <h2 className="mb-3 text-sm font-extrabold text-muted">심사 대기 중인 신청서</h2>
        {queueLoading ? (
          <div className="surface grid min-h-24 place-items-center text-sm text-muted">불러오는 중이에요...</div>
        ) : queueError ? (
          <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <p>{queueError}</p>
          </div>
        ) : queue.length === 0 ? (
          <EmptyState title="심사 대기 중인 신청서가 없어요." description="새 신청이 들어오면 이곳에 표시돼요." />
        ) : (
          <Table>
            <thead>
              <tr className="border-b border-line text-xs font-bold text-muted">
                <th className="px-4 py-3">행사명</th>
                <th className="px-4 py-3">신청일</th>
                <th className="px-4 py-3" aria-label="심사" />
              </tr>
            </thead>
            <tbody>
              {queue.map((application) => (
                <tr key={application.fairId} className="border-b border-line last:border-0 hover:bg-page">
                  <td className="px-4 py-3 font-bold text-ink">{application.name}</td>
                  <td className="px-4 py-3 text-muted">{formatDateTime(application.createdAt)}</td>
                  <td className="px-4 py-3 text-right">
                    <Button variant="outline" onClick={() => openFair(application.fairId)}>
                      심사하기
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </div>

      <div className="surface mb-6 p-5">
        <label htmlFor="fairSelect" className="mb-1.5 block text-sm font-bold text-ink">검토할 행사(신청서)</label>
        <Select
          id="fairSelect"
          value={selectedFairId ?? ""}
          onChange={(event) => handleSelectFair(event.target.value)}
        >
          <option value="">행사명으로 찾기...</option>
          {allApplications.map((application) => (
            <option key={application.fairId} value={application.fairId}>
              {application.name} ({statusLabels[application.status] ?? application.status})
            </option>
          ))}
        </Select>
        {allApplicationsError && <p className="mt-1.5 text-xs font-bold text-primary-strong">{allApplicationsError}</p>}
      </div>

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {!detail && !loading && (
        <EmptyState title="행사를 먼저 선택해 주세요." description="위 목록에서 검토할 행사를 선택하면 상세 내용이 표시돼요." />
      )}

      {loading && <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {detail && !loading && (
        <div className="space-y-6">
          <Card className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="mb-2 flex items-center gap-2">
                <h2 className="text-lg font-extrabold">{detail.name}</h2>
                <Badge tone={isPendingReview ? "sun" : detail.status === "REJECTED" ? "neutral" : "leaf"}>{statusLabels[detail.status] ?? detail.status}</Badge>
              </div>
              <p className="text-sm text-muted">신청서 #{detail.fairId} · 신청자 #{detail.applicantUserId}</p>
              {detail.rejectReason && <p className="mt-2 text-sm text-primary-strong">반려 사유: {detail.rejectReason}</p>}
              {detail.openingFeeAmount !== null && <p className="mt-2 text-sm text-muted">개설비: {detail.openingFeeAmount.toLocaleString("ko-KR")}원</p>}
              {detail.paymentDueAt && <p className="mt-2 text-sm text-muted">개설비 결제 기한: {formatDateTime(detail.paymentDueAt)}</p>}
              {detail.publishedAt ? (
                <p className="mt-2 flex items-center gap-1.5 text-sm font-bold text-ink">
                  <Globe size={14} />
                  {formatDateTime(detail.publishedAt)}에 공개됨 (티켓 예매 화면에 노출 중)
                </p>
              ) : detail.canceledAt ? (
                <p className="mt-2 text-sm text-muted">취소된 행사라 공개할 수 없어요.</p>
              ) : null}
            </div>
            <div className="flex shrink-0 gap-2">
              {isPendingReview && (
                <>
                  <Button variant="outline" onClick={openRejectDialog} disabled={reviewing}><X size={16} />반려</Button>
                  <Button onClick={openApproveDialog} disabled={reviewing}><Check size={16} />승인</Button>
                </>
              )}
              {canPublish && (
                <Button onClick={handlePublish} disabled={publishing}>
                  <Globe size={16} />
                  {publishing ? "공개 처리 중..." : "공개하기"}
                </Button>
              )}
            </div>
          </Card>

          {reviewError && (
            <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <p>{reviewError}</p>
            </div>
          )}

          {publishError && (
            <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <p>{publishError}</p>
            </div>
          )}

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">기본 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-2">
              <Field label="카테고리" value={detail.category ?? "-"} />
              <div>
                <dt className="text-xs font-bold text-muted">포스터 이미지</dt>
                <dd className="mt-1 text-sm text-ink">
                  {detail.posterImageUrl ? (
                    <a href={detail.posterImageUrl} target="_blank" rel="noopener noreferrer" className="inline-block">
                      <img
                        src={detail.posterImageUrl}
                        alt="행사 포스터"
                        className="h-24 w-auto rounded-card border border-line object-cover transition hover:opacity-80"
                      />
                    </a>
                  ) : (
                    "-"
                  )}
                </dd>
              </div>
              <Field label="행사 소개" value={detail.description ?? "-"} />
              <Field label="유의사항" value={detail.noticeText ?? "-"} />
              <Field label="반려동물 동반" value={detail.petAllowed ? "동반 가능" : "동반 금지"} />
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">장소</h3>
            <dl className="grid gap-4 sm:grid-cols-2">
              <Field label="장소명" value={detail.placeName ?? "-"} />
              <Field label="실내/실외" value={detail.indoorOutdoor === "INDOOR" ? "실내" : detail.indoorOutdoor === "OUTDOOR" ? "실외" : "-"} />
              <Field label="주소" value={detail.address ?? "-"} />
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">일정</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="참가업체 모집" value={`${formatDate(detail.vendorRecruitStartDate)} ~ ${formatDate(detail.vendorRecruitEndDate)}`} />
              <Field label="사전예약" value={`${formatDate(detail.reservationStartDate)} ~ ${formatDate(detail.reservationEndDate)}`} />
              <Field label="행사 운영" value={`${formatDate(detail.operationStartDate)} ~ ${formatDate(detail.operationEndDate)}`} />
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">예약 정책</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="예약금" value={detail.reservationFee !== null ? `${detail.reservationFee.toLocaleString("ko-KR")}원` : "-"} />
              <Field label="취소 가능 기한" value={detail.reservationCancelDeadlineHours !== null ? `${detail.reservationCancelDeadlineHours}시간` : "-"} />
              <Field label="변경 가능 기한" value={detail.reservationChangeDeadlineHours !== null ? `${detail.reservationChangeDeadlineHours}시간` : "-"} />
            </dl>
          </Card>

          <Card className="space-y-4 p-6">
            <h3 className="text-sm font-extrabold text-muted">담당자 정보</h3>
            <dl className="grid gap-4 sm:grid-cols-3">
              <Field label="이름" value={detail.managerName} />
              <Field label="연락처" value={detail.managerPhone ?? "-"} />
              <Field label="이메일" value={detail.managerEmail} />
            </dl>
          </Card>
        </div>
      )}

      <Dialog open={approveDialogOpen} onClose={() => setApproveDialogOpen(false)} title="신청 승인">
        <form onSubmit={handleApproveSubmit} className="space-y-4">
          <div>
            <label htmlFor="openingFeeAmountInput" className="mb-1.5 block text-sm font-bold text-ink">개설비 금액(원)<span className="ml-1 text-primary-strong">*</span></label>
            <Input
              id="openingFeeAmountInput"
              type="number"
              min={1}
              value={openingFeeAmountInput}
              onChange={(event) => setOpeningFeeAmountInput(event.target.value)}
              placeholder="예: 500000"
              required
            />
          </div>
          <div>
            <label htmlFor="paymentDueDaysInput" className="mb-1.5 block text-sm font-bold text-ink">결제 기한(일)</label>
            <Input
              id="paymentDueDaysInput"
              type="number"
              min={1}
              max={365}
              value={paymentDueDaysInput}
              onChange={(event) => setPaymentDueDaysInput(event.target.value)}
              placeholder="비워두면 기본 7일"
            />
          </div>
          {reviewError && <p className="text-sm font-bold text-primary-strong">{reviewError}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setApproveDialogOpen(false)}>취소</Button>
            <Button type="submit" disabled={reviewing}>{reviewing ? "처리 중..." : "승인"}</Button>
          </div>
        </form>
      </Dialog>

      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} title="신청 반려">
        <form onSubmit={handleReject} className="space-y-4">
          <div>
            <label htmlFor="rejectReason" className="mb-1.5 block text-sm font-bold text-ink">반려 사유<span className="ml-1 text-primary-strong">*</span></label>
            <Textarea id="rejectReason" value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} placeholder="신청자에게 안내할 반려 사유를 입력해 주세요." required />
          </div>
          {reviewError && <p className="text-sm font-bold text-primary-strong">{reviewError}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setRejectDialogOpen(false)}>취소</Button>
            <Button type="submit" disabled={reviewing}>{reviewing ? "처리 중..." : "반려 확정"}</Button>
          </div>
        </form>
      </Dialog>

      {confirmDialog}
    </div>
  );
}
