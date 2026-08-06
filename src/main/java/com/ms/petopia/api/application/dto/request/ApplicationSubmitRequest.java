package com.ms.petopia.api.application.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 참가 신청서 제출 요청 (POST /api/fairs/{fairId}/applications)
@Getter
@Setter
public class ApplicationSubmitRequest {

    @NotNull(message = "사업자를 선택해야 합니다")
    private Long businessId; // 신청 주체 사업자 ID

    @NotEmpty(message = "부스 슬롯을 1개 이상 선택해야 합니다.")
    @Size(max = 3, message = "부스 슬롯은 최대 3개까지 선택할 수 있습니다.")
    private List<Long> boothSlotIds; // 선택한 부스 슬롯 ID, 1~3개

    @NotBlank(message = "참가 목적은 필수입니다.")
    private String purpose;

    @NotBlank(message = "판매·전시 품목은 필수입니다.")
    private String itemsDesc;

    @NotBlank(message = "신청 담당자명은 필수입니다.")
    private String managerName;

    @NotBlank(message = "신청 담당자 연락처는 필수입니다.")
    private String managerPhone;

    @NotBlank(message = "신청 담당자 이메일은 필수입니다.")
    @Email
    private String managerEmail;

    private Boolean agreedTerms; // 제출 시 true 필수

    private String attachmentUrl; // 사업자등록증·인증서 등 제출서류를 PDF 하나로 합쳐서 업로드, 선택 입력

}
