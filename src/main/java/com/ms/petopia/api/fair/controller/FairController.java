package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.CreateFairApplicationRequest;
import com.ms.petopia.api.fair.dto.CreateFairApplicationResponse;
import com.ms.petopia.api.fair.dto.FairApplicationDetailResponse;
import com.ms.petopia.api.fair.dto.FairApplicationSummaryResponse;
import com.ms.petopia.api.fair.dto.FairPublicSummaryResponse;
import com.ms.petopia.api.fair.dto.PublishFairResponse;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationRequest;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationResponse;
import com.ms.petopia.api.fair.dto.UpdateFairApplicationRequest;
import com.ms.petopia.api.fair.service.FairService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 요청자 식별은 전부 {@code @AuthenticationPrincipal}(JwtAuthenticationFilter가 심어주는 userId)로
 * 받는다. review/publish/getApplication은 SecurityConfig에서 SUPER_ADMIN role로, getPublicSummary는
 * 인증 없이 permitAll로, 나머지는 로그인 여부만 검증한다({@code SecurityConfig}의 "Fair 도메인"
 * 섹션 참고) - 본인 신청 여부처럼 role만으로 못 가리는 검증은 지금처럼 서비스 계층(FairService)이
 * 계속 담당한다.
 */
@RestController
@RequestMapping("/api/fairs")
@RequiredArgsConstructor
public class FairController {

    private final FairService fairService;

    @PostMapping
    public ResponseEntity<CreateFairApplicationResponse> createApplication(
            @AuthenticationPrincipal Long userId,
            @RequestBody CreateFairApplicationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fairService.createApplication(userId, request));
    }

    @GetMapping("/mine")
    public List<FairApplicationSummaryResponse> getMyApplications(
            @AuthenticationPrincipal Long requesterId
    ) {
        return fairService.getMyApplications(requesterId);
    }

    @GetMapping("/{fairId}/mine")
    public FairApplicationDetailResponse getMyApplicationDetail(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long requesterId
    ) {
        return fairService.getMyApplicationDetail(fairId, requesterId);
    }

    @GetMapping("/{fairId}/public")
    public FairPublicSummaryResponse getPublicSummary(@PathVariable Long fairId) {
        // SecurityConfig에서 이 경로는 인증 없이 permitAll이다 - 티켓 예매 화면처럼 로그인
        // 여부와 무관하게 볼 수 있어야 하는 화면 전용(PII·심사 정보는 응답에 없음).
        return fairService.getPublicSummary(fairId);
    }

    @GetMapping("/{fairId}")
    public FairApplicationDetailResponse getApplication(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long requesterId
    ) {
        // SecurityConfig에서 SUPER_ADMIN role만 이 엔드포인트에 도달할 수 있게 막는다
        // (관리자 검토 화면 전용, managerPhone/managerEmail 노출).
        return fairService.getApplication(fairId, requesterId);
    }

    /**
     * 요청 본문을 타입 있는 DTO 대신 {@code Map}으로 받는다 - PATCH는 "필드를 생략함(기존 값
     * 유지)"과 "필드를 명시적으로 null로 보냄(지움)"을 구분해야 하는데, DTO로 바로 역직렬화하면
     * 두 경우 모두 그냥 null이 되어 구분이 사라진다. {@code rawBody.keySet()}으로 요청 JSON에
     * 실제로 있었던 필드명을 알아내 {@link FairService#updateApplication}에 함께 넘긴다.
     * {@code rawBody} 자체는 {@link #toUpdateRequest}가 타입 있는 DTO로 직접 변환한다(Jackson
     * {@code ObjectMapper}를 이 모듈 컴파일 클래스패스에서 직접 쓸 수 없어 - webmvc 스타터가
     * jackson-databind를 컴파일 타임에 노출하지 않는다 - 수동으로 변환한다).
     */
    @PatchMapping("/{fairId}")
    public FairApplicationDetailResponse updateApplication(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long requesterId,
            @RequestBody Map<String, Object> rawBody
    ) {
        UpdateFairApplicationRequest request = toUpdateRequest(rawBody);
        return fairService.updateApplication(fairId, requesterId, request, rawBody.keySet());
    }

    private UpdateFairApplicationRequest toUpdateRequest(Map<String, Object> rawBody) {
        try {
            return new UpdateFairApplicationRequest(
                    asString(rawBody.get("name")),
                    asString(rawBody.get("description")),
                    asString(rawBody.get("category")),
                    asString(rawBody.get("posterImageObjectKey")),
                    asString(rawBody.get("noticeText")),
                    asString(rawBody.get("placeName")),
                    asString(rawBody.get("address")),
                    asString(rawBody.get("indoorOutdoor")),
                    asDate(rawBody.get("vendorRecruitStartDate")),
                    asDate(rawBody.get("vendorRecruitEndDate")),
                    asDate(rawBody.get("reservationStartDate")),
                    asDate(rawBody.get("reservationEndDate")),
                    asDate(rawBody.get("operationStartDate")),
                    asDate(rawBody.get("operationEndDate")),
                    asLong(rawBody.get("reservationFee")),
                    asInteger(rawBody.get("reservationCancelDeadlineHours")),
                    asInteger(rawBody.get("reservationChangeDeadlineHours")),
                    asString(rawBody.get("managerName")),
                    asString(rawBody.get("managerPhone")),
                    asString(rawBody.get("managerEmail"))
            );
        } catch (RuntimeException e) {
            // 필드 타입이 안 맞는 값(예: 숫자 필드에 문자열)이 오면 400으로 응답한다.
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : (String) value;
    }

    private static LocalDate asDate(Object value) {
        return value == null ? null : LocalDate.parse((String) value);
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer asInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    @PatchMapping("/{fairId}/review")
    public ReviewFairApplicationResponse reviewApplication(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long reviewerId,
            @RequestBody ReviewFairApplicationRequest request
    ) {
        // SecurityConfig에서 SUPER_ADMIN role만 이 엔드포인트에 도달할 수 있게 막는다.
        return fairService.review(fairId, reviewerId, request);
    }

    @PatchMapping("/{fairId}/publish")
    public PublishFairResponse publish(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long actorId
    ) {
        // SecurityConfig에서 SUPER_ADMIN role만 이 엔드포인트에 도달할 수 있게 막는다.
        return fairService.publish(fairId, actorId);
    }
}
