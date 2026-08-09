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

    // saveAndFlush로 즉시 INSERT를 실행해 UNIQUE(company_id, member_id, type, source_id) 위반을
    // 이 호출 안에서 바로 잡는다(지연 flush면 예외가 나중에, 엉뚱한 곳에서 터진다).
    @Override
    public boolean saveIfAbsent(Notification notification) {
        try {
            springDataNotificationRepository.saveAndFlush(NotificationJpaEntity.fromDomain(notification));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
