package com.ms.petopia.api.statistics.event;

// fairId 하나만 담는 가벼운 이벤트 객체.
// 각 서비스가 예약 상태를 바꾼 뒤 이 이벤트를 발행하면,
// 리스너가 해당 fairId의 SSE 클라이언트에 최신 데이터를 push한다.
public record ReservationStatusChangedEvent(Long fairId) {}
