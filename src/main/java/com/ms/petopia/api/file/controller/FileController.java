package com.ms.petopia.api.file.controller;

import com.ms.petopia.api.file.dto.PresignedUploadRequest;
import com.ms.petopia.api.file.dto.PresignedUploadResponse;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.dto.PresignedUpload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;

    @PostMapping("/presigned-upload")
    public ResponseEntity<PresignedUploadResponse> issuePresignedUpload(
            @Valid @RequestBody PresignedUploadRequest request) {

        PresignedUpload upload = storageService.presignPut(
                request.policy(), request.filename(), request.contentType(), request.size());
        return ResponseEntity.ok(new PresignedUploadResponse(
                upload.uploadUrl(), upload.objectKey(), upload.expiresInSeconds()));
    }
}
