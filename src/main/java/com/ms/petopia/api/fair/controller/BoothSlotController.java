package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.BoothSlotResponse;
import com.ms.petopia.api.fair.dto.BulkSaveBoothSlotsRequest;
import com.ms.petopia.api.fair.service.BoothSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fairs/{fairId}/halls/{hallId}/booth-slots")
@RequiredArgsConstructor
public class BoothSlotController {

    private final BoothSlotService boothSlotService;

    @GetMapping
    public List<BoothSlotResponse> getBoothSlots(@PathVariable Long fairId, @PathVariable Long hallId) {
        return boothSlotService.getBoothSlots(fairId, hallId);
    }

    @PutMapping
    public List<BoothSlotResponse> bulkSave(
            @PathVariable Long fairId,
            @PathVariable Long hallId,
            @RequestBody BulkSaveBoothSlotsRequest request
    ) {
        return boothSlotService.bulkSave(fairId, hallId, request);
    }
}
