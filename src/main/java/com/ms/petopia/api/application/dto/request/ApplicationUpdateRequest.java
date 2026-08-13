package com.ms.petopia.api.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 참가 신청서 수정 요청 (PUT /api/applications/{applicationId})
// 심사 대기(PENDING_REVIEW) 상태에서만 가능하고, 부스 슬롯은 수정 범위에 포함하지 않는다.
@Getter
@Setter
public class ApplicationUpdateRequest {

    @NotBlank(message = "참가 목적은 필수입니다.")
    @Size(max = 200, message = "참가 목적은 200자 이하여야 합니다.")
    private String purpose;

    @NotBlank(message = "판매·전시 품목은 필수입니다.")
    @Size(max = 500, message = "판매·전시 품목은 500자 이하여야 합니다.")
    private String itemsDesc;

    @NotBlank(message = "신청 담당자명은 필수입니다.")
    @Size(max = 50, message = "신청 담당자명은 50자 이하여야 합니다.")
    private String managerName;

    @NotBlank(message = "신청 담당자 연락처는 필수입니다.")
    @Size(max = 20, message = "신청 담당자 연락처는 20자 이하여야 합니다.")
    private String managerPhone;

    @NotBlank(message = "신청 담당자 이메일은 필수입니다.")
    @Email
    @Size(max = 100, message = "신청 담당자 이메일은 100자 이하여야 합니다.")
    private String managerEmail;

    @Size(max = 500, message = "첨부파일 키는 500자 이하여야 합니다.")
    private String attachmentObjectKey; // presigned-upload로 받은 임시 객체 키, 선택 입력

}
