package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.CreateHallRequest;
import com.ms.petopia.api.fair.dto.HallResponse;
import com.ms.petopia.api.fair.dto.UpdateHallRequest;
import com.ms.petopia.api.fair.service.HallService;
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
@RequestMapping("/api/fairs/{fairId}/halls")
@RequiredArgsConstructor
public class HallController {

    private final HallService hallService;

    @PostMapping
    public ResponseEntity<HallResponse> createHall(
            @PathVariable Long fairId,
            @RequestBody CreateHallRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hallService.create(fairId, request));
    }

    @GetMapping
    public ResponseEntity<List<HallResponse>> getHalls(@PathVariable Long fairId) {
        return ResponseEntity.ok(hallService.getHalls(fairId));
    }

    @GetMapping("/{hallId}")
    public ResponseEntity<HallResponse> getHall(
            @PathVariable Long fairId,
            @PathVariable Long hallId
    ) {
        return ResponseEntity.ok(hallService.getHall(fairId, hallId));
    }

    @PutMapping("/{hallId}")
    public ResponseEntity<HallResponse> updateHall(
            @PathVariable Long fairId,
            @PathVariable Long hallId,
            @RequestBody UpdateHallRequest request
    ) {
        return ResponseEntity.ok(hallService.update(fairId, hallId, request));
    }

    @DeleteMapping("/{hallId}")
    public ResponseEntity<Void> deleteHall(
            @PathVariable Long fairId,
            @PathVariable Long hallId
    ) {
        hallService.delete(fairId, hallId);
        return ResponseEntity.noContent().build();
    }
}
