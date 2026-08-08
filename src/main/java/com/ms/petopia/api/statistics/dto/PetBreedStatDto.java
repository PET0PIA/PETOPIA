package com.ms.petopia.api.statistics.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PetBreedStatDto {
    private String species;
    private String breed; // 품종명, null이면 "미등록"으로 프론트에서 처리
    private int count;
}
