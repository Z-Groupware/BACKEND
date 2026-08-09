package com.module06.backend.meeting.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.meeting.domain.model.CaptureSession;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;

/*
 * D 도메인의 capture_session 테이블을 매핑하는 JPA 영속성 엔티티다.
 *
 * 현재 녹음자 컬럼을 두지 않아 A 도메인의 capture_upload_state와 원본이 중복되지 않게 한다.
 */
@Entity
@Table(
        name = "capture_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_capture_session_meeting",
                columnNames = "meeting_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaptureSessionJpaEntity {

    /* 데이터베이스가 생성하는 캡처 세션 식별자다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 회의당 세션 하나를 보장하는 유일 키 대상 회의 식별자다. */
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /* 세션을 연 host이며 현재 녹음자를 의미하지 않는다. */
    @Column(name = "started_by", nullable = false)
    private Long startedBy;

    /* D가 소유하는 세션 생명주기 상태다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CaptureSessionStatus status;

    /* 서버 KST 기준 캡처 세션 시작 일시다. */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /* 자막·청크가 공통으로 사용하는 Unix epoch 밀리초 기준점이다. */
    @Column(name = "started_at_epoch_ms", nullable = false)
    private long startedAtEpochMs;

    /* 현재 PAUSED 상태가 시작된 시각이며 ACTIVE 상태에서는 null이다. */
    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    /* 종료 시각이며 ACTIVE 초기 상태에서는 null이다. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /* 캡처 세션 행 생성 시각이다. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /* 캡처 세션 행 최종 수정 시각이다. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /* 도메인 애그리거트의 모든 저장 값을 JPA 엔티티로 옮긴다. */
    private CaptureSessionJpaEntity(CaptureSession captureSession) {
        /* 기존 식별자가 있으면 이후 상태 변경에서 같은 행을 갱신할 수 있도록 보존한다. */
        this.id = captureSession.getId();
        this.meetingId = captureSession.getMeetingId();
        this.startedBy = captureSession.getStartedBy();
        this.status = captureSession.getStatus();
        this.startedAt = captureSession.getStartedAt();
        this.startedAtEpochMs = captureSession.getStartedAtEpochMs();
        this.pausedAt = captureSession.getPausedAt();
        this.endedAt = captureSession.getEndedAt();
        this.createdAt = captureSession.getCreatedAt();
        this.updatedAt = captureSession.getUpdatedAt();
    }

    /* 캡처 세션 도메인 애그리거트를 영속성 엔티티로 변환한다. */
    public static CaptureSessionJpaEntity from(CaptureSession captureSession) {
        /* 영속성 기술을 알지 못하는 도메인 값을 어댑터 경계에서 변환한다. */
        return new CaptureSessionJpaEntity(captureSession);
    }

    /* 저장된 모든 값을 순수 캡처 세션 도메인 애그리거트로 복원한다. */
    public CaptureSession toDomain() {
        /* JPA 엔티티가 애플리케이션 계층 밖으로 새지 않도록 순수 값만 전달한다. */
        return CaptureSession.reconstitute(
                id,
                meetingId,
                startedBy,
                status,
                startedAt,
                startedAtEpochMs,
                pausedAt,
                endedAt,
                createdAt,
                updatedAt
        );
    }
}
