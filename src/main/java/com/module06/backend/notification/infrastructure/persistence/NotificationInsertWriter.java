package com.module06.backend.notification.infrastructure.persistence;

import com.module06.backend.notification.domain.model.Notification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/*
 * saveIfAbsent()의 INSERT만 담당하는 별도 협력자 빈.
 *
 * NotificationPersistenceAdapter 자신에 @Transactional(REQUIRES_NEW)을 걸고 그 안에서
 * DataIntegrityViolationException을 잡으면, saveAndFlush()가 그 트랜잭션에 참여(join)하다가
 * 예외로 rollback-only 표시를 남기고, 잡아서 정상 반환해도 커밋 시점에 UnexpectedRollbackException이
 * 터진다(자기 자신 호출로는 프록시를 못 거치는 문제와 같은 계열 — CaptureUploadService의
 * CompletePartUploadWriter와 동일한 이유로 별도 빈으로 뽑았다). 예외는 반드시 이 프록시 경계
 * 바깥(NotificationPersistenceAdapter)에서 잡아야 트랜잭션이 깨끗하게 롤백된 뒤 넘어온다.
 */
@Component
public class NotificationInsertWriter {

    private final SpringDataNotificationRepository springDataNotificationRepository;

    public NotificationInsertWriter(SpringDataNotificationRepository springDataNotificationRepository) {
        this.springDataNotificationRepository = springDataNotificationRepository;
    }

    // AFTER_COMMIT 리스너(MeetingCanceledNotificationTrigger)가 주 호출자라서 REQUIRES_NEW로
    // 항상 새 트랜잭션을 열어 이 안에서 커밋까지 확실히 끝낸다 — 원본 트랜잭션이 이미 커밋된
    // 직후, 자원이 아직 정리되기 전인 좁은 구간에서 기본 전파(REQUIRED)로 두면 참여만 하고
    // 별도로 커밋되지 않아 저장이 유실될 수 있다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(Notification notification) {
        springDataNotificationRepository.saveAndFlush(NotificationJpaEntity.fromDomain(notification));
    }
}
