package com.ms.petopia.api.recruitnotice.service;

import com.ms.petopia.api.recruitnotice.domain.FairStatusInfo;
import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import com.ms.petopia.api.recruitnotice.dto.request.RecruitNoticeRequest;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeResponse;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;

/*
 * RecruitNoticeService 단위 테스트.
 * Mapper는 Mock으로 대체하고, Service의 로직(작성자 검증, 응답 변환)만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RecruitNoticeServiceTest {

    @Mock
    private RecruitNoticeMapper recruitNoticeMapper;

    @InjectMocks
    private RecruitNoticeService recruitNoticeService;

    // 테스트용 요청 DTO를 만드는 헬퍼 메서드.
    private RecruitNoticeRequest createRequest(String title) {

        RecruitNoticeRequest request = new RecruitNoticeRequest();

        request.setTitle(title);
        request.setContent("반려동물 관련 사업자를 모집합니다.");
        request.setImageUrl("https://cdn.petopia.kr/notice/1.jpg");
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
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(null, savedNotice);
            given(recruitNoticeMapper.selectFairStatusByFairId(fairId))
                    .willReturn(createFairStatus(null, "IN_PROGRESS"));

            // when
            RecruitNoticeResponse result = recruitNoticeService.upsertNotice(fairId, writerId, request);

            // then: 새로 만들어진 공고 정보가 응답에 정확히 담겼는지 확인
            assertThat(result.getRecruitNoticeId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("멍냥페스타 참가업체 모집");
            assertThat(result.isClosed()).isFalse();

            // upsertNotice가 실제로 호출됐는지 확인
            verify(recruitNoticeMapper).upsertNotice(any(RecruitNotice.class));

        }

        @Test
        @DisplayName("담당 EVENT_ADMIN이고 본인이 작성한 기존 공고면 수정한다")
        void updatesNoticeWhenOwnerMatches() {

            // given: 요청자(1L)가 담당자이면서, 기존 공고도 본인이 쓴 상황
            Long fairId = 1L;
            Long writerId = 1L;

            RecruitNoticeRequest request = createRequest("수정된 제목");
            RecruitNotice existing = createNotice(1L, fairId, writerId,
                    "원래 제목", null);
            RecruitNotice updated = createNotice(1L, fairId, writerId,
                    "수정된 제목", LocalDateTime.of(2026, 8, 3, 15, 0));

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(writerId);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(existing, updated);
            given(recruitNoticeMapper.selectFairStatusByFairId(fairId))
                    .willReturn(createFairStatus(null, "IN_PROGRESS"));

            // when
            RecruitNoticeResponse result = recruitNoticeService.upsertNotice(fairId, writerId, request);

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
            Long writerId = 1L;

            RecruitNoticeRequest request = createRequest("담당자 없는 행사 공고 시도");

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> recruitNoticeService.upsertNotice(fairId, writerId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("담당자가 배정되지 않은 행사입니다");

            // 담당자 검증에서 막혔으니, 그 이후 로직(공고 조회·저장)은 전혀 실행되면 안 됨
            verify(recruitNoticeMapper, never()).selectByFairId(any());
            verify(recruitNoticeMapper, never()).upsertNotice(any(RecruitNotice.class));
        }


        @Test
        @DisplayName("담당 EVENT_ADMIN이 아니면 예외를 던진다")
        void throwsWhenNotFairAdmin() {

            // given: 이 행사의 진짜 담당자는 1L인데, 요청자는 2L
            Long fairId = 1L;
            Long actualAdminId = 1L;
            Long requesterId = 2L;

            RecruitNoticeRequest request = createRequest("남의 행사에 공고 작성 시도");

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(actualAdminId);

            // when & then
            assertThatThrownBy(() -> recruitNoticeService.upsertNotice(fairId, requesterId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인이 담당하는 행사가 아닙니다");

            verify(recruitNoticeMapper, never()).selectByFairId(any());
            verify(recruitNoticeMapper, never()).upsertNotice(any(RecruitNotice.class));

        }

        @Test
        @DisplayName("담당 EVENT_ADMIN이지만 다른 사람이 쓴 기존 공고면 예외를 던진다")
        void throwsWhenNotOwner() {

            /*
             * given: 요청자(2L)가 현재 이 행사의 담당자로 배정돼있지만,
             * 기존 공고는 예전 담당자(1L)가 쓴 상황(담당자가 바뀐 케이스)
             */
            Long fairId = 1L;
            Long currentAdminId = 2L;

            RecruitNoticeRequest request = createRequest("이전 담당자의 공고 수정 시도");
            RecruitNotice existing = createNotice(1L, fairId, 1L, "원래 제목", null);

            given(recruitNoticeMapper.selectAdminUserIdByFairId(fairId)).willReturn(currentAdminId);
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(existing);

            // when & then
            assertThatThrownBy(() -> recruitNoticeService.upsertNotice(fairId, currentAdminId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인이 작성한 공고만");

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
            given(recruitNoticeMapper.selectByFairId(fairId)).willReturn(null, savedNotice);
            given(recruitNoticeMapper.selectFairStatusByFairId(fairId))
                    .willReturn(createFairStatus(null, "IN_PROGRESS"));

            // ArgumentCaptor: "Service가 Mock한테 뭘 넘겼는지" 우리가 훔쳐봄 (Service가 보낸 값을 확인)
            ArgumentCaptor<RecruitNotice> captor = ArgumentCaptor.forClass(RecruitNotice.class);

            // when
            recruitNoticeService.upsertNotice(fairId, writerId, request);

            // then: upsertNotice가 호출됐는지 확인하면서, 그때 넘어온 값을 캡처해라
            verify(recruitNoticeMapper).upsertNotice(captor.capture());
            // 캡처해둔 값을 꺼내서 실제로 열어봄
            RecruitNotice passed = captor.getValue();

            assertThat(passed.getFairId()).isEqualTo(fairId);
            assertThat(passed.getWriterId()).isEqualTo(writerId);
            // 그 안의 값들이 request 내용이랑 정확히 일치하는지 확인
            assertThat(passed.getTitle()).isEqualTo("검증용 제목");
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

    }

}
