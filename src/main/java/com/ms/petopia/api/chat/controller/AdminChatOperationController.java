package com.ms.petopia.api.chat.controller;

import com.ms.petopia.api.chat.dto.AdminChatMenuRequest;
import com.ms.petopia.api.chat.dto.AdminChatMenuResponse;
import com.ms.petopia.api.chat.dto.ChatBusinessHour;
import com.ms.petopia.api.chat.dto.ChatMenuStat;
import com.ms.petopia.api.chat.dto.ChatSetting;
import com.ms.petopia.api.chat.service.ChatOperationService;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상담 운영 설정 API - 버튼, 운영시간, 문구, 지표.
 *
 * <p>대화를 다루는 {@link AdminChatController}와 나눈 이유는 사용 주기가 다르기 때문이다.
 * 상담 응대는 상시, 설정 변경은 가끔이고, 둘을 한 컨트롤러에 두면 상담 응대 코드를 볼 때마다
 * 설정 CRUD를 지나쳐야 한다.
 */
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
public class AdminChatOperationController {

    private final ChatOperationService operationService;

    @GetMapping("/menus")
    public ResponseEntity<ApiResponse<List<AdminChatMenuResponse>>> listMenus() {
        return ResponseEntity.ok(ApiResponse.success(operationService.listMenus()));
    }

    @PostMapping("/menus")
    public ResponseEntity<ApiResponse<AdminChatMenuResponse>> createMenu(
            @Valid @RequestBody AdminChatMenuRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, operationService.createMenu(request)));
    }

    @PutMapping("/menus/{menuId}")
    public ResponseEntity<ApiResponse<AdminChatMenuResponse>> updateMenu(
            @PathVariable Long menuId,
            @Valid @RequestBody AdminChatMenuRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(operationService.updateMenu(menuId, request)));
    }

    /** 삭제가 아니라 비활성이다 - 과거 상담이 이 버튼을 참조하고 있다. */
    @DeleteMapping("/menus/{menuId}")
    public ResponseEntity<ApiResponse<Void>> deactivateMenu(@PathVariable Long menuId) {
        operationService.deactivateMenu(menuId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/menus/order")
    public ResponseEntity<ApiResponse<Void>> reorderMenus(@RequestBody List<Long> menuIds) {
        operationService.reorderMenus(menuIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/business-hours")
    public ResponseEntity<ApiResponse<List<ChatBusinessHour>>> listBusinessHours() {
        return ResponseEntity.ok(ApiResponse.success(operationService.listBusinessHours()));
    }

    @PutMapping("/business-hours")
    public ResponseEntity<ApiResponse<Void>> saveBusinessHours(
            @Valid @RequestBody List<ChatBusinessHour> hours
    ) {
        operationService.saveBusinessHours(hours);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<ChatSetting>>> listSettings() {
        return ResponseEntity.ok(ApiResponse.success(operationService.listSettings()));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<Void>> saveSettings(@Valid @RequestBody List<ChatSetting> settings) {
        operationService.saveSettings(settings);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<List<ChatMenuStat>>> stats() {
        return ResponseEntity.ok(ApiResponse.success(operationService.menuStats()));
    }
}
