package com.ms.petopia.api.pet.controller;

import com.ms.petopia.api.pet.dto.PetCreateRequest;
import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.pet.dto.PetUpdateRequest;
import com.ms.petopia.api.pet.service.PetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/pets")
public class PetController {

    private final PetService petService;

    //내 반려동물 목록 조회
    @GetMapping
    public List<PetResponse> getMyPets(@AuthenticationPrincipal Long userId) {
        return petService.getMyPets(userId);
    }

    //반려동물 등록
    @PostMapping
    public ResponseEntity<PetResponse> createPet(@AuthenticationPrincipal Long userId, @Valid @RequestBody PetCreateRequest request) {
        PetResponse response = petService.createPet(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //반려동물 상세 조회
    @GetMapping("/{petId}")
    public PetResponse getPet(@AuthenticationPrincipal Long userId, @PathVariable Long petId) {
        return petService.getPet(userId, petId);
    }

    //반려동물 부분 수정
    @PatchMapping("/{petId}")
    public PetResponse updatePet(@AuthenticationPrincipal Long userId, @PathVariable Long petId, @Valid @RequestBody PetUpdateRequest request) {
        return petService.updatePet(userId, petId, request);
    }

    //반려동물 삭제
    @DeleteMapping("/{petId}")
    public ResponseEntity<Void> deletePet(@AuthenticationPrincipal Long userId, @PathVariable Long petId) {
        petService.deletePet(userId, petId);
        return ResponseEntity.noContent().build();
    }
}
