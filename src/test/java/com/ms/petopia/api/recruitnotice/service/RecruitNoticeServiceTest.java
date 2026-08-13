package com.ms.petopia.api.recruitnotice.service;

import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.recruitnotice.domain.FairStatusInfo;
import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import com.ms.petopia.api.recruitnotice.dto.request.RecruitNoticeRequest;
import com.ms.petopia.api.recruitnotice.dto.response.BoothSlotStatusResponse;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeResponse;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeUpsertResponse;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.willThrow;

/*
 * RecruitNoticeService 단위 테스트.
 * Mapper는 Mock으로 대체하고, Service의 로직(작성자 검증, 응답 변환)만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RecruitNoticeServiceTest {

    @Mock
    private RecruitNoticeMapper recruitNoticeMapper;

    @Mock
    private StorageService storageService;

    @Mock private FairAdminAccessGuard fairAdminAccessGuard;

    @InjectMocks
    private RecruitNoticeService recruitNoticeService;

    // 테스트용 요청 DTO를 만드는 헬퍼 메서드.
    private RecruitNoticeRequest createRequest(String title) {

        RecruitNoticeRequest request = new RecruitNoticeRequest();

        request.setTitle(title);
        request.setContent("반려동물 관련 사업자를 모집합니다.");
        request.setImageObjectKey("tmp/image/notice-1.jpg");
        request.setRecruitDeadline(LocalDateTime.now().plusDays(1));

        return request;

    }

    // 테스트용 RecruitNotice 도메인 객체(DB에 저장된 것처럼 가정)를 만드는 헬퍼 메서드.
    private RecruitNotice createNotice(Long recruitNoticeId, Long fairId, Long writerId,
                                       String title, LocalDateTime updatedAt) {

        return RecruitNotice.builder()
                .recruitNoticeId(recruitNoticeId)
                .fairId(fairId)
                .writerId(writerId)
                .title(title)
                .content("반려동물 관련 사업자를 모집합니다.")
                .imageUrl("https://cdn.petopia.kr/notice/1.jpg")
                .recruitDeadline(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .updatedAt(updatedAt)
                .build();

    }

    /*
     * 테스트용 FairStatusInfo(행사 취소/종료 여부) 객체를 만드는 헬퍼 메서드.
     * closed 판정 로직에서 참조하는 fairs 테이블의 상태값을 임의로 지정하기 위함.
     */
    private FairStatusInfo createFairStatus(LocalDateTime canceledAt, String status) {

        FairStatusInfo info = new FairStatusInfo();

        info.setCanceledAt(canceledAt);
        info.setStatus(status);

        return info;

    }

    @Nested
    @DisplayName("모집 공고 작성/수정")
    class UpsertNotice {

        @Test
        @DisplayName("담당 EVENT_ADMIN이고 기존 공고가 없으면 새로 작성한다")
        void createsNewNoticeWhenNotExists() {

            // given: 요청자(1L)가 이 행사의 담당자이고, 아직 공고는 없는 상황
            Long fairId = 1L;
            Long writerId = 1L;

            RecruitNoticeRequest request = createRequest("멍냥페스타 참가업체 모집");
            RecruitNotice savedNotice = createNotice(1L, fairId, writerId,
                    "멍냥페스타 참가업체 모집", null);

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(writerId);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(savedNotice);

            // when
            RecruitNoticeUpsertResponse result = recruitNoticeService.upsertNotice(fairId, request);

            // then: 새로 만들어진 공고 정보가 응답에 정확히 담겼는지 확인
            assertThat(result.getRecruitNoticeId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("멍냥페스타 참가업체 모집");

            // upsertNotice가 실제로 호출됐는지 확인
            verify(recruitNoticeMapper).upsertNotice(any(RecruitNotice.class));

        }

        @Test
        @DisplayName("담당 EVENT_ADMIN이고 본인이 작성한 기존 공고면 수정한다")
        void updatesNoticeWhenOwnerMatches() {

            // given: 요청자(1L)가 담당자인 상황
            Long fairId = 1L;
            Long writerId = 1L;

            RecruitNoticeRequest request = createRequest("수정된 제목");
            RecruitNotice updated = createNotice(1L, fairId, writerId,
                    "수정된 제목", LocalDateTime.of(2026, 8, 3, 15, 0));

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(writerId);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(updated);

            // when
            RecruitNoticeUpsertResponse result = recruitNoticeService.upsertNotice(fairId, request);

            // then: 같은 PK(1L)를 유지한 채로 제목이 바뀌고, updatedAt이 채워졌는지 확인
            assertThat(result.getRecruitNoticeId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("수정된 제목");
            assertThat(result.getUpdatedAt()).isNotNull();

            // upsertNotice가 실제로 호출됐는지 확인
            verify(recruitNoticeMapper).upsertNotice(any(RecruitNotice.class));

        }

        @Test
        @DisplayName("담당 EVENT_ADMIN이 배정되지 않은 행사면 예외를 던진다")
        void throwsWhenNoAdminAssigned() {

            // given: 이 fairId에 대해 fair_admin_assignments가 아예 없는 상황(null 리턴)
            Long fairId = 999L;

            RecruitNoticeRequest request = createRequest("담당자 없는 행사 공고 시도");

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> recruitNoticeService.upsertNotice(fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("담당자가 배정되지 않은 행사입니다");

            // 담당자 검증에서 막혔으니, 그 이후 로직(공고 조회·저장)은 전혀 실행되면 안 됨
            verify(recruitNoticeMapper, never()).selectByFairId(any());
            verify(recruitNoticeMapper, never()).upsertNotice(any(RecruitNotice.class));
        }


        @Test
        @DisplayName("담당 EVENT_ADMIN이 아니면 예외를 던진다")
        void throwsWhenNotFairAdmin() {

            Long fairId = 1L;
            RecruitNoticeRequest request = createRequest("남의 행사에 공고 작성 시도");

            willThrow(new CommonException(ErrorCode.ACCESS_DENIED, "담당하는 행사가 아닙니다."))
                    .given(fairAdminAccessGuard).checkAssigned(fairId);

            assertThatThrownBy(() -> recruitNoticeService.upsertNotice(fairId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("담당하는 행사가 아닙니다");

            // checkAssigned가 실제로 호출됐는지, 그 예외 때문에 막힌 게 맞는지 확인
            verify(fairAdminAccessGuard).checkAssigned(fairId);
            verify(recruitNoticeMapper, never()).upsertNotice(any(RecruitNotice.class));

        }

        @Test
        @DisplayName("upsertNotice에 넘기는 값이 요청 내용과 정확히 일치한다")
        void passesCorrectValuesToMapper() {

            // given
            Long fairId = 1L;
            Long writerId = 1L;

            RecruitNoticeRequest request = createRequest("검증용 제목");
            RecruitNotice savedNotice = createNotice(1L, fairId, writerId,
                    "검증용 제목", null);

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(writerId);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(savedNotice);

            // ArgumentCaptor: "Service가 Mock한테 뭘 넘겼는지" 우리가 훔쳐봄 (Service가 보낸 값을 확인)
            ArgumentCaptor<RecruitNotice> captor = ArgumentCaptor.forClass(RecruitNotice.class);

            // when
            recruitNoticeService.upsertNotice(fairId, request);

            // then: upsertNotice가 호출됐는지 확인하면서, 그때 넘어온 값을 캡처해라
            verify(recruitNoticeMapper).upsertNotice(captor.capture());
            // 캡처해둔 값을 꺼내서 실제로 열어봄
            RecruitNotice passed = captor.getValue();

            assertThat(passed.getFairId()).isEqualTo(fairId);
            assertThat(passed.getWriterId()).isEqualTo(writerId);
            // 그 안의 값들이 request 내용이랑 정확히 일치하는지 확인
            assertThat(passed.getTitle()).isEqualTo("검증용 제목");
        }

        @Test
        @DisplayName("imageObjectKey를 objectKey 확정 후 공개 URL로 변환해서 저장한다")
        void resolvesImageUrlFromObjectKey() {

            // given: presigned-upload로 받은 임시 objectKey를 포함한 요청
            Long fairId = 1L;
            Long writerId = 1L;

            RecruitNoticeRequest request = createRequest("이미지 변환 검증용 제목");
            RecruitNotice savedNotice = createNotice(1L, fairId, writerId,
                    "이미지 변환 검증용 제목", null);

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(writerId);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(savedNotice);

            // 스토리지 확정 흐름 스텁
            given(storageService.confirm("tmp/image/notice-1.jpg", UploadPolicy.IMAGE))
                    .willReturn("uploads/image/2026/08/07/notice-1.jpg");
            given(storageService.toPublicUrl("uploads/image/2026/08/07/notice-1.jpg"))
                    .willReturn("https://d2jl6zs612zyt4.cloudfront.net/uploads/image/2026/08/07/notice-1.jpg");

            ArgumentCaptor<RecruitNotice> captor = ArgumentCaptor.forClass(RecruitNotice.class);

            // when
            recruitNoticeService.upsertNotice(fairId, request);

            // then: Mapper에 넘어간 imageUrl이 confirm/toPublicUrl을 거친 최종 공개 URL인지 확인
            verify(recruitNoticeMapper).upsertNotice(captor.capture());
            assertThat(captor.getValue().getImageUrl())
                    .isEqualTo("https://d2jl6zs612zyt4.cloudfront.net/uploads/image/2026/08/07/notice-1.jpg");

        }

        @Test
        @DisplayName("새 이미지 없이 수정하면 기존 이미지를 그대로 유지한다")
        void keepsExistingImageWhenNoNewImageProvided() {

            // given: 기존 공고에 이미지가 이미 있고, 이번 요청엔 새 이미지가 없는 상황(제목만 수정)
            Long fairId = 1L;
            Long writerId = 1L;

            RecruitNoticeRequest request = createRequest("이미지 안 건드리고 제목만 수정");
            request.setImageObjectKey(null);

            RecruitNotice existing = createNotice(1L, fairId, writerId, "이전 제목", null);
            // createNotice가 만드는 imageUrl은 "https://cdn.petopia.kr/notice/1.jpg"로 고정

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(writerId);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(existing);

            ArgumentCaptor<RecruitNotice> captor = ArgumentCaptor.forClass(RecruitNotice.class);

            // when
            recruitNoticeService.upsertNotice(fairId, request);

            // then: Mapper에 넘어간 imageUrl이 null로 덮어써지지 않고 기존 값 그대로인지 확인
            verify(recruitNoticeMapper).upsertNotice(captor.capture());
            assertThat(captor.getValue().getImageUrl()).isEqualTo("https://cdn.petopia.kr/notice/1.jpg");

            // 새 이미지가 없으니 S3 confirm 흐름은 시도되면 안 됨
            verify(storageService, never()).confirm(any(), any());

        }

        @Test
        @DisplayName("imageObjectKey가 빈 문자열이어도 기존 이미지를 유지한다")
        void keepsExistingImageWhenObjectKeyIsBlank() {

            // given: imageObjectKey가 null이 아니라 빈 문자열로 온 엣지 케이스
            Long fairId = 1L;
            Long writerId = 1L;

            RecruitNoticeRequest request = createRequest("이미지 빈 문자열 엣지케이스");
            request.setImageObjectKey("");

            RecruitNotice existing = createNotice(1L, fairId, writerId, "이전 제목", null);

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(writerId);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(existing);

            ArgumentCaptor<RecruitNotice> captor = ArgumentCaptor.forClass(RecruitNotice.class);

            // when
            recruitNoticeService.upsertNotice(fairId, request);

            // then
            verify(recruitNoticeMapper).upsertNotice(captor.capture());
            assertThat(captor.getValue().getImageUrl()).isEqualTo("https://cdn.petopia.kr/notice/1.jpg");
            verify(storageService, never()).confirm(any(), any());

        }

    }

    @Nested
    @DisplayName("모집 공고 상세 조회")
    class GetNotice{

        @Test
        @DisplayName("마감일 전이고 행사도 정상이면 closed=false로 응답한다")
        void returnsNoticeWhenNotClosed() {

            // given: 마감일이 미래이고, 행사도 취소/종료 안 된 정상 상황
            Long fairId = 1L;

            RecruitNotice notice = createNotice(1L, fairId, 1L, "멍냥페스타 참가업체 모집", null);
            FairStatusInfo fairStatus = createFairStatus(null, "IN_PROGRESS");

            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(notice);
            given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(fairStatus);

            // when
            RecruitNoticeResponse result = recruitNoticeService.getNotice(fairId);

            // then: 세 조건 다 해당 안 되니 closed는 false여야 함
            assertThat(result.getRecruitNoticeId()).isEqualTo(1L);
            assertThat(result.isClosed()).isFalse();

        }

        @Test
        @DisplayName("모집 마감일이 지났으면 closed=true로 응답한다")
        void returnsClosedTrueWhenDeadlinePassed() {

            // given: recruitDeadline을 과거(2020년)로 설정한 공고
            Long fairId = 1L;

            RecruitNotice notice = RecruitNotice.builder()
                    .recruitNoticeId(1L)
                    .fairId(fairId)
                    .writerId(1L)
                    .title("마감된 공고")
                    .content("내용")
                    .recruitDeadline(LocalDateTime.of(2020, 1, 1, 0, 0))
                    .build();

            FairStatusInfo fairStatus = createFairStatus(null, "IN_PROGRESS");

            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(notice);
            given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(fairStatus);

            // when
            RecruitNoticeResponse result = recruitNoticeService.getNotice(fairId);

            // then: 마감일 지남 조건 하나만으로도 closed=true여야 함
            assertThat(result.isClosed()).isTrue();

        }

        @Test
        @DisplayName("행사가 취소됐으면 마감일이 남았어도 closed=true로 응답한다")
        void returnsClosedTrueWhenFairCanceled() {

            // given: 마감일은 미래지만, fairs.canceled_at에 값이 있는(취소된) 행사
            Long fairId = 1L;

            RecruitNotice notice = createNotice(1L, fairId, 1L, "취소된 행사의 공고", null);
            FairStatusInfo fairStatus = createFairStatus(LocalDateTime.of(2026, 8, 1, 10, 0),
                    "IN_PROGRESS");

            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(notice);
            given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(fairStatus);

            // when
            RecruitNoticeResponse result = recruitNoticeService.getNotice(fairId);

            // then: 마감일과 무관하게, 행사 취소만으로도 closed=true여야 함
            assertThat(result.isClosed()).isTrue();

        }

        @Test
        @DisplayName("행사가 종료(ENDED)됐으면 마감일이 남았어도 closed=true로 응답한다")
        void returnsClosedTrueWhenFairEnded() {

            // given: 마감일은 미래지만, fairs.status가 ENDED인 행사
            Long fairId = 1L;

            RecruitNotice notice = createNotice(1L, fairId, 1L, "종료된 행사의 공고", null);
            FairStatusInfo fairStatus = createFairStatus(null, "ENDED");

            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(notice);
            given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(fairStatus);

            // when
            RecruitNoticeResponse result = recruitNoticeService.getNotice(fairId);

            // then: 마감일과 무관하게, 행사 종료만으로도 closed=true여야 함
            assertThat(result.isClosed()).isTrue();

        }

        @Test
        @DisplayName("공고가 없으면 예외를 던진다")
        void throwsWhenNoticeNotFound() {

            // given: 이 fairId에 대해 아직 작성된 공고가 없는 상황(Mapper가 null 리턴)
            Long fairId = 999L;

            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> recruitNoticeService.getNotice(fairId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("아직 작성된 모집 공고가 없습니다");

            // 공고 자체가 없어 예외로 끝났으니, 그 뒤 fairs 상태 조회는 시도되면 안 됨
            verify(recruitNoticeMapper, never()).selectFairStatusByFairId(any());

        }

        @Test
        @DisplayName("부스 슬롯 현황(상태 3단계 + 확정 업체명)을 응답에 담아 반환한다")
        void includesBoothSlotStatusesInResponse() {

            // given: 공고/행사는 정상, 부스 슬롯은 AVAILABLE/PENDING/CONFIRMED 각각 하나씩
            Long fairId = 1L;

            RecruitNotice notice = createNotice(1L, fairId, 1L, "멍냥페스타 참가업체 모집", null);
            FairStatusInfo fairStatus = createFairStatus(null, "IN_PROGRESS");

            List<BoothSlotStatusResponse> boothSlots = List.of(
                    BoothSlotStatusResponse.builder()
                            .boothSlotsId(501L).slotNumber("A-01").price(450000L)
                            .status("AVAILABLE").businessName(null)
                            .build(),
                    BoothSlotStatusResponse.builder()
                            .boothSlotsId(502L).slotNumber("A-02").price(450000L)
                            .status("PENDING").businessName(null)
                            .build(),
                    BoothSlotStatusResponse.builder()
                            .boothSlotsId(503L).slotNumber("A-03").price(450000L)
                            .status("CONFIRMED").businessName("멍냥사료")
                            .build()
            );

            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(notice);
            given(recruitNoticeMapper.selectFairStatusByFairId(fairId)).willReturn(fairStatus);
            given(recruitNoticeMapper.selectBoothSlotStatusesByFairId(fairId)).willReturn(boothSlots);

            // when
            RecruitNoticeResponse result = recruitNoticeService.getNotice(fairId);

            // then: 매퍼가 리턴한 슬롯 현황이 응답에 그대로 담기는지 확인
            assertThat(result.getBoothSlots()).hasSize(3);
            assertThat(result.getBoothSlots().get(0).getStatus()).isEqualTo("AVAILABLE");
            assertThat(result.getBoothSlots().get(1).getStatus()).isEqualTo("PENDING");
            assertThat(result.getBoothSlots().get(2).getStatus()).isEqualTo("CONFIRMED");
            assertThat(result.getBoothSlots().get(2).getBusinessName()).isEqualTo("멍냥사료");

        }

    }

}
