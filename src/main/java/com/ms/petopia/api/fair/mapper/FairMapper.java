package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.PublicFairListFilter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * fairs 테이블 매퍼.
 *
 * <p>XML은 {@code mapper/fair/FairMapper.xml}에 있다. 신청 조회 목록, 상태별 검색처럼
 * 특정 API 전용 쿼리는 여기에 넣지 않고 해당 API 작업에서 추가한다. 이 매퍼는 entity 단위
 * 기본 CRUD만 담당한다.
 */
@Mapper
public interface FairMapper {

    /**
     * 신청서를 저장한다. 저장 후 {@code fair.getFairId()}로 생성된 PK를 바로 조회할 수 있다.
     */
    int insert(Fair fair);

    /**
     * fairId로 단건 조회한다. 없으면 null.
     */
    Fair selectById(@Param("fairId") Long fairId);

    /**
     * 특정 신청자가 낸 모든 신청서를 최신순으로 조회한다. 마이페이지 "내 신청 현황"
     * 목록용이라 상세 컬럼(managerPhone/managerEmail 등)까지 다 끌고 오지만, 응답
     * DTO({@link com.ms.petopia.api.fair.dto.FairApplicationSummaryResponse})에서 필요한
     * 것만 추린다.
     */
    List<Fair> selectByApplicantUserId(@Param("applicantUserId") Long applicantUserId);

    /**
     * null이 아닌 필드만 갱신한다. 승인/반려/수정 등 API마다 채우는 필드가 달라서
     * 범용 update 하나로 두고, 호출부에서 필요한 필드만 세팅한 Fair를 넘기는 방식이다.
     * updated_at은 항상 현재 시각으로 갱신된다. applicant_user_id/created_at은 변경 대상이 아니다.
     */
    int update(Fair fair);

    /**
     * 신청서 심사(FairService#review) 전용 조건부 갱신. status = 'RECEIVED'일 때만 실제로
     * 갱신된다(동시성 방어) - {@code FairCancelRequestMapper#update}와 동일한 패턴. "SELECT로
     * RECEIVED 확인 후 UPDATE"는 두 검토 요청이 동시에 들어오면 둘 다 체크를 통과해버릴 수
     * 있는데, 이 조건이 있으면 먼저 커밋되는 쪽만 실제로 행을 바꾸고 나머지는 영향 행 0건으로
     * 실패한다.
     */
    int updateReviewResult(Fair fair);

    /**
     * 신청서 수정(재제출, FairService#updateApplication) 전용 조건부 갱신. status가
     * RECEIVED 또는 REJECTED일 때만 실제로 갱신된다(동시성 방어 - {@link #updateReviewResult}와
     * 동일한 패턴). status/reject_reason/reviewed_by/reviewed_at은 이 갱신이 성공하는 순간
     * 항상 RECEIVED/NULL/NULL/NULL로 되돌린다 - REJECTED였던 신청서가 수정으로 다시 심사
     * 대기열에 설 때 이전 반려 사유가 남아있으면 안 되기 때문이다.
     *
     * <p>내용 필드는 {@code fair}의 null 여부가 아니라 {@code setFields}(요청 JSON에 실제로
     * 있었던 필드명 집합)로 갱신 여부를 정한다 - null 여부만으로 판단하면 "필드를 생략함(기존
     * 값 유지)"과 "필드를 명시적으로 비움(NULL로 지움)"을 구분할 수 없다. {@code setFields}에
     * 있는 필드는 {@code fair}에 담긴 값(null이면 NULL로 지움, 아니면 그 값으로) 그대로
     * 반영하고, 없는 필드는 컬럼을 건드리지 않는다.
     */
    int updateApplication(@Param("fair") Fair fair, @Param("setFields") Set<String> setFields);

    /**
     * 취소된({@code canceled_at IS NOT NULL}) 행사 중, 아직 정리할 PENDING 예약금·참가비
     * 결제가 남아있는 것만 오래된 취소순으로 조회한다({@code FairCancelPendingPaymentService}
     * 전용). {@code FairCancelRefundTargetMapper#selectUnenumeratedCanceledFairIds}처럼 별도
     * 완료 기록 테이블을 두지 않는다 - PENDING 결제는 우리가 취소에 성공하지 못하는 한 계속
     * PENDING으로 남아 다음 호출에서 자연히 다시 걸리므로(멱등) 완료 기록이 필요 없다.
     *
     * <p>다만 "아직 남은 PENDING이 있는지"(EXISTS)는 반드시 걸러야 한다 -
     * 이 조건 없이 canceled_at DESC로만 limit를 걸면, 취소된 행사 수가 limit를 넘는 순간
     * 이미 다 정리된 최신 행사들이 매번 그 자리를 계속 차지해서 더 오래된 행사의 PENDING
     * 결제가 배치 슬롯을 영영 못 받을 수 있다.
     */
    List<Long> selectCanceledFairIds(@Param("limit") int limit);

    /**
     * 공개된(published_at IS NOT NULL) 행사 중 취소되지 않은 것만 {@code filter}에 맞게
     * 골라 반환한다({@code FairService#listPublicFairs} 전용, 인증 없이 누구나 볼 수 있는
     * 목록이라 PII/심사 필드는 아예 SELECT하지 않는다).
     *
     * <p>UPCOMING/PAST는 operation_end_date를 {@code today}와 비교해서 가른다 -
     * operation_end_date가 없는(일정 미정) 행사는 끝났다고 볼 근거가 없어 UPCOMING으로 묶인다.
     * UPCOMING은 임박한 순으로(operation_start_date ASC, NULL은 맨 뒤), PAST는 최근에 끝난
     * 순으로(operation_end_date DESC) 정렬한다.
     */
    List<Fair> selectPublicFairs(@Param("filter") PublicFairListFilter filter, @Param("today") LocalDate today);
}
