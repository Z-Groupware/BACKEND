package com.module06.backend.notification.infrastructure.persistence;

import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.repository.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

// domain의 NotificationRepository 계약을 JPA로 구현하는 어댑터.
@Repository
public class NotificationPersistenceAdapter implements NotificationRepository {

    private final SpringDataNotificationRepository springDataNotificationRepository;

    public NotificationPersistenceAdapter(SpringDataNotificationRepository springDataNotificationRepository) {
        this.springDataNotificationRepository = springDataNotificationRepository;
    }

    // saveAndFlush로 즉시 INSERT를 실행해 UNIQUE(company_id, recipient_member_id, type, meeting_id)
    // 위반을 이 호출 안에서 바로 잡는다(지연 flush면 예외가 나중에, 엉뚱한 곳에서 터진다).
    @Override
    public boolean saveIfAbsent(Notification notification) {
        try {
            springDataNotificationRepository.saveAndFlush(NotificationJpaEntity.fromDomain(notification));
            return true;
        } catch (DataIntegrityViolationException e) {
            // UK_notification_dedup 위반만 "이미 있던 알림"이다. FK_notification_company 등
            // 다른 무결성 위반까지 중복으로 취급하면 실제 오류가 조용히 스킵된다 — 그대로 던진다.
            if (isDedupViolation(e)) {
                return false;
            }
            throw e;
        }
    }

    // H2(테스트)는 제약 이름을 대문자로 바꿔 메시지에 싣고 MySQL(운영)은 그대로 싣는다 — 대소문자
    // 구분 없이 비교한다.
    private boolean isDedupViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause != null && cause.getMessage() != null
                && cause.getMessage().toUpperCase().contains("UK_NOTIFICATION_DEDUP");
    }
}
