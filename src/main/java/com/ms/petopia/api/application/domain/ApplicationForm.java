package com.ms.petopia.api.application.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationForm {

    private Long applicationFormId;
    private Long applicationId; // application과 1:1
    private String purpose; // 참가 목적
    private String itemsDesc; // 판매·전시 품목
    private String managerName; // 신청 담당자명
    private String managerPhone; // 신청 담당자 연락처
    private String managerEmail; // 신청 담당자 이메일
    private Boolean agreedTerms; // 이용약관 동의 여부, 제출 시 true 필수
    private String attachmentUrl; // 제출서류 zip URL, 선택 입력

}
