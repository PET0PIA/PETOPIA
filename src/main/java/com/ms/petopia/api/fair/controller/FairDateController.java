package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.CreateFairDateRequest;
import com.ms.petopia.api.fair.dto.FairDateResponse;
import com.ms.petopia.api.fair.dto.UpdateFairDateRequest;
import com.ms.petopia.api.fair.service.FairDateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fairs/{fairId}/fair-dates")
@RequiredArgsConstructor
public class FairDateController {

    private final FairDateService fairDateService;

    @PostMapping
    public ResponseEntity<FairDateResponse> createFairDate(
            @PathVariable Long fairId,
            @RequestBody CreateFairDateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fairDateService.create(fairId, request));
    }

    @GetMapping
    public ResponseEntity<List<FairDateResponse>> getFairDates(@PathVariable Long fairId) {
        return ResponseEntity.ok(fairDateService.getFairDates(fairId));
    }

    @PutMapping("/{fairDateId}")
    public ResponseEntity<FairDateResponse> updateFairDate(
            @PathVariable Long fairId,
            @PathVariable Long fairDateId,
            @RequestBody UpdateFairDateRequest request
    ) {
        return ResponseEntity.ok(fairDateService.update(fairId, fairDateId, request));
    }

    @DeleteMapping("/{fairDateId}")
    public ResponseEntity<Void> deleteFairDate(
            @PathVariable Long fairId,
            @PathVariable Long fairDateId
    ) {
        fairDateService.delete(fairId, fairDateId);
        return ResponseEntity.noContent().build();
    }
}
