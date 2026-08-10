package com.module06.backend.notification.infrastructure.persistence;

import com.module06.backend.notification.domain.model.Notification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/*
 * V1 baseline의 notification 테이블(원래 recipient_member_id/type ENUM/meeting_id/message/read_at만
 * 있던 미사용 테이블, V4.3.1에서 company_id·UNIQUE 추가)에 대한 실제 JPA 매핑.
 *
 * uniqueConstraints는 V4.3.1의 UK_notification_dedup과 반드시 같은 컬럼 조합이어야 한다 — 테스트
 * 스키마(H2, Hibernate create-drop)는 이 어노테이션으로만 제약을 만들고 Flyway는 안 타므로, 여기가
 * 어긋나면 운영에서만 중복이 막히고 테스트에서는 통과하는(또는 그 반대) 괴리가 생긴다.
 *
 * type은 DB에서 ENUM('MEETING_CREATED','MEETING_REMINDER','MEETING_CANCELED')이지만 Java 쪽은
 * 그냥 String이다 — JDBC가 MySQL ENUM을 문자열로 돌려주므로 별도 컨버터 없이 그대로 매핑된다.
 */
@Entity
@Table(name = "notification",
        uniqueConstraints = @UniqueConstraint(name = "UK_notification_dedup",
                columnNames = {"company_id", "recipient_member_id", "type", "meeting_id"}))
public class NotificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "recipient_member_id", nullable = false)
    private Long memberId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NotificationJpaEntity() {
    }

    // 도메인 모델 → JPA 엔티티 (저장 직전). 신규 알림은 항상 미확인(read_at=null)이다.
    static NotificationJpaEntity fromDomain(Notification notification) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.companyId = notification.getCompanyId();
        entity.memberId = notification.getMemberId();
        entity.type = notification.getType();
        entity.meetingId = notification.getMeetingId();
        entity.message = notification.getMessage();
        entity.readAt = notification.getReadAt();
        return entity;
    }

    // JPA 엔티티 → 도메인 모델 (DB에서 읽어온 직후)
    Notification toDomain() {
        return Notification.restore(id, companyId, memberId, type, meetingId, message, readAt, createdAt);
    }
}
