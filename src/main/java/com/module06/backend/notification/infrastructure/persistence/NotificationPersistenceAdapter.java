package com.module06.backend.notification.infrastructure.persistence;

import java.util.Locale;

import com.module06.backend.notification.domain.model.Notification;
import com.module06.backend.notification.domain.repository.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

// domain의 NotificationRepository 계약을 JPA로 구현하는 어댑터.
@Repository
public class NotificationPersistenceAdapter implements NotificationRepository {

    // 실제 INSERT(+ REQUIRES_NEW 트랜잭션 경계)는 별도 빈(NotificationInsertWriter)에 위임한다 —
    // 이 클래스 자신에 @Transactional을 걸고 그 안에서 예외를 잡으면, this.xxx() 자기 호출과
    // 같은 이유로 프록시 경계가 뒤섞여 커밋 시점에 UnexpectedRollbackException이 난다.
    private final NotificationInsertWriter notificationInsertWriter;

    public NotificationPersistenceAdapter(NotificationInsertWriter notificationInsertWriter) {
        this.notificationInsertWriter = notificationInsertWriter;
    }

    // UNIQUE(company_id, recipient_member_id, type, meeting_id) 위반을 여기서 잡는다 —
    // notificationInsertWriter.insert()는 REQUIRES_NEW라서, 예외가 그 프록시 경계를 넘어 여기
    // (트랜잭션 밖)까지 나온 시점엔 내부 트랜잭션이 이미 깨끗하게 롤백되어 있다.
    @Override
    public boolean saveIfAbsent(Notification notification) {
        try {
            notificationInsertWriter.insert(notification);
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
    // 구분 없이 비교한다. Locale.ROOT 고정(CodeRabbit 지적) — 튀르키예어 로케일에서는
    // toUpperCase()가 'i'를 'İ'로 바꿔 "NOTIFICATION"이 깨져서 매칭이 실패할 수 있다.
    private boolean isDedupViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause != null && cause.getMessage() != null
                && cause.getMessage().toUpperCase(Locale.ROOT).contains("UK_NOTIFICATION_DEDUP");
    }
}
