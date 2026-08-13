package com.ms.petopia.api.review.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.review.dto.FairReviewListItemResponse;
import com.ms.petopia.api.review.dto.FairReviewListResponse;
import com.ms.petopia.api.review.dto.FairReviewSummaryResponse;
import com.ms.petopia.api.review.dto.FairReviewSummaryRow;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 리뷰 조회 전용 서비스. 쓰기(FairReviewService)와 분리한다 - NotificationQueryService/
 * NotificationService 분리와 같은 패턴.
 */
@Service
@RequiredArgsConstructor
public class FairReviewQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final FairReviewMapper fairReviewMapper;
    private final FairMapper fairMapper;

    /** 한 행사의 리뷰 목록(공개, 로그인 불필요). page/size는 NotificationQueryService와 같은 규약을 쓴다. */
    @Transactional(readOnly = true)
    public FairReviewListResponse getReviewsByFair(Long fairId, int page, int size) {
        if (page < 0 || size <= 0 || size > MAX_PAGE_SIZE) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        requireFairExists(fairId);

        long totalElements = fairReviewMapper.countByFairId(fairId);
        int totalPages = (int) ((totalElements + size - 1) / size);
        List<FairReviewListItemResponse> items = fairReviewMapper
                .selectListByFairId(fairId, (long) page * size, size)
                .stream()
                .map(FairReviewListItemResponse::from)
                .toList();
        return new FairReviewListResponse(items, page, size, totalElements, totalPages, page + 1 < totalPages);
    }

    /** 한 행사의 평점 요약(평균·개수, 공개, 로그인 불필요). */
    @Transactional(readOnly = true)
    public FairReviewSummaryResponse getSummaryByFair(Long fairId) {
        requireFairExists(fairId);
        FairReviewSummaryRow row = fairReviewMapper.selectSummaryByFairId(fairId);
        return FairReviewSummaryResponse.from(fairId, row);
    }

    private void requireFairExists(Long fairId) {
        Fair fair = fairMapper.selectById(fairId);
        if (fair == null) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }
    }
}
