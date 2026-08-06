package com.module06.backend.meeting.domain.model;

import java.time.LocalDateTime;

import lombok.Getter;

/*
 * 회의당 하나만 존재하는 D 도메인의 캡처 세션 애그리거트다.
 *
 * D는 세션 식별자·생명주기·시간축 기준점만 소유하고 현재 녹음자와 청크는 A 도메인에 맡긴다.
 */
@Getter
public class CaptureSession {

    /* 데이터베이스가 생성하는 캡처 세션 식별자다. */
    private final Long id;

    /* 캡처 세션이 연결된 회의 식별자다. */
    private final Long meetingId;

    /* 세션 시작 API를 호출한 회의 개설자 식별자이며 현재 녹음자를 뜻하지 않는다. */
    private final Long startedBy;

    /* D 도메인이 관리하는 캡처 세션 생명주기 상태다. */
    private final CaptureSessionStatus status;

    /* 서버 KST 기준 캡처 세션 시작 일시다. */
    private final LocalDateTime startedAt;

    /* 모든 자막·청크 오프셋이 공유하는 서버 기준 Unix epoch 밀리초다. */
    private final long startedAtEpochMs;

    /* 마지막 일시정지 시각이며 아직 일시정지되지 않았으면 null이다. */
    private final LocalDateTime pausedAt;

    /* 세션 종료 시각이며 종료 전에는 null이다. */
    private final LocalDateTime endedAt;

    /* 캡처 세션 행 생성 시각이다. */
    private final LocalDateTime createdAt;

    /* 캡처 세션 행 최종 수정 시각이다. */
    private final LocalDateTime updatedAt;

    /* 모든 상태를 명시적으로 받아 애그리거트를 구성하는 내부 생성자다. */
    private CaptureSession(
            Long id,
            Long meetingId,
            Long startedBy,
            CaptureSessionStatus status,
            LocalDateTime startedAt,
            long startedAtEpochMs,
            LocalDateTime pausedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /* 저장소와 서비스에서 전달한 순수 값을 프레임워크 의존 없이 보관한다. */
        this.id = id;
        this.meetingId = meetingId;
        this.startedBy = startedBy;
        this.status = status;
        this.startedAt = startedAt;
        this.startedAtEpochMs = startedAtEpochMs;
        this.pausedAt = pausedAt;
        this.endedAt = endedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /* host가 진행 중인 회의에서 최초 ACTIVE 캡처 세션을 생성한다. */
    public static CaptureSession start(
            Long meetingId,
            Long startedBy,
            LocalDateTime startedAt,
            long startedAtEpochMs
    ) {
        /* 식별 불가능한 회의나 시작자는 캡처 세션 원본에 저장할 수 없다. */
        if (meetingId == null || meetingId <= 0L || startedBy == null || startedBy <= 0L) {
            throw new IllegalArgumentException("캡처 세션의 회의와 시작자 식별자는 양수여야 합니다.");
        }

        /* 서버 시간축을 만들 수 없는 값은 청크 정렬의 기준으로 사용할 수 없다. */
        if (startedAt == null || startedAtEpochMs < 0L) {
            throw new IllegalArgumentException("캡처 세션 시작 시각은 필수입니다.");
        }

        /* 생성·수정 시각을 시작 시각과 맞춰 최초 상태를 하나의 시점으로 고정한다. */
        return new CaptureSession(
                null,
                meetingId,
                startedBy,
                CaptureSessionStatus.ACTIVE,
                startedAt,
                startedAtEpochMs,
                null,
                null,
                startedAt,
                startedAt
        );
    }

    /* 영속성 조회 결과를 손실 없이 캡처 세션 애그리거트로 복원한다. */
    public static CaptureSession reconstitute(
            Long id,
            Long meetingId,
            Long startedBy,
            CaptureSessionStatus status,
            LocalDateTime startedAt,
            long startedAtEpochMs,
            LocalDateTime pausedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /* 데이터베이스가 보장한 식별자와 상태를 이후 CAP 생명주기 API가 사용할 수 있게 복원한다. */
        return new CaptureSession(
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

    /* API 응답의 isPaused 값을 세션 상태 원본에서 파생한다. */
    public boolean isPaused() {
        /* 별도 boolean 원본을 두지 않아 상태와 일시정지 여부가 어긋나지 않게 한다. */
        return status == CaptureSessionStatus.PAUSED;
    }

    /* ACTIVE 세션을 같은 식별자와 시간축을 유지한 PAUSED 상태로 전이한다. */
    public CaptureSession pause(LocalDateTime pausedAt) {
        /* 일시정지 시각은 세션 시작 시각보다 빠를 수 없고 null일 수도 없다. */
        if (pausedAt == null || pausedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("캡처 일시정지 시각은 세션 시작 시각 이후여야 합니다.");
        }

        /* 이미 일시정지된 세션에 같은 전이를 반복하는 잘못된 내부 호출을 차단한다. */
        if (status == CaptureSessionStatus.PAUSED) {
            throw new IllegalStateException("이미 일시정지된 캡처 세션입니다.");
        }

        /* 종료된 세션을 다시 제어 가능한 상태로 변경하는 상태 역행을 차단한다. */
        if (status == CaptureSessionStatus.ENDED) {
            throw new IllegalStateException("종료된 캡처 세션은 일시정지할 수 없습니다.");
        }

        /* 세션 ID·시간축·시작자는 유지하고 상태·일시정지·수정 시각만 변경한다. */
        return new CaptureSession(
                id,
                meetingId,
                startedBy,
                CaptureSessionStatus.PAUSED,
                startedAt,
                startedAtEpochMs,
                pausedAt,
                endedAt,
                createdAt,
                pausedAt
        );
    }
}
