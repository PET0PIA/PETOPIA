package com.ms.petopia.api.pet.controller;

import com.ms.petopia.api.pet.dto.PetAllergyTypeResponse;
import com.ms.petopia.api.pet.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 알레르기 유형 마스터 조회 API.
 *
 * <p>인증을 요구하지 않는다. SecurityConfig의 기본값이 {@code anyRequest().permitAll()}이라
 * 새 경로는 별도 설정 없이 열리고, 알레르기 항목 목록은 특정 사용자의 정보가 아니라
 * 선택지 그 자체라 공개여도 무방하다(리뷰 태그 목록 조회와 같은 성격).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pet-allergy-types")
public class PetAllergyTypeController {

    private final PetService petService;

    /** 활성 알레르기 항목 목록. 등록·수정 화면의 선택지로 쓴다. */
    @GetMapping
    public List<PetAllergyTypeResponse> getAllergyTypes() {
        return petService.getAllergyTypes();
    }
}
