package com.ms.petopia.api.booth.service;

import com.ms.petopia.api.booth.domain.Booth;
import com.ms.petopia.api.booth.domain.BoothItem;
import com.ms.petopia.api.booth.dto.request.BoothItemCreateRequest;
import com.ms.petopia.api.booth.dto.request.BoothItemUpdateRequest;
import com.ms.petopia.api.booth.dto.request.BoothUpdateRequest;
import com.ms.petopia.api.booth.dto.response.BoothFavoriteResponse;
import com.ms.petopia.api.booth.dto.response.BoothItemResponse;
import com.ms.petopia.api.booth.dto.response.BoothResponse;
import com.ms.petopia.api.booth.dto.response.ConfirmedBoothResponse;
import com.ms.petopia.api.booth.mapper.BoothMapper;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoothService {

    private final BoothMapper boothMapper;
    private final BusinessMapper businessMapper;
    private final StorageService storageService;

    /*
     * 부스 상세 조회 (비회원 포함 공개). viewerId는 로그인 사용자면 채워지고, 비로그인이면
     * null — null이면 즐겨찾기 여부를 조회할 필요가 없으니 favorited는 항상 false로 내려간다.
     */
    public BoothResponse getBooth(Long boothId, Long viewerId) {

        // 부스 존재 확인
        Booth booth = boothMapper.selectById(boothId);

        if(booth == null) {
            throw new CommonException(ErrorCode.BOOTH_NOT_FOUND);
        }

        // 부스에 등록된 판매상품·이벤트 목록을 같이 내려준다
        List<BoothItemResponse> items = boothMapper.selectItemsByBoothId(boothId).stream()
                .map(BoothItemResponse::from)
                .toList();

        // 즐겨찾기 여부
        boolean favorited = viewerId != null && boothMapper.existsFavorite(viewerId, boothId);

        return BoothResponse.from(booth, items, favorited);

    }

    /*
     * 부스 프로필을 수정한다. 본인 소유(부스가 속한 사업자의 owner)만 가능하고,
     * 요청에서 값을 보낸 필드만 갱신한다(null은 "안 바꿈"으로 취급 - 부분 업데이트).
     */
    @Transactional
    public BoothResponse updateBooth(Long callerId, Long boothId, BoothUpdateRequest request) {

        verifyOwner(callerId, boothId);

        /*
         * 요청에 담긴 값만으로 patch 객체를 만든다 - null인 필드는 XML의 <if>가 걸러내서
         * 실제 UPDATE의 SET 절에 포함되지 않는다(=기존 값 유지)
         */
        Booth patch = Booth.builder()
                .boothId(boothId)
                .name(request.getName())
                .intro(request.getIntro())
                .category(request.getCategory())
                .targetAnimal(request.getTargetAnimal())
                .imageUrl(resolveImageUrl(request.getImageObjectKey()))
                .build();

        // 갱신할 필드가 하나도 없으면 UPDATE를 건너뛴다(<set>이 비어 SQL 문법 오류가 나는 걸 방지)
        boolean hasChanges = patch.getName() != null || patch.getIntro() != null
                || patch.getCategory() != null || patch.getTargetAnimal() != null
                || patch.getImageUrl() != null;

        if (hasChanges) {
            boothMapper.updateBooth(patch);
        }

        // 갱신된 최신 상태를 다시 조회해서 응답한다 (patch 객체엔 안 바뀐 필드가 비어있어서 그대로 응답하면 안 됨)
        Booth updated = boothMapper.selectById(boothId);

        // 프로필 수정 응답이라 상품 목록까지는 필요 없어 items는 비워서 반환(null)
        return BoothResponse.from(updated, null);

    }

    // 내 즐겨찾기 목록 조회
    public List<BoothFavoriteResponse> getMyFavorites(Long userId) {

        return boothMapper.selectFavoritesByUserId(userId);

    }

    /*
     * 즐겨찾기 추가. 부스 존재 확인 후 insert — INSERT IGNORE라 이미 즐겨찾기한 부스를
     * 다시 눌러도(중복 클릭) 에러 없이 조용히 통과한다(멱등).
     */
    public void addFavorite(Long userId, Long boothId) {

        if(boothMapper.selectById(boothId) == null) {
            throw new CommonException(ErrorCode.BOOTH_NOT_FOUND);
        }

        boothMapper.insertFavorite(userId, boothId);

    }

    /*
     * 판매상품·이벤트를 등록한다. 본인 소유(부스가 속한 사업자의 owner) 부스만 가능하다.
     * 이름 중복은 의도적으로 막지 않는다 - 사업자가 같은 이름으로 여러 건(다른 배치 등)
     * 등록하고 싶을 수 있어서, 중복 체크 대신 프론트의 이중 클릭 방지에 맡긴다.
     */
    @Transactional
    public BoothItemResponse addItem(Long callerId, Long boothId, BoothItemCreateRequest request) {

        verifyOwner(callerId, boothId);

        BoothItem item = BoothItem.builder()
                .boothId(boothId)
                .name(request.getName())
                .type(request.getType())
                .imageUrl(resolveImageUrl(request.getImageObjectKey()))
                .note(request.getNote())
                .build();

        boothMapper.insertBoothItem(item);

        // insertBoothItem이 useGeneratedKeys로 item.boothItemId를 채워준 상태라 재조회 없이 바로 응답 가능
        return BoothItemResponse.from(item);

    }

    /*
     * 판매상품·이벤트를 수정한다. boothItemId만으로 들어오는 요청이라(booth 소속 정보가
     * 경로에 없음), 먼저 상품을 조회해서 소속 boothId를 알아낸 다음 소유권을 확인한다.
     * 보낸 필드만 갱신(부분 업데이트) - updateBooth와 동일한 패턴.
     */
    @Transactional
    public BoothItemResponse updateItem(Long callerId, Long boothItemId, BoothItemUpdateRequest request) {

        // 상품 존재 확인 + 소속 boothId 확보
        BoothItem item = boothMapper.selectItemById(boothItemId);

        if(item == null) {
            throw new CommonException(ErrorCode.BOOTH_ITEM_NOT_FOUND);
        }

        // 그 상품이 속한 부스의 소유권 확인
        verifyOwner(callerId, item.getBoothId());

        BoothItem patch = BoothItem.builder()
                .boothItemId(boothItemId)
                .name(request.getName())
                .type(request.getType())
                .imageUrl(resolveImageUrl(request.getImageObjectKey()))
                .note(request.getNote())
                .build();

        // 갱신할 필드가 하나도 없으면 UPDATE를 건너뛴다(<set>이 비어 SQL 문법 오류가 나는 걸 방지)
        boolean hasChanges = patch.getName() != null || patch.getType() != null
                || patch.getImageUrl() != null || patch.getNote() != null;

        if (hasChanges) {
            boothMapper.updateBoothItem(patch);
        }

        // 갱신된 최신 상태를 다시 조회해서 응답한다 (patch 객체엔 안 바뀐 필드가 비어있음)
        BoothItem updated = boothMapper.selectItemById(boothItemId);

        return BoothItemResponse.from(updated);

    }

    // 판매상품·이벤트 삭제 (본인 소유 부스만)
    @Transactional
    public void deleteItem(Long callerId, Long boothItemId) {

        // 상품 존재 확인 + 소속 boothId 확보
        BoothItem item = boothMapper.selectItemById(boothItemId);

        if(item == null) {
            throw new CommonException(ErrorCode.BOOTH_ITEM_NOT_FOUND);
        }

        // 부스 소유권 확인
        verifyOwner(callerId, item.getBoothId());

        boothMapper.deleteBoothItem(boothItemId);

    }

    /*
     * 부스 존재 + 소유권(부스가 속한 사업자의 owner가 요청자 본인인지) 확인 공용 헬퍼.
     * updateBooth/addItem이 공유한다(상품 수정·삭제도 이어서 이 헬퍼를 재사용할 예정).
     */
    private void verifyOwner(Long callerId, Long boothId) {

        // 부스 존재 확인
        Booth booth = boothMapper.selectById(boothId);

        if(booth == null) {
            throw new CommonException(ErrorCode.BOOTH_NOT_FOUND);
        }

        // 본인 소유인지 확인
        Business business = businessMapper.selectById(booth.getBusinessId());

        if(business == null || !business.getOwnerId().equals(callerId)) {
            throw new CommonException(ErrorCode.BOOTH_ACCESS_DENIED);
        }

    }

    /*
     * presigned 업로드로 받은 임시 객체 키를 확정(tmp -> uploads)하고 공개 URL로 바꾼다.
     * 키가 없으면(이미지를 안 바꾸는 경우) null을 그대로 반환한다 - 부분 업데이트에서
     * null은 "이 필드는 갱신 안 함"을 뜻하므로 자연스럽게 기존 이미지가 유지된다.
     */
    private String resolveImageUrl(String temporaryObjectKey) {

        if(temporaryObjectKey == null || temporaryObjectKey.isBlank()) {
            return null;
        }

        String confirmedKey = storageService.confirm(temporaryObjectKey, UploadPolicy.IMAGE);

        return storageService.toPublicUrl(confirmedKey);

    }

    // 확정 부스 안내판 조회 (비회원 포함 공개)
    public List<ConfirmedBoothResponse> getConfirmedBooths(Long fairId) {

        if(!boothMapper.existsFair(fairId)) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }

        return boothMapper.selectConfirmedBooths(fairId);

    }



}
