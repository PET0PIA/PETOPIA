package com.ms.petopia.api.recommendation.domain;

import lombok.Getter;
import lombok.Setter;

//부스 아이템 목록을 줄 domain
@Getter
@Setter
public class BoothItemCandidate {
    private Long boothItemId;
    private String name;
    private String type;
    private String note;
}
