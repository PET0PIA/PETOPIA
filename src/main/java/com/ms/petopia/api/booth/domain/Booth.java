package com.ms.petopia.api.booth.domain;

import lombok.*;

import java.time.LocalDateTime;

// 부스 공개 프로필. application 1건당 1:1 (신청서가 CONFIRMED될 때 자동 생성, 취소되면 자동 삭제)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booth {

    private Long boothId;
    private Long applicationId; // 1:1, 결제완료된 신청서
    private Long businessId; // business.business_id
    private String name;
    private String imageUrl; // 업체 로고·부스 배너
    private String intro;
    private String category; // 사료/간식, 미용, 훈련, 굿즈 등 (자유 텍스트)
    private TargetAnimal targetAnimal; // DOG / CAT / ETC
    private LocalDateTime confirmedAt; // 부스 생성(=신청 확정) 시각

    public enum TargetAnimal {
        DOG, CAT, ETC
    }

}
