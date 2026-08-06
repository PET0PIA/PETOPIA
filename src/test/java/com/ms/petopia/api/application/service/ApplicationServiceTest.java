package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.domain.Application;
import com.ms.petopia.api.application.dto.request.ApplicationApproveRequest;
import com.ms.petopia.api.application.dto.request.ApplicationRejectRequest;
import com.ms.petopia.api.application.dto.request.ApplicationSubmitRequest;
import com.ms.petopia.api.application.dto.response.*;
import com.ms.petopia.api.application.mapper.ApplicationMapper;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.api.recruitnotice.domain.FairStatusInfo;
import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

/*
 * ApplicationService 단위 테스트.
 * 실제 DB(Mapper)는 Mock으로 대체하고, Service의 검증·조립 로직만 검증한다.
 * ApplicationMapper 외에 다른 도메인 매퍼(BusinessMapper, RecruitNoticeMapper)도
 * 그대로 Mock 처리한다 — 서비스가 실제로 그렇게 재사용하고 있기 때문.
 *
 * submitApplication()은 부스 슬롯별 GET_LOCK 락을 트랜잭션 커밋/롤백 완료 후에만
 * 해제하도록 TransactionSynchronizationManager에 콜백을 등록한다. 테스트에는 진짜 DB
 * 트랜잭션이 없어 그 콜백이 자동으로 실행되지 않으므로, setUp/tearDown으로 동기화를
 * 수동 활성화하고, 필요한 테스트에서 등록된 콜백을 직접 실행해 커밋/롤백을 시뮬레이션한다.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private BusinessMapper businessMapper;

    @Mock
    private RecruitNoticeMapper recruitNoticeMapper;

    @InjectMocks
    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // submitApplication()이 등록해둔 트랜잭션 동기화 콜백을 실제 커밋/롤백이 일어난 것처럼 수동 실행
    private void simulateTransactionCompletion(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(status));
    }

    // 테스트용 신청 요청 DTO. boothSlotIds만 테스트마다 다르게 주고 나머지는 고정값 사용
    private ApplicationSubmitRequest createRequest(List<Long> boothSlotIds) {

        ApplicationSubmitRequest request = new ApplicationSubmitRequest();

        request.setBusinessId(1L);
        request.setBoothSlotIds(boothSlotIds);
        request.setPurpose("신제품 홍보 및 오프라인 판매");
        request.setItemsDesc("유기농 사료, 간식 샘플");
        request.setManagerName("김담당");
        request.setManagerPhone("010-1234-5678");
        request.setManagerEmail("manager@test.com");
        request.setAgreedTerms(true);

        return request;

    }

    // 테스트용 사업자. ownerId를 바꿔가며 본인 소유 확인 케이스에도 재사용
    private Business createBusiness(Long businessId, Long ownerId) {

        Business business = new Business();

        business.setBusinessId(businessId);
        business.setOwnerId(ownerId);

        return business;

    }

    // 테스트용 부스 슬롯(잠금 상태 포함). locked만 바꿔가며 잠금 케이스 재현
    private BoothSlotLockStatusResponse createSlot(Long boothSlotId, boolean locked, long price) {

        return BoothSlotLockStatusResponse.builder()
                .boothSlotsId(boothSlotId)
                .slotNumber("A-0" + boothSlotId)
                .price(price)
                .locked(locked)
                .build();

    }

    /*
     * 모집 마감 판정(isRecruitClosed)에 안 걸리는 정상 모집중 상태를 만드는 공용 헬퍼.
     * 마감일은 내일, 행사는 취소 안 됨 + IN_PROGRESS로 고정해서, 마감 관련 케이스가 아닌
     * 다른 테스트에서 매번 반복해서 스텁하지 않도록 뽑아뒀다.
     */
    private void stubRecruitOpen(Long fairId) {

        RecruitNotice notice = RecruitNotice.builder()
                .recruitDeadline(LocalDateTime.now().plusDays(1))
                .build();

        FairStatusInfo fairStatus = new FairStatusInfo();

        fairStatus.setCanceledAt(null);
        fairStatus.setStatus("IN_PROGRESS");

        given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(notice);
        given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(fairStatus);

    }

    @Nested
    @DisplayName("부스 슬롯 목록 + 잠금 상태 조회")
    class GetBoothSlots {

        @Test
        @DisplayName("행사가 존재하면 슬롯 목록을 반환한다")
        void returnsSlotsWhenFairExists() {

            // given: 행사 존재 + 슬롯 1개짜리 목록을 매퍼가 리턴하도록 스텁
            Long fairId = 1L;
            List<BoothSlotLockStatusResponse> slots =
                    List.of(createSlot(1L, false, 450000));

            given(applicationMapper.existsFair(fairId)).willReturn(true);
            given(applicationMapper.selectBoothSlotsWithLockStatus(fairId)).willReturn(slots);

            // when
            List<BoothSlotLockStatusResponse> result = applicationService.getBoothSlots(fairId);

            // then: 매퍼가 리턴한 슬롯 목록이 그대로 반환되는지 확인
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getBoothSlotsId()).isEqualTo(1L);

        }

        @Test
        @DisplayName("행사가 없으면 예외를 던진다")
        void throwsWhenFairNotFound() {

            // given: 존재하지 않는 행사
            Long fairId = 999L;

            given(applicationMapper.existsFair(fairId)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> applicationService.getBoothSlots(fairId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 행사입니다");

            // 행사 자체가 없으니, 슬롯 조회 쿼리는 아예 호출되면 안 됨
            verify(applicationMapper, never()).selectBoothSlotsWithLockStatus(any());

        }

    }

    @Nested
    @DisplayName("참가 신청서 제출")
    class SubmitApplication {

        @Test
        @DisplayName("검증을 모두 통과하면 신청서를 저장하고 응답을 반환한다")
        void submitsSuccessfully() {

            // given: 약관동의/사업자소유/행사존재/모집중/중복없음/슬롯정상, 검증을 다 통과하는 상황
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L, 2L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));
            stubRecruitOpen(fairId);
            given(applicationMapper.existsActiveApplication(1L, fairId)).willReturn(false);

            // 선택한 슬롯 2개 다 존재하고 아직 안 잠긴 상태
            List<BoothSlotLockStatusResponse> slots = List.of(
                    createSlot(1L, false, 450000),
                    createSlot(2L, false, 450000)
            );
            given(applicationMapper.selectBoothSlotsWithLockStatus(fairId)).willReturn(slots);

            // 부스 슬롯별 GET_LOCK 획득 성공
            given(applicationMapper.acquireBoothSlotLock(1L)).willReturn(1);
            given(applicationMapper.acquireBoothSlotLock(2L)).willReturn(1);

            // 락 확보 후 점유 여부 재확인에서도 잠긴 슬롯 없음(빈 리스트)
            given(applicationMapper.selectLockedBoothSlotIds(List.of(1L, 2L))).willReturn(List.of());

            // insert 후 재조회(selectById) 시 돌려줄 저장된 신청서
            Application saved = Application.builder()
                    .applicationId(100L)
                    .businessId(1L)
                    .fairId(fairId)
                    .status(Application.Status.PENDING_REVIEW)
                    .submittedAt(LocalDateTime.now())
                    .build();
            given(applicationMapper.selectById(any())).willReturn(saved);

            // when
            ApplicationResponse result = applicationService.submitApplication(ownerId, fairId, request);

            // 실제 커밋 시점을 시뮬레이션 — 슬롯별로 등록해둔 락 해제 콜백을 수동 실행
            simulateTransactionCompletion(TransactionSynchronization.STATUS_COMMITTED);

            // then: 응답 값 확인
            assertThat(result.getApplicationId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
            assertThat(result.getBoothSlotIds()).containsExactly(1L, 2L);

            // application -> application_form -> application_slot(슬롯 개수만큼) 순서로 저장됐는지 확인
            verify(applicationMapper).insertApplication(any());
            verify(applicationMapper).insertApplicationForm(any());
            verify(applicationMapper, org.mockito.Mockito.times(2))
                    .insertApplicationSlot(any());
            // 커밋 후 슬롯별 락이 각각 해제됐는지 확인
            verify(applicationMapper).releaseBoothSlotLock(1L);
            verify(applicationMapper).releaseBoothSlotLock(2L);
            // 같은 사업자 동시 신청 직렬화용 락이 걸렸는지 확인
            verify(businessMapper).lockBusinessForApplication(1L);

        }

        @Test
        @DisplayName("약관에 동의하지 않으면 예외를 던진다")
        void throwsWhenTermsNotAgreed() {

            // given: agreedTerms가 false인 요청
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            request.setAgreedTerms(false);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("이용약관에 동의해야");

            // 약관 체크에서 바로 막혔으니, 그 이후 조회(사업자 확인 등)는 전혀 실행되면 안 됨
            verify(businessMapper, never()).selectById(any());

        }

        @Test
        @DisplayName("약관 동의 여부를 아예 안 보내면(null) 예외를 던진다")
        void throwsWhenTermsIsNull() {

            // given: agreedTerms가 null인 요청
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            request.setAgreedTerms(null);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("이용약관에 동의해야");

            // 약관 null은 동의 안 함으로 막혔으니, 그 이후 조회(사업자 확인 등)는 전혀 실행되면 안 됨
            verify(businessMapper, never()).selectById(any());

        }

        @Test
        @DisplayName("사업자가 존재하지 않으면 예외를 던진다")
        void throwsWhenBusinessNotFound() {

            // given: businessMapper가 null을 리턴(사업자 없음)
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("사업자를 찾을 수 없습니다");

            // 사업자 확인에서 막혔으니, 행사 존재 확인 이후 단계는 실행되면 안 됨
            verify(applicationMapper, never()).existsFair(any());

        }

        @Test
        @DisplayName("본인 소유의 사업자가 아니면 예외를 던진다")
        void throwsWhenNotBusinessOwner() {

            // given: 사업자의 실제 소유자는 2L인데, 요청자는 1L
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, 2L));

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인 소유의 사업자만");

            // 사업자 실제 소유 확인에서 막혔으니, 이후 단계는 실행되면 안 됨
            verify(applicationMapper, never()).existsFair(any());

        }

        @Test
        @DisplayName("행사가 존재하지 않으면 예외를 던진다")
        void throwsWhenFairNotFoundOnSubmit() {

            // given: 사업자 소유 확인은 통과, fairStatus 조회 결과가 없는(행사 없음) 상황
            Long ownerId = 1L;
            Long fairId = 999L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));
            given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 행사입니다");

            // 행사 확인에서 막혔으니, 모집 마감 판정(selectByFairId) 이후 단계는 실행되면 안 됨
            verify(recruitNoticeMapper, never()).selectByFairId(any());

        }

        @Test
        @DisplayName("모집 마감일이 지났으면 예외를 던진다")
        void throwsWhenRecruitDeadlinePassed() {

            // given: 사업자·행사 확인은 통과, 마감일이 과거인 상황
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));

            RecruitNotice notice = RecruitNotice.builder()
                    .recruitDeadline(LocalDateTime.now().minusDays(1))
                    .build();
            FairStatusInfo fairStatus = new FairStatusInfo();
            fairStatus.setCanceledAt(null);
            fairStatus.setStatus("IN_PROGRESS");

            given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(fairStatus);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(notice);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("모집이 마감되어");

            // 마감 확인에서 막혔으니, 중복 신청 확인 이후 단계는 실행되면 안 됨
            verify(applicationMapper, never()).existsActiveApplication(any(), any());

        }

        @Test
        @DisplayName("행사가 취소됐으면 마감일이 남았어도 예외를 던진다")
        void throwsWhenFairCanceled() {

            // given: 마감일은 미래지만, 행사가 취소된 상황
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));

            RecruitNotice notice = RecruitNotice.builder()
                    .recruitDeadline(LocalDateTime.now().plusDays(1))
                    .build();
            FairStatusInfo fairStatus = new FairStatusInfo();
            fairStatus.setCanceledAt(LocalDateTime.now().minusDays(1));
            fairStatus.setStatus("IN_PROGRESS");

            given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(fairStatus);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(notice);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("모집이 마감되어");

        }

        @Test
        @DisplayName("행사가 종료(ENDED)됐으면 마감일이 남았어도 예외를 던진다")
        void throwsWhenFairEnded() {

            // given: 마감일은 미래지만, 행사 상태가 ENDED인 상황
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));

            RecruitNotice notice = RecruitNotice.builder()
                    .recruitDeadline(LocalDateTime.now().plusDays(1))
                    .build();
            FairStatusInfo fairStatus = new FairStatusInfo();
            fairStatus.setCanceledAt(null);
            fairStatus.setStatus("ENDED");

            given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(fairStatus);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(notice);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("모집이 마감되어");

        }

        @Test
        @DisplayName("이미 진행 중인 신청이 있으면 예외를 던진다")
        void throwsWhenActiveApplicationExists() {

            // given: 앞 단계(사업자·행사·마감) 전부 통과, 이미 활성 신청이 있는 상황
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));
            stubRecruitOpen(fairId);
            given(applicationMapper.existsActiveApplication(1L, fairId)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("이미 진행 중인 신청이 존재합니다");

            // 중복 신청에서 막혔으니, 슬롯 조회는 아예 실행되면 안 됨
            verify(applicationMapper, never()).selectBoothSlotsWithLockStatus(any());

        }

        @Test
        @DisplayName("같은 슬롯을 중복으로 선택하면 예외를 던진다")
        void throwsWhenSlotSelectedTwice() {

            // given: boothSlotIds에 같은 슬롯(1L)이 두 번 들어감
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L, 1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));
            stubRecruitOpen(fairId);
            given(applicationMapper.existsActiveApplication(1L, fairId)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("중복으로 선택할 수 없습니다");

            // 중복 체크에서 막혔으니, 슬롯 조회 쿼리는 실행되면 안 됨
            verify(applicationMapper, never()).selectBoothSlotsWithLockStatus(any());

        }

        @Test
        @DisplayName("이 행사에 존재하지 않는 슬롯이면 예외를 던진다")
        void throwsWhenSlotNotInFair() {

            // given: 요청한 슬롯(999L)이 이 행사의 슬롯 목록에 없음
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(999L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));
            stubRecruitOpen(fairId);
            given(applicationMapper.existsActiveApplication(1L, fairId)).willReturn(false);
            given(applicationMapper.selectBoothSlotsWithLockStatus(fairId))
                    .willReturn(List.of(createSlot(1L, false, 450000)));

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("존재하지 않는 부스 슬롯");

            // 슬롯 검증에서 막혔으니, 락 획득/점유 확인까지는 안 감
            verify(applicationMapper, never()).acquireBoothSlotLock(any());
            verify(applicationMapper, never()).selectLockedBoothSlotIds(any());

        }

        @Test
        @DisplayName("이미 다른 활성 신청이 선점한 슬롯이면 예외를 던진다")
        void throwsWhenSlotAlreadyLocked() {

            // given: 요청한 슬롯(1L)이 조회 시점부터 이미 locked=true
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));
            stubRecruitOpen(fairId);
            given(applicationMapper.existsActiveApplication(1L, fairId)).willReturn(false);
            given(applicationMapper.selectBoothSlotsWithLockStatus(fairId))
                    .willReturn(List.of(createSlot(1L, true, 450000)));

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("이미 다른 신청에서 선택된 부스 슬롯");

            verify(applicationMapper, never()).acquireBoothSlotLock(any());
            verify(applicationMapper, never()).selectLockedBoothSlotIds(any());

        }

        @Test
        @DisplayName("부스 슬롯 락 획득에 실패(타임아웃)하면 예외를 던진다")
        void throwsWhenBoothSlotLockAcquisitionFails() {

            // given: 다른 요청이 이미 이 슬롯의 GET_LOCK을 잡고 있어서 타임아웃(0)이 리턴되는 상황
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));
            stubRecruitOpen(fairId);
            given(applicationMapper.existsActiveApplication(1L, fairId)).willReturn(false);
            given(applicationMapper.selectBoothSlotsWithLockStatus(fairId))
                    .willReturn(List.of(createSlot(1L, false, 450000)));
            given(applicationMapper.acquireBoothSlotLock(1L)).willReturn(0);

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("이미 다른 신청에서 선택된 부스 슬롯");

            // 락 획득 자체가 실패했으니, 점유 여부 재확인 쿼리까지는 가면 안 됨
            verify(applicationMapper, never()).selectLockedBoothSlotIds(any());

        }

        @Test
        @DisplayName("1차 조회 땐 안 잠겨있었지만 최종 점유 확인 시점엔 잠긴 슬롯이면 예외를 던진다")
        void throwsWhenSlotLockedAtFinalCheck() {

            // given: selectBoothSlotsWithLockStatus에선 locked=false로 나왔지만,
            // (그 사이 다른 트랜잭션이 먼저 잠근 상황을 가정) 점유 여부 재확인 쿼리에선 잠긴 걸로 나옴
            Long ownerId = 1L;
            Long fairId = 1L;
            ApplicationSubmitRequest request = createRequest(List.of(1L));

            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));
            stubRecruitOpen(fairId);
            given(applicationMapper.existsActiveApplication(1L, fairId)).willReturn(false);
            given(applicationMapper.selectBoothSlotsWithLockStatus(fairId))
                    .willReturn(List.of(createSlot(1L, false, 450000)));
            given(applicationMapper.acquireBoothSlotLock(1L)).willReturn(1);
            given(applicationMapper.selectLockedBoothSlotIds(List.of(1L)))
                    .willReturn(List.of(1L));

            // when & then
            assertThatThrownBy(() -> applicationService.submitApplication(ownerId, fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("이미 다른 신청에서 선택된 부스 슬롯");

            // 실제로는 트랜잭션이 롤백되면서 afterCompletion(ROLLED_BACK)이 호출됨 — 시뮬레이션
            simulateTransactionCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            // 최종 잠금 확인에서 막혔으니, 실제 저장(insert)은 전혀 실행되면 안 됨
            verify(applicationMapper, never()).insertApplication(any());
            // 롤백되더라도 이미 잡았던 락(1L)은 반드시 해제돼야 함
            verify(applicationMapper).releaseBoothSlotLock(1L);

        }

    }

    @Nested
    @DisplayName("내 신청 현황 목록 조회")
    class GetMyApplications {

        @Test
        @DisplayName("사업자 필터 없이 조회하면 내 신청 전체를 반환한다")
        void returnsAllMyApplications() {

            // given
            Long ownerId = 1L;

            List<ApplicationSummaryResponse> applications = List.of(
                    ApplicationSummaryResponse.builder()
                            .applicationId(1L).fairName("멍냥페스타 2026")
                            .status("PENDING_REVIEW").finalPrice(null).rejectReason(null)
                            .build(),
                    ApplicationSummaryResponse.builder()
                            .applicationId(2L).fairName("멍냥페스타 2027")
                            .status("PAYMENT_PENDING").finalPrice(900000L).rejectReason(null)
                            .build()
            );

            given(applicationMapper.selectMyApplications(ownerId, null)).willReturn(applications);

            // when
            List<ApplicationSummaryResponse> result = applicationService.getMyApplications(ownerId, null);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getFairName()).isEqualTo("멍냥페스타 2026");
            assertThat(result.get(1).getStatus()).isEqualTo("PAYMENT_PENDING");

        }

        @Test
        @DisplayName("businessId를 넘기면 매퍼에 그대로 필터로 전달된다")
        void passesBusinessIdFilterToMapper() {

            // given
            Long ownerId = 1L;
            Long businessId = 5L;

            given(applicationMapper.selectMyApplications(ownerId, businessId)).willReturn(List.of());

            // when
            applicationService.getMyApplications(ownerId, businessId);

            // then: 서비스가 받은 businessId를 그대로 매퍼에 넘기는지 확인
            verify(applicationMapper).selectMyApplications(ownerId, businessId);

        }

        @Test
        @DisplayName("신청 이력이 없으면 빈 리스트를 반환한다")
        void returnsEmptyListWhenNoApplications() {

            // given
            Long ownerId = 999L;

            given(applicationMapper.selectMyApplications(ownerId, null)).willReturn(List.of());

            // when
            List<ApplicationSummaryResponse> result = applicationService.getMyApplications(ownerId, null);

            // then
            assertThat(result).isEmpty();

        }

    }

    @Nested
    @DisplayName("신청 상세 조회")
    class GetApplicationDetail {

        @Test
        @DisplayName("본인 소유 신청이면 슬롯 목록까지 채워서 반환한다")
        void returnsDetailWithSlots() {

            // given: 신청 상세는 정상 조회되고, 사업자 소유자도 요청자와 일치하는 상황
            Long ownerId = 1L;
            Long applicationId = 100L;

            ApplicationDetailResponse detail = ApplicationDetailResponse.builder()
                    .applicationId(applicationId)
                    .fairId(1L)
                    .businessId(1L)
                    .status("PENDING_REVIEW")
                    .purpose("신제품 홍보 및 오프라인 판매")
                    .itemsDesc("유기농 사료, 간식 샘플")
                    .managerName("김담당")
                    .build();

            // 이 신청이 선택한 슬롯 2개
            List<ApplicationSlotDetailResponse> slots = List.of(
                    ApplicationSlotDetailResponse.builder()
                            .boothSlotsId(1L).slotNumber("A-01").priceAtSelection(450000L)
                            .build(),
                    ApplicationSlotDetailResponse.builder()
                            .boothSlotsId(2L).slotNumber("A-02").priceAtSelection(450000L)
                            .build()
            );

            given(applicationMapper.selectApplicationDetail(applicationId)).willReturn(detail);
            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, ownerId));
            given(applicationMapper.selectApplicationSlotDetails(applicationId)).willReturn(slots);

            // when
            ApplicationDetailResponse result = applicationService.getApplicationDetail(ownerId, applicationId);

            // then: 신청 내용 + 슬롯 목록이 정확히 담겼는지 확인
            assertThat(result.getApplicationId()).isEqualTo(applicationId);
            assertThat(result.getPurpose()).isEqualTo("신제품 홍보 및 오프라인 판매");
            assertThat(result.getSlots()).hasSize(2);
            assertThat(result.getSlots().get(0).getSlotNumber()).isEqualTo("A-01");

        }

        @Test
        @DisplayName("신청이 존재하지 않으면 예외를 던진다")
        void throwsWhenApplicationNotFound() {

            // given: 존재하지 않는 applicationId(매퍼가 null 리턴)
            Long ownerId = 1L;
            Long applicationId = 999L;

            given(applicationMapper.selectApplicationDetail(applicationId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> applicationService.getApplicationDetail(ownerId, applicationId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("신청을 찾을 수 없습니다");

            // 신청 자체가 없으니, 사업자 조회는 시도되면 안 됨
            verify(businessMapper, never()).selectById(any());

        }

        @Test
        @DisplayName("본인 소유의 신청이 아니면 예외를 던진다")
        void throwsWhenNotOwner() {

            // given: 신청의 사업자(1L) 실제 소유자는 2L인데, 요청자는 1L인 상황
            Long ownerId = 1L;
            Long applicationId = 100L;

            ApplicationDetailResponse detail = ApplicationDetailResponse.builder()
                    .applicationId(applicationId)
                    .businessId(1L)
                    .build();

            given(applicationMapper.selectApplicationDetail(applicationId)).willReturn(detail);
            given(businessMapper.selectById(1L)).willReturn(createBusiness(1L, 2L));

            // when & then
            assertThatThrownBy(() -> applicationService.getApplicationDetail(ownerId, applicationId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인 소유의 신청만");

            // 소유 확인에서 막혔으니, 슬롯 조회는 실행되면 안 됨
            verify(applicationMapper, never()).selectApplicationSlotDetails(any());

        }

    }

    @Nested
    @DisplayName("담당 행사의 신청 목록 조회(행사 담당자용)")
    class GetApplicationsForFair {

        @Test
        @DisplayName("담당자 본인이면 신청 목록을 반환한다")
        void returnsApplicationsWhenAdminMatches() {

            // given: 이 행사의 담당자가 요청자 본인인 상황
            Long adminUserId = 1L;
            Long fairId = 1L;

            List<ApplicationReviewSummaryResponse> applications = List.of(
                    ApplicationReviewSummaryResponse.builder()
                            .applicationId(1L).businessId(10L).businessName("멍냥용품")
                            .status("PENDING_REVIEW").submittedAt(LocalDateTime.now())
                            .finalPrice(null).rejectReason(null)
                            .build()
            );

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);
            given(applicationMapper.selectApplicationsByFair(fairId, null)).willReturn(applications);

            // when
            List<ApplicationReviewSummaryResponse> result =
                    applicationService.getApplicationsForFair(adminUserId, fairId, null);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getBusinessName()).isEqualTo("멍냥용품");

        }

        @Test
        @DisplayName("status 필터를 넘기면 매퍼에 그대로 전달된다")
        void passesStatusFilterToMapper() {

            // given
            Long adminUserId = 1L;
            Long fairId = 1L;
            String status = "PENDING_REVIEW";

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);
            given(applicationMapper.selectApplicationsByFair(fairId, status)).willReturn(List.of());

            // when
            applicationService.getApplicationsForFair(adminUserId, fairId, status);

            // then
            verify(applicationMapper).selectApplicationsByFair(fairId, status);

        }

        @Test
        @DisplayName("담당자가 배정되지 않은 행사면 예외를 던진다")
        void throwsWhenNoAdminAssigned() {

            // given: fair_admin_assignments에 담당자 자체가 없는 상황
            Long adminUserId = 1L;
            Long fairId = 999L;

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> applicationService.getApplicationsForFair(adminUserId, fairId, null))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("담당자가 배정되지 않은 행사입니다");

            // 담당자 확인에서 막혔으니, 목록 조회 쿼리는 실행되면 안 됨
            verify(applicationMapper, never()).selectApplicationsByFair(any(), any());

        }

        @Test
        @DisplayName("본인이 담당하는 행사가 아니면 예외를 던진다")
        void throwsWhenNotAssignedAdmin() {

            // given: 이 행사의 실제 담당자는 2L인데, 요청자는 1L
            Long adminUserId = 1L;
            Long fairId = 1L;

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(2L);

            // when & then
            assertThatThrownBy(() -> applicationService.getApplicationsForFair(adminUserId, fairId, null))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인이 담당하는 행사가 아닙니다");

            verify(applicationMapper, never()).selectApplicationsByFair(any(), any());

        }

    }

    @Nested
    @DisplayName("참가 신청서 승인")
    class ApproveApplication {

        // 테스트용 신청서. status만 바꿔가며 심사 대기/이미 처리됨 케이스 재현
        private Application createApplication(Long applicationId, Long fairId, Application.Status status) {

            return Application.builder()
                    .applicationId(applicationId)
                    .businessId(1L)
                    .fairId(fairId)
                    .status(status)
                    .build();

        }

        @Test
        @DisplayName("finalPrice를 안 주면 슬롯 가격 합계로 자동 계산해서 승인한다")
        void approvesWithAutoCalculatedFinalPrice() {

            // given: request=null(담당자가 finalPrice를 안 보낸 상황), 심사 대기 상태인 신청서
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.PENDING_REVIEW));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);
            // 슬롯 가격 합계(실제 SUM 쿼리 결과라고 가정) - 이 매퍼 값 자체가 900000이라고 스텁
            given(applicationMapper.sumSlotPricesByApplicationId(applicationId)).willReturn(900000L);
            given(applicationMapper.updateApplicationApproved(eq(applicationId), eq(900000L), any(), any()))
                    .willReturn(1);

            // when
            ApplicationReviewResultResponse result =
                    applicationService.approveApplication(adminUserId, applicationId, null);

            // then: 합계값이 그대로 finalPrice로 반영됐는지, 결제 마감일도 채워졌는지 확인
            assertThat(result.getStatus()).isEqualTo("PAYMENT_PENDING");
            assertThat(result.getFinalPrice()).isEqualTo(900000L);
            assertThat(result.getPaymentDueAt()).isNotNull();

        }

        @Test
        @DisplayName("담당자가 finalPrice를 직접 지정하면 그 값을 그대로 사용한다")
        void approvesWithAdminSpecifiedFinalPrice() {

            // given: 담당자가 화면에서 미리 본 합계 대신 다른 값(500000)을 직접 입력한 상황
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;
            ApplicationApproveRequest request = new ApplicationApproveRequest();
            request.setFinalPrice(500000L);

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.PENDING_REVIEW));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);
            given(applicationMapper.updateApplicationApproved(eq(applicationId), eq(500000L), any(), any()))
                    .willReturn(1);

            // when
            ApplicationReviewResultResponse result =
                    applicationService.approveApplication(adminUserId, applicationId, request);

            // then: 담당자가 지정한 값이 그대로 쓰였는지 + 불필요한 합계 조회 쿼리는 안 불렸는지 확인
            assertThat(result.getFinalPrice()).isEqualTo(500000L);
            verify(applicationMapper, never()).sumSlotPricesByApplicationId(any());

        }

        @Test
        @DisplayName("신청이 존재하지 않으면 예외를 던진다")
        void throwsWhenApplicationNotFound() {

            // given: 존재하지 않는 applicationId
            Long adminUserId = 1L;
            Long applicationId = 999L;

            given(applicationMapper.selectById(applicationId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> applicationService.approveApplication(adminUserId, applicationId, null))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("신청을 찾을 수 없습니다");

            // 신청 자체가 없으니, 담당자 확인 단계까지 가면 안 됨
            verify(recruitNoticeMapper, never()).selectAdminUserIdByFairId(any());

        }

        @Test
        @DisplayName("담당자가 아니면 예외를 던진다")
        void throwsWhenNotAssignedAdmin() {

            // given: 이 행사의 실제 담당자는 2L인데, 요청자는 1L
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.PENDING_REVIEW));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(2L);

            // when & then
            assertThatThrownBy(() -> applicationService.approveApplication(adminUserId, applicationId, null))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인이 담당하는 행사가 아닙니다");

            // 담당자 확인에서 막혔으니, 실제 승인 처리는 실행되면 안 됨
            verify(applicationMapper, never()).updateApplicationApproved(any(), any(), any(), any());

        }

        @Test
        @DisplayName("이미 심사 처리된 신청이면 예외를 던진다")
        void throwsWhenNotPendingReview() {

            // given: 이미 반려된 신청서를 다시 승인하려는 상황
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.REJECTED));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);

            // when & then
            assertThatThrownBy(() -> applicationService.approveApplication(adminUserId, applicationId, null))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("심사 대기 중인 신청서만");

            verify(applicationMapper, never()).updateApplicationApproved(any(), any(), any(), any());

        }

        @Test
        @DisplayName("동시 처리로 UPDATE가 0행 반영되면 예외를 던진다")
        void throwsWhenConcurrentApprovalWins() {

            /*
             * given: 조회 시점엔 PENDING_REVIEW였지만(선행 체크 통과),
             * 실제 UPDATE 실행 시점엔 다른 요청이 먼저 처리해버려서 0행 반영된 상황(락 없이 UPDATE 조건으로 방어)
             */
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.PENDING_REVIEW));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);
            given(applicationMapper.sumSlotPricesByApplicationId(applicationId)).willReturn(900000L);
            given(applicationMapper.updateApplicationApproved(any(), any(), any(), any())).willReturn(0);

            // when & then: 선행 체크는 통과했지만, UPDATE의 영향받은 행이 0이라 동일한 예외로 최종 차단됨
            assertThatThrownBy(() -> applicationService.approveApplication(adminUserId, applicationId, null))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("심사 대기 중인 신청서만");

        }

    }

    @Nested
    @DisplayName("참가 신청서 반려")
    class RejectApplication {

        // 테스트용 신청서. status만 바꿔가며 심사 대기/이미 처리됨 케이스 재현
        private Application createApplication(Long applicationId, Long fairId, Application.Status status) {

            return Application.builder()
                    .applicationId(applicationId)
                    .businessId(1L)
                    .fairId(fairId)
                    .status(status)
                    .build();

        }

        // 테스트용 반려 요청 DTO
        private ApplicationRejectRequest createRejectRequest(String rejectReason) {

            ApplicationRejectRequest request = new ApplicationRejectRequest();
            request.setRejectReason(rejectReason);

            return request;

        }

        @Test
        @DisplayName("정상적으로 반려 처리한다")
        void rejectsSuccessfully() {

            // given: 심사 대기 상태인 신청서 + 정상적인 반려 사유
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.PENDING_REVIEW));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);
            given(applicationMapper.updateApplicationRejected(eq(applicationId), eq("부적합"), any()))
                    .willReturn(1);

            // when
            ApplicationReviewResultResponse result =
                    applicationService.rejectApplication(adminUserId, applicationId, createRejectRequest("부적합"));

            // then: 상태와 반려 사유가 응답에 정확히 담겼는지 확인
            assertThat(result.getStatus()).isEqualTo("REJECTED");
            assertThat(result.getRejectReason()).isEqualTo("부적합");

        }

        @Test
        @DisplayName("신청이 존재하지 않으면 예외를 던진다")
        void throwsWhenApplicationNotFound() {

            // given: 존재하지 않는 applicationId
            Long adminUserId = 1L;
            Long applicationId = 999L;

            given(applicationMapper.selectById(applicationId)).willReturn(null);

            // when & then
            assertThatThrownBy(() ->
                    applicationService.rejectApplication(adminUserId, applicationId, createRejectRequest("사유")))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("신청을 찾을 수 없습니다");

        }

        @Test
        @DisplayName("담당자가 아니면 예외를 던진다")
        void throwsWhenNotAssignedAdmin() {

            // given: 이 행사의 실제 담당자는 2L인데, 요청자는 1L
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.PENDING_REVIEW));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(2L);

            // when & then
            assertThatThrownBy(() ->
                    applicationService.rejectApplication(adminUserId, applicationId, createRejectRequest("사유")))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인이 담당하는 행사가 아닙니다");

            verify(applicationMapper, never()).updateApplicationRejected(any(), any(), any());

        }

        @Test
        @DisplayName("이미 심사 처리된 신청이면 예외를 던진다")
        void throwsWhenNotPendingReview() {

            // given: 이미 승인(PAYMENT_PENDING)된 신청서를 다시 반려하려는 상황
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.PAYMENT_PENDING));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);

            // when & then
            assertThatThrownBy(() ->
                    applicationService.rejectApplication(adminUserId, applicationId, createRejectRequest("사유")))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("심사 대기 중인 신청서만");

            verify(applicationMapper, never()).updateApplicationRejected(any(), any(), any());

        }

        @Test
        @DisplayName("반려 사유가 없으면 예외를 던진다")
        void throwsWhenRejectReasonBlank() {

            // given: rejectReason이 공백뿐인 요청 (컨트롤러 @NotBlank 대신 서비스에서 방어)
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.PENDING_REVIEW));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);

            // when & then
            assertThatThrownBy(() ->
                    applicationService.rejectApplication(adminUserId, applicationId, createRejectRequest("  ")))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("반려 사유를 입력해야 합니다");

            verify(applicationMapper, never()).updateApplicationRejected(any(), any(), any());

        }

        @Test
        @DisplayName("동시 처리로 UPDATE가 0행 반영되면 예외를 던진다")
        void throwsWhenConcurrentRejectionLoses() {

            // given: 조회 시점엔 PENDING_REVIEW였지만, UPDATE 시점엔 다른 요청이 먼저 처리해버린 상황
            Long adminUserId = 1L;
            Long applicationId = 1L;
            Long fairId = 1L;

            given(applicationMapper.selectById(applicationId))
                    .willReturn(createApplication(applicationId, fairId, Application.Status.PENDING_REVIEW));
            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(adminUserId);
            given(applicationMapper.updateApplicationRejected(any(), any(), any())).willReturn(0);

            // when & then
            assertThatThrownBy(() ->
                    applicationService.rejectApplication(adminUserId, applicationId, createRejectRequest("사유")))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("심사 대기 중인 신청서만");

        }

    }

}
