package com.ms.petopia.api.recruitnotice.controller;

import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeResponse;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeUpsertResponse;
import com.ms.petopia.api.recruitnotice.service.RecruitNoticeService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * RecruitNoticeController 통합 테스트. RecruitNoticeService는 Mock으로 대체하고,
 * standaloneSetup + GlobalExceptionHandler로 실제 라우팅/@Valid 검증/
 * 예외→HTTP 상태코드 매핑까지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RecruitNoticeControllerTest {

    @Mock
    private RecruitNoticeService recruitNoticeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(new RecruitNoticeController(recruitNoticeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

    }

    // PUT /api/fairs/{fairId}/recruit-notice - 정상 작성/수정
    @Test
    void upsertsRecruitNotice() throws Exception {

        given(recruitNoticeService.upsertNotice(eq(1L), eq(1L), any())).willReturn(
                RecruitNoticeUpsertResponse.builder()
                        .recruitNoticeId(1L)
                        .fairId(1L)
                        .title("펫페어 참가업체 모집")
                        .recruitDeadline(LocalDateTime.of(2026, 9, 1, 0, 0))
                        .build());

        mockMvc.perform(put("/api/fairs/1/recruit-notice")
                        .header(RecruitNoticeTemporaryAuthHeaders.USER_ID, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"펫페어 참가업체 모집\",\"content\":\"신청 받습니다\",\"recruitDeadline\":\"2026-09-01T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("펫페어 참가업체 모집"));

    }

    // PUT /api/fairs/{fairId}/recruit-notice - title 빈 값 -> @NotBlank가 400으로 막는지 확인
    @Test
    void returns400WhenTitleBlank() throws Exception {

        mockMvc.perform(put("/api/fairs/1/recruit-notice")
                        .header(RecruitNoticeTemporaryAuthHeaders.USER_ID, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"신청 받습니다\",\"recruitDeadline\":\"2026-09-01T00:00:00\"}"))
                .andExpect(status().isBadRequest());

    }

    // PUT /api/fairs/{fairId}/recruit-notice - recruitDeadline 누락 -> @NotNull이 400으로 막는지 확인
    @Test
    void returns400WhenRecruitDeadlineMissing() throws Exception {

        mockMvc.perform(put("/api/fairs/1/recruit-notice")
                        .header(RecruitNoticeTemporaryAuthHeaders.USER_ID, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"펫페어 참가업체 모집\",\"content\":\"신청 받습니다\"}"))
                .andExpect(status().isBadRequest());

    }

    // PUT /api/fairs/{fairId}/recruit-notice - 본인 소유(담당) 행사 아님 -> 403 + V002
    @Test
    void returns403WhenNotNoticeOwner() throws Exception {

        willThrow(new CommonException(ErrorCode.RECRUIT_NOTICE_ACCESS_DENIED))
                .given(recruitNoticeService).upsertNotice(eq(1L), eq(2L), any());

        mockMvc.perform(put("/api/fairs/1/recruit-notice")
                        .header(RecruitNoticeTemporaryAuthHeaders.USER_ID, 2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"펫페어 참가업체 모집\",\"content\":\"신청 받습니다\",\"recruitDeadline\":\"2026-09-01T00:00:00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V002"));

    }

    // GET /api/fairs/{fairId}/recruit-detail - 정상 조회 (인증 불필요)
    @Test
    void getsRecruitNoticeDetail() throws Exception {

        given(recruitNoticeService.getNotice(1L)).willReturn(
                RecruitNoticeResponse.builder()
                        .recruitNoticeId(1L)
                        .fairId(1L)
                        .title("펫페어 참가업체 모집")
                        .content("신청 받습니다")
                        .closed(false)
                        .boothSlots(List.of())
                        .build());

        mockMvc.perform(get("/api/fairs/1/recruit-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("펫페어 참가업체 모집"))
                .andExpect(jsonPath("$.data.closed").value(false));

    }

    // GET /api/fairs/{fairId}/recruit-detail - 공고 없음 -> 404 + V004
    @Test
    void returns404WhenRecruitNoticeNotFound() throws Exception {

        willThrow(new CommonException(ErrorCode.RECRUIT_NOTICE_NOT_FOUND))
                .given(recruitNoticeService).getNotice(999L);

        mockMvc.perform(get("/api/fairs/999/recruit-detail"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V004"));

    }

}