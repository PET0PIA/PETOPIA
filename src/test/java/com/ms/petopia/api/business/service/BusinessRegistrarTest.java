package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.request.BusinessRegisterRequest;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * BusinessRegistrar 단위 테스트.
 * 실제 DB(BusinessMapper)는 Mock으로 대체하고, 중복 사업자등록번호 처리와
 * 동시 등록 직렬화용 락 처리 로직을 검증한다.
 *
 * save()는 트랜잭션 커밋/롤백 완료 후에 락을 해제하도록 TransactionSynchronizationManager에
 * 콜백을 등록한다. 테스트에는 진짜 DB 트랜잭션이 없어 그 콜백이 자동으로 실행되지 않으므로,
 * setUp/tearDown으로 동기화를 수동 활성화하고, 각 테스트에서 등록된 콜백을 직접 실행해
 * "커밋/롤백 이후"를 시뮬레이션한다.
 */
@ExtendWith(MockitoExtension.class)
class BusinessRegistrarTest {

    @Mock
    private BusinessMapper businessMapper;

    @InjectMocks
    private BusinessRegistrar businessRegistrar;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // save()가 등록해둔 트랜잭션 동기화 콜백을 실제 커밋/롤백이 일어난 것처럼 수동 실행
    private void simulateTransactionCompletion(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(status));
    }

    private BusinessRegisterRequest createRequest() {

        BusinessRegisterRequest request = new BusinessRegisterRequest();

        request.setName("테스트업체");
        request.setCeoName("홍길동");
        request.setBizRegNo("1234567890");
        request.setStartDate(LocalDate.of(2020, 1, 1));
        request.setAddress("서울시 강남구 테스트로 1");
        request.setPhone("02-1234-5678");
        request.setWebsite("https://test.co.kr");
        request.setBusinessRegDocKey("uploads/document/reg.pdf");

        return request;

    }

    private Business createBusiness(Long businessId, Long ownerId, String bizRegNo, Business.VerifyStatus verifyStatus) {

        Business business = new Business();

        business.setBusinessId(businessId);
        business.setOwnerId(ownerId);
        business.setName("테스트업체");
        business.setCeoName("홍길동");
        business.setBizRegNo(bizRegNo);
        business.setStartDate(LocalDate.of(2020, 1, 1));
        business.setAddress("서울시 강남구 테스트로 1");
        business.setPhone("02-1234-5678");
        business.setWebsite("https://test.co.kr");
        business.setVerifyStatus(verifyStatus);
        business.setApprovalStatus(Business.ApprovalStatus.PENDING_REVIEW);

        return business;

    }

    @Test
    @DisplayName("동시 등록 락을 획득하지 못하면(타임아웃) 예외를 던진다")
    void throwsWhenLockAcquisitionFails() {

        // given: 같은 ownerId로 이미 다른 요청이 락을 잡고 있어서 타임아웃(0)이 리턴되는 상황
        Long ownerId = 1L;
        BusinessRegisterRequest request = createRequest();

        given(businessMapper.acquireRegistrationLock(ownerId)).willReturn(0);

        // when & then
        assertThatThrownBy(() ->
                businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED, "uploads/document/reg.pdf"))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("사업자 등록 처리 중입니다");

        // 락을 못 잡았으니, 실제 저장 로직은 전혀 실행되면 안 됨
        verify(businessMapper, never()).insertBusiness(any());

        // 락 획득 자체를 실패했으니, 커밋 이후 콜백(해제)도 아예 등록되면 안 됨
        verify(businessMapper, never()).releaseRegistrationLock(any());

    }

    @Test
    @DisplayName("이미 등록된 사업자등록번호면 BUSINESS_DUPLICATE 예외를 던진다")
    void throwsWhenBizRegNoDuplicated() {

        // given: 락은 정상 획득, insert 시점에 DB unique 제약(active_biz_reg_no) 위반이 발생하는 상황을 재현
        Long ownerId = 1L;
        BusinessRegisterRequest request = createRequest();

        given(businessMapper.acquireRegistrationLock(ownerId)).willReturn(1);
        doThrow(new DuplicateKeyException("UK_BUSINESS_ACTIVE_BIZ_REG_NO"))
                .when(businessMapper).insertBusiness(any(Business.class));

        // when & then
        assertThatThrownBy(() ->
                businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED, "uploads/document/reg.pdf"))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("이미 등록된 사업자등록번호");

        // 실제로는 트랜잭션이 롤백되면서 afterCompletion(ROLLED_BACK)이 호출됨 — 시뮬레이션
        simulateTransactionCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        // 저장이 실패했으니, 재조회(selectById)까지 가면 안 됨
        verify(businessMapper, never()).selectById(any());

        // 롤백되더라도 락은 반드시 해제돼야 함
        verify(businessMapper).releaseRegistrationLock(ownerId);

    }

    @Test
    @DisplayName("정상 저장되면 첨부서류 objectKey와 함께 PENDING_REVIEW 상태로 insert한다")
    void savesBusinessAsPendingReview() {

        // given
        Long ownerId = 1L;
        BusinessRegisterRequest request = createRequest();
        Business saved = createBusiness(1L, ownerId, "1234567890", Business.VerifyStatus.VERIFIED);

        given(businessMapper.acquireRegistrationLock(ownerId)).willReturn(1);
        given(businessMapper.selectById(any())).willReturn(saved);

        ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);

        // when
        Business result = businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED, "uploads/document/reg.pdf");

        // 실제 커밋 시점을 시뮬레이션 — save()가 등록해둔 동기화 콜백을 수동으로 실행
        simulateTransactionCompletion(TransactionSynchronization.STATUS_COMMITTED);

        // then: insert에 넘어간 객체가 첨부서류 objectKey + PENDING_REVIEW로 조립됐는지 확인
        verify(businessMapper).insertBusiness(captor.capture());
        assertThat(captor.getValue().getBusinessRegDocKey()).isEqualTo("uploads/document/reg.pdf");
        assertThat(captor.getValue().getApprovalStatus()).isEqualTo(Business.ApprovalStatus.PENDING_REVIEW);

        assertThat(result.getBusinessId()).isEqualTo(1L);
        verify(businessMapper).releaseRegistrationLock(ownerId);

    }

}
