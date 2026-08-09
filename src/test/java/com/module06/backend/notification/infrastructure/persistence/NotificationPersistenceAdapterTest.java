package com.module06.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.repository.NotificationRepository;

/*
 * NotificationRepository의 실제 저장·중복 방지(UNIQUE(company_id, recipient_member_id, type,
 * meeting_id))를 실제 JPA로 검증한다 — H2 테스트 스키마는 Hibernate create-drop이 소유하므로,
 * 이 제약이 실제로 걸리려면 NotificationJpaEntity의 @Table(uniqueConstraints=...)가 V4.3.1
 * 마이그레이션과 같은 컬럼 조합이어야 한다(그 정합성 자체를 이 테스트가 증명한다).
 */
@SpringBootTest
@Transactional
@DisplayName("알림 영속성 어댑터")
class NotificationPersistenceAdapterTest {

    @Autowired
    private NotificationRepository notificationRepository;

    /* 처음 저장하는 조합이면 true를 반환하는지 검증한다. */
    @Test
    @DisplayName("처음 저장하면 true를 반환한다")
    void savesNewNotification() {
        Notification notification = Notification.create(1L, 7L, "MEETING_CANCELED", 500L, "회의가 취소되었습니다.");

        assertThat(notificationRepository.saveIfAbsent(notification)).isTrue();
    }

    /* 같은 (companyId, memberId, type, meetingId) 조합으로 두 번 저장하면 두 번째는 false인지 검증한다
       (중복 알림 방지의 실제 방어선). */
    @Test
    @DisplayName("같은 조합을 두 번 저장하면 두 번째는 false다")
    void secondSaveOfSameKeyIsRejected() {
        Notification first = Notification.create(1L, 7L, "MEETING_CANCELED", 500L, "회의가 취소되었습니다.");
        Notification duplicate = Notification.create(1L, 7L, "MEETING_CANCELED", 500L, "메시지가 달라도 키가 같으면 중복.");

        assertThat(notificationRepository.saveIfAbsent(first)).isTrue();
        assertThat(notificationRepository.saveIfAbsent(duplicate)).isFalse();
    }

    /* 같은 회의(meetingId)라도 수신자(memberId)가 다르면 둘 다 저장되는지 검증한다(참석자별로 각자 알림). */
    @Test
    @DisplayName("같은 회의라도 수신자가 다르면 둘 다 저장된다")
    void differentMembersBothSaved() {
        Notification toFirstMember = Notification.create(1L, 7L, "MEETING_CANCELED", 500L, "회의가 취소되었습니다.");
        Notification toSecondMember = Notification.create(1L, 9L, "MEETING_CANCELED", 500L, "회의가 취소되었습니다.");

        assertThat(notificationRepository.saveIfAbsent(toFirstMember)).isTrue();
        assertThat(notificationRepository.saveIfAbsent(toSecondMember)).isTrue();
    }

    /* 같은 회원·같은 회의라도 알림 종류(type)가 다르면 별개로 저장되는지 검증한다. */
    @Test
    @DisplayName("같은 회원·회의라도 알림 종류가 다르면 둘 다 저장된다")
    void differentTypesBothSaved() {
        Notification canceled = Notification.create(1L, 7L, "MEETING_CANCELED", 500L, "회의가 취소되었습니다.");
        Notification reminder = Notification.create(1L, 7L, "MEETING_REMINDER", 500L, "회의 시작 10분 전입니다.");

        assertThat(notificationRepository.saveIfAbsent(canceled)).isTrue();
        assertThat(notificationRepository.saveIfAbsent(reminder)).isTrue();
    }
}
