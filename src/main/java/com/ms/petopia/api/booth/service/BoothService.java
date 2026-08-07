package com.ms.petopia.api.booth.service;

import com.ms.petopia.api.booth.domain.Booth;
import com.ms.petopia.api.booth.dto.response.BoothItemResponse;
import com.ms.petopia.api.booth.dto.response.BoothResponse;
import com.ms.petopia.api.booth.mapper.BoothMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoothService {

    private final BoothMapper boothMapper;

    // 부스 상세 조회 (비회원 포함 공개)
    public BoothResponse getBooth(Long boothId) {

        Booth booth = boothMapper.selectById(boothId);

        if(booth == null) {
            throw new CommonException(ErrorCode.BOOTH_NOT_FOUND);
        }

        List<BoothItemResponse> items = boothMapper.selectItemsByBoothId(boothId).stream()
                .map(BoothItemResponse::from)
                .toList();

        return BoothResponse.from(booth, items);

    }

}
