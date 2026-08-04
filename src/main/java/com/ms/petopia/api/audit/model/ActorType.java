package com.ms.petopia.api.audit.model;

public enum ActorType {
    USER, // 일반 사용자 행위
    ADMIN, // EVENT_ADMIN / SUPER_ADMIN 행위
    SYSTEM, // 배치/스케줄러/자동화
    PAYMENT // 결제 도메인
}
