package com.ms.petopia.api.popup.service;

import com.ms.petopia.api.popup.domain.Popup;
import com.ms.petopia.api.popup.dto.request.PopupCreateRequest;
import com.ms.petopia.api.popup.dto.request.PopupUpdateRequest;
import com.ms.petopia.api.popup.dto.response.PopupResponse;
import com.ms.petopia.api.popup.mapper.PopupMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PopupService {
    private final PopupMapper popupMapper;
    private final StorageService storageService;

    // 공개
    public List<PopupResponse> getActiveList(){
        return popupMapper.selectActiveList().stream()
                .map(PopupResponse::from)
                .toList();
    }

    // 관리자
    // 전체 팝업 목록
    public List<PopupResponse> getAll(){
        return popupMapper.selectAll().stream()
                .map(PopupResponse::from)
                .toList();
    }

    // 단건 조회
    public PopupResponse getById(Long popupId){
        Popup popup = findOrThrow(popupId);
        return PopupResponse.from(popup);
    }

    // 등록
    @Transactional
    public PopupResponse create(Long callerId, PopupCreateRequest request){
        Popup popup = Popup.builder()
                .title(request.getTitle())
                .subtitle(clearable(request.getSubtitle()))
                .imageKey(resolveImageUrl(request.getImageKey()))
                .linkUrl(clearable(request.getLinkUrl()))
                .linkTarget(request.getLinkTarget())
                .linkLabel(clearable(request.getLinkLabel()))
                .bgColor(clearable(request.getBgColor()))
                .width(request.getWidth())
                .height(request.getHeight())
                .isActive(true)
                .startedAt(request.getStartedAt())
                .endedAt(request.getEndedAt())
                .createdAt(LocalDateTime.now())
                .createdBy(callerId)
                .build();
        popupMapper.insert(popup);
        return PopupResponse.from(popupMapper.selectById(popup.getPopupId()));
    }

    // 수정
    @Transactional
    public PopupResponse update(Long popupId, PopupUpdateRequest request){
        Popup existing = findOrThrow(popupId);
        boolean hasChanges = request.getTitle() != null || request.getSubtitle() != null
                || request.getImageKey() != null
                || request.getLinkUrl() != null || request.getLinkTarget() != null
                || request.getLinkLabel() != null || request.getBgColor() != null
                || request.getWidth() != null || request.getHeight() != null
                || request.getStartedAt() != null || request.getEndedAt() != null;
        if (hasChanges) {
            String subtitle = clearable(request.getSubtitle());
            String imageKey = resolveImageUrl(request.getImageKey());
            if (!hasContent(imageKey, existing.getImageKey()) && !hasContent(subtitle, existing.getSubtitle())) {
                throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
            }

            Popup patch = Popup.builder()
                    .popupId(popupId)
                    .title(request.getTitle())
                    .subtitle(subtitle)
                    .imageKey(imageKey)
                    .linkUrl(clearable(request.getLinkUrl()))
                    .linkTarget(request.getLinkTarget())
                    .linkLabel(clearable(request.getLinkLabel()))
                    .bgColor(clearable(request.getBgColor()))
                    .width(request.getWidth())
                    .height(request.getHeight())
                    .startedAt(request.getStartedAt())
                    .endedAt(request.getEndedAt())
                    .build();
            popupMapper.update(patch);
        }
        return PopupResponse.from(popupMapper.selectById(popupId));
    }

    // patch 값(생략=null이면 기존 값 사용)이 이미지/본문 중 하나라도 남아있는지 확인한다.
    private boolean hasContent(String patched, String existingValue) {
        String resolved = patched != null ? patched : existingValue;
        return resolved != null && !resolved.isEmpty();
    }

    // 삭제
    @Transactional
    public void delete(Long popupId) {
        findOrThrow(popupId);
        popupMapper.delete(popupId);
    }

    // 노출 토글
    @Transactional
    public void toggleActive(Long popupId, boolean isActive) {
        findOrThrow(popupId);
        popupMapper.updateActive(popupId, isActive);
    }

    private Popup findOrThrow(Long popupId) {
        Popup popup = popupMapper.selectById(popupId);
        if (popup == null) {
            throw new CommonException(ErrorCode.POPUP_NOT_FOUND);
        }
        return popup;
    }

    // null(생략)은 그대로 두고, 공백만 입력된 경우는 명시적 삭제 신호(빈 문자열)로 정규화한다.
    // 매퍼는 빈 문자열을 컬럼 NULL로, null은 미변경으로 해석한다.
    private String clearable(String raw) {
        return raw == null ? null : raw.trim();
    }

    private String resolveImageUrl(String temporaryObjectKey) {
        if (temporaryObjectKey == null) {
            return null;
        }
        String trimmed = temporaryObjectKey.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String confirmedKey = storageService.confirm(trimmed, UploadPolicy.IMAGE);
        return storageService.toPublicUrl(confirmedKey);
    }
}
