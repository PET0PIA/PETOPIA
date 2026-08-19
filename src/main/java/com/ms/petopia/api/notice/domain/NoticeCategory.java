package com.ms.petopia.api.notice.domain;

/**
 * 소식 목록의 분류.
 *
 * <p>{@link #RECRUIT}만 성격이 다르다 - notice 테이블에 저장되지 않고, 목록을 만들 때
 * recruit_notice(행사별 참가업체 모집공고)에서 끌어와 붙이는 가상 분류다. 같은 내용을 두 곳에
 * 쓰지 않으려는 선택이라, 등록/수정 API는 이 값을 받지 않는다({@link #isStored()}로 걸러낸다).
 */
public enum NoticeCategory {
    NOTICE,   // 공지
    EVENT,    // 이벤트
    GUIDE,    // 안내
    RECRUIT;  // 모집공고 (recruit_notice에서 병합, 저장 안 함)

    /** notice 테이블에 실제로 저장되는 분류인지. */
    public boolean isStored() {
        return this != RECRUIT;
    }
}
