package com.ms.petopia.api.pet.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

//반려동물 등록 테이블

@Getter
@Setter
@Builder
public class Pet {

    private Long petId;
    private Long userId;
    private String name;
    private String species; //동물 종 ex) 강아지, 고양이(이 컬럼도 그냥 text로 받음. 종이 다양할 수 있는데 기타로 분류하면 동물 주인들 기분이 별로일 것 같다 생각)
    private String breed;   //품종 ex) 말티즈
    private LocalDate birthDate;
    private String gender;
    private Boolean isNeutered;
    private String imageUrl;
    private LocalDateTime createdAt;
}
