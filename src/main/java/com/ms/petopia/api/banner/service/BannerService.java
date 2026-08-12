package com.ms.petopia.api.banner.service;

import com.ms.petopia.api.banner.domain.Banner;
import com.ms.petopia.api.banner.dto.request.BannerCreateRequest;
import com.ms.petopia.api.banner.dto.request.BannerOrderRequest;
import com.ms.petopia.api.banner.dto.request.BannerUpdateRequest;
import com.ms.petopia.api.banner.dto.response.BannerResponse;
import com.ms.petopia.api.banner.mapper.BannerMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerMapper bannerMapper;

    // 공개 - 현재 노출 중인 배너 목록
    public List<BannerResponse> getActiveList(){
        return bannerMapper.selectActiveList().stream()
                .map(BannerResponse::from)
                .toList();
    }

    // 관리자 - 전체 배너 목록
    public List<BannerResponse> getAll(){
        return bannerMapper.selectAll().stream()
                .map(BannerResponse::from)
                .toList();
    }

    // 관리자 - 단건 조회
    public BannerResponse getById(Long bannerId){
        Banner banner = findOrThrow(bannerId);
        return BannerResponse.from(banner);
    }


    // 관리자 - 등록
    @Transactional
    public BannerResponse create(Long callerId, BannerCreateRequest request){
        Banner banner = Banner.builder()
                .title(request.getTitle())
                .imageKey(request.getTitle())
                .linkUrl(request.getLinkUrl())
                .linkTarget(request.getLinkTarget())
                .sortOrder(request.getSortOrder())
                .isActive(true)
                .startedAt(request.getStartedAt())
                .endedAt(request.getEndedAt())
                .createdAt(request.getEndedAt())
                .createdBy(callerId)
                .build();
        bannerMapper.insert(banner);
        return BannerResponse.from(bannerMapper.selectById(banner.getBannerId()));
    }

    // 관리자 - 수정
    @Transactional
    public BannerResponse update(Long bannerId, BannerUpdateRequest request){
        findOrThrow(bannerId);

        boolean hasChanges = request.getTitle() != null || request.getImageKey() != null
                || request.getLinkUrl() != null || request.getLinkTarget() != null
                || request.getSortOrder() != null || request.getStartedAt() != null
                || request.getEndedAt() != null;
        if (hasChanges){
            Banner patch = Banner.builder()
                    .bannerId(bannerId)
                    .title(request.getTitle())
                    .imageKey(request.getImageKey())
                    .linkUrl(request.getLinkUrl())
                    .linkTarget(request.getLinkTarget())
                    .sortOrder(request.getSortOrder())
                    .startedAt(request.getStartedAt())
                    .endedAt(request.getEndedAt())
                    .build();
            bannerMapper.update(patch);
        }
        return BannerResponse.from(bannerMapper.selectById(bannerId));
    }

    // 관리자 - 삭제
    @Transactional
    public void delete(Long bannerId){
        findOrThrow(bannerId);
        bannerMapper.delete(bannerId);
    }

    // 관리자 - 노출 토글
    @Transactional
    public void toggleActive(Long bannerId, boolean isActive){
        findOrThrow(bannerId);
        bannerMapper.updateActive(bannerId, isActive);
    }

    // 관리자 - 순서 일관 변경
    @Transactional
    public void updateOrder(BannerOrderRequest request){
        List<Long> ids = request.getBannerIds();
        for(int i=0; i < ids.size(); i++){
            bannerMapper.updateOrder(ids.get(i), i);
        }
    }

    private Banner findOrThrow(Long bannerId){
        Banner banner = bannerMapper.selectById(bannerId);
        if(banner == null){
            throw new CommonException(ErrorCode.BANNER_NOT_FOUND);
        }
        return banner;
    }
}
