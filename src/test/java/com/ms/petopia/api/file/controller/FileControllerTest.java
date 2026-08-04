package com.ms.petopia.api.file.controller;

import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import com.ms.petopia.global.security.SecurityConfig;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.dto.PresignedUpload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import(SecurityConfig.class)
class FileControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean StorageService storageService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(post("/api/files/presigned-upload")
                        .contentType("application/json")
                        .content("{\"policy\":\"IMAGE\",\"filename\":\"photo.png\",\"contentType\":\"image/png\",\"size\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsPresignedUpload() throws Exception {
        given(storageService.presignPut(any(), any(), any(), any(Long.class)))
                .willReturn(new PresignedUpload("https://storage.example/upload", "tmp/image/key.png", 300));
        given(jwtTokenProvider.getUserId("valid-token")).willReturn(1L);
        given(jwtTokenProvider.getRole("valid-token")).willReturn("USER");

        mockMvc.perform(post("/api/files/presigned-upload")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"policy\":\"IMAGE\",\"filename\":\"photo.png\",\"contentType\":\"image/png\",\"size\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://storage.example/upload"))
                .andExpect(jsonPath("$.objectKey").value("tmp/image/key.png"));
    }

    @Test
    void invalidPolicyReturns400() throws Exception {
        given(jwtTokenProvider.getUserId("valid-token")).willReturn(1L);
        given(jwtTokenProvider.getRole("valid-token")).willReturn("USER");
        mockMvc.perform(post("/api/files/presigned-upload")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"policy\":\"UNKNOWN\",\"filename\":\"photo.png\",\"contentType\":\"image/png\",\"size\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }
}
