package com.ms.petopia.api.recommendation.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

//클로드에게 넘길 정보들을 db에서 조회하는 domain
@Getter
@Setter
public class BoothCandidate {
    // TODO: boothId, name, category, targetAnimal, intro, items(List<BoothItemCandidate>)
    private Long boothId;
    private String name;
    private String category;
    private String targetAnimal;
    private String intro;
    private List<BoothItemCandidate> items;


}
