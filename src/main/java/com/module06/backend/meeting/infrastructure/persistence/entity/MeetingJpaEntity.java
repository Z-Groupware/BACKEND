package com.module06.backend.meeting.infrastructure.persistence.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * meeting 테이블을 매핑하는 MEET-01 영속성 엔티티다.
 *
 * 프로젝트·회의실·구성원·액션과 JPA 연관관계를 만들지 않고 식별자만 저장해 도메인 결합을 막는다.
 */
@Entity
@Table(name = "meeting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingJpaEntity {

    /* 데이터베이스가 생성하는 회의 기본 키다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* 테넌트 스코프에 사용하는 회사 식별자다. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /* 필수 프로젝트 태그의 식별자다. */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /* 개설자의 팀 식별자이며 팀 무소속이면 null이다. */
    @Column(name = "team_id")
    private Long teamId;

    /* 예약한 회의실 식별자다. */
    @Column(name = "meeting_room_id", nullable = false)
    private Long meetingRoomId;

    /* 회의 개설자 식별자다. */
    @Column(name = "host_member_id", nullable = false)
    private Long hostMemberId;

    /* 회의 제목이다. */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /* 회의의 현재 생명주기 상태다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MeetingStatus status;

    /* 예약 시작 일시다. */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /* 예약 종료 일시다. */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /* 녹음 동의 안내 여부다. */
    @Column(name = "recording_consent", nullable = false)
    private boolean recordingConsent;

    /* 실제 회의 시작 일시이며 예약 단계에서는 null이다. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /* 실제 회의 종료 일시이며 예약 단계에서는 null이다. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /* 시작 전 취소가 확정된 일시이며 취소되지 않은 회의에서는 null이다. */
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /* 선택한 관련 팀 액션 식별자다. */
    @Column(name = "related_action_id")
    private Long relatedActionId;

    /* 회의 행의 생성 시각이다. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /* 회의 행의 마지막 수정 시각이다. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /* 신규 회의를 영속성 엔티티로 변환하는 생성자다. */
    private MeetingJpaEntity(Meeting meeting) {
        /* 도메인 값을 동일한 이름의 데이터베이스 컬럼에 대응시킨다. */
        this.id = meeting.getId();
        this.companyId = meeting.getCompanyId();
        this.projectId = meeting.getProjectId();
        this.teamId = meeting.getTeamId();
        this.meetingRoomId = meeting.getMeetingRoomId();
        this.hostMemberId = meeting.getHostMemberId();
        this.title = meeting.getTitle();
        this.status = meeting.getStatus();
        this.startAt = meeting.getStartAt();
        this.endAt = meeting.getEndAt();
        this.recordingConsent = meeting.isRecordingConsent();
        this.startedAt = meeting.getStartedAt();
        this.endedAt = meeting.getEndedAt();
        this.canceledAt = meeting.getCanceledAt();
        this.relatedActionId = meeting.getRelatedActionId();
    }

    /* 신규 도메인 회의를 저장 가능한 엔티티로 변환한다. */
    public static MeetingJpaEntity from(Meeting meeting) {
        /* 변환 책임을 한 곳에 두어 어댑터의 컬럼 매핑 중복을 막는다. */
        return new MeetingJpaEntity(meeting);
    }

    /* 저장된 엔티티를 참석자 명단과 함께 도메인 애그리거트로 복원한다. */
    public Meeting toDomain(List<Long> attendeeMemberIds) {
        /* 데이터베이스가 채운 식별자와 타임스탬프까지 모두 도메인에 전달한다. */
        return Meeting.reconstitute(
                id,
                companyId,
                projectId,
                teamId,
                meetingRoomId,
                hostMemberId,
                title,
                status,
                startAt,
                endAt,
                recordingConsent,
                relatedActionId,
                attendeeMemberIds,
                startedAt,
                endedAt,
                canceledAt,
                createdAt,
                updatedAt
        );
    }
}
