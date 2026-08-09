package com.module06.backend.notification.domain.repository;

import com.module06.backend.notification.domain.model.Notification;

/*
 * Notification 영속성 계약 — 프레임워크(JPA) 비의존, domain 계층 소유.
 *
 * ⚠️ 이 인터페이스에 companyId 없이 memberId만으로 조회/저장하는 메서드를 추가하지 않는다 —
 * 테넌트 격리(모성진 요청)를 코드 리뷰가 아니라 이 계약 자체로 강제하기 위함이다. 나중에 조회
 * 메서드가 필요해지면 반드시 companyId를 파라미터에 포함할 것.
 */
public interface NotificationRepository {

    /**
     * 저장을 시도한다. (companyId, memberId, type, meetingId) 조합이 이미 있으면(UNIQUE 위반)
     * 저장하지 않고 false를 반환한다 — 이게 실제 중복 알림 방지 메커니즘이다(DB 제약이 최종 방어선).
     *
     * @return 새로 저장됐으면 true, 이미 있던 알림이라 건너뛰었으면 false
     */
    boolean saveIfAbsent(Notification notification);
}
