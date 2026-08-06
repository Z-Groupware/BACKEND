package com.module06.backend.meeting.domain.model;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import lombok.Getter;

/*
 * 회의 예약 애그리거트 루트다.
 *
 * 다른 도메인의 객체를 직접 참조하지 않고 회사·프로젝트·회의실·구성원·액션 식별자만 보관한다.
 * 개설자는 항상 첫 번째 참석자로 포함되며 중복된 참석자 식별자는 생성 시점에 제거한다.
 */
@Getter
public class Meeting {

    /* 데이터베이스가 생성하는 회의 식별자다. */
    private final Long id;

    /* 회의가 속한 회사 식별자다. */
    private final Long companyId;

    /* 회의가 연결된 프로젝트 식별자다. */
    private final Long projectId;

    /* 개설자의 팀 식별자이며 팀이 없는 OWNER·ADMIN은 null이다. */
    private final Long teamId;

    /* 예약한 회의실 식별자다. */
    private final Long meetingRoomId;

    /* 회의 개설자이자 담당자의 구성원 식별자다. */
    private final Long hostMemberId;

    /* 회의 제목이다. */
    private final String title;

    /* 현재 회의 상태다. */
    private final MeetingStatus status;

    /* 예약 시작 일시다. */
    private final LocalDateTime startAt;

    /* 예약 종료 일시다. */
    private final LocalDateTime endAt;

    /* 참석자에게 녹음 안내가 필요한지 나타낸다. */
    private final boolean recordingConsent;

    /* 이 회의가 참조하는 팀 액션 식별자이며 없을 수 있다. */
    private final Long relatedActionId;

    /* 개설자를 첫 번째로 포함한 중복 없는 참석자 식별자 목록이다. */
    private final List<Long> attendeeMemberIds;

    /* 실제 회의 시작 시각이며 예약 단계에서는 null이다. */
    private final LocalDateTime startedAt;

    /* 실제 회의 종료 시각이며 예약 단계에서는 null이다. */
    private final LocalDateTime endedAt;

    /* 회의 생성 시각이며 저장 전에는 null이다. */
    private final LocalDateTime createdAt;

    /* 회의 수정 시각이며 저장 전에는 null이다. */
    private final LocalDateTime updatedAt;

    /* 모든 상태를 명시적으로 받는 내부 생성자다. */
    private Meeting(
            Long id,
            Long companyId,
            Long projectId,
            Long teamId,
            Long meetingRoomId,
            Long hostMemberId,
            String title,
            MeetingStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean recordingConsent,
            Long relatedActionId,
            List<Long> attendeeMemberIds,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /* 전달받은 값을 외부 엔티티 참조 없이 순수 값으로 보관한다. */
        this.id = id;
        this.companyId = companyId;
        this.projectId = projectId;
        this.teamId = teamId;
        this.meetingRoomId = meetingRoomId;
        this.hostMemberId = hostMemberId;
        this.title = title;
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
        this.recordingConsent = recordingConsent;
        this.relatedActionId = relatedActionId;
        this.attendeeMemberIds = List.copyOf(attendeeMemberIds);
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /*
     * 검증이 끝난 요청으로 새로운 예약 회의를 생성한다.
     *
     * @return 식별자와 실제 시작·종료 시각이 아직 없는 SCHEDULED 회의
     */
    public static Meeting create(
            Long companyId,
            Long projectId,
            Long teamId,
            Long meetingRoomId,
            Long hostMemberId,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean recordingConsent,
            Long relatedActionId,
            List<Long> attendeeMemberIds
    ) {
        /* 개설자를 먼저 넣고 요청 참석자를 추가해 순서를 유지하면서 중복을 제거한다. */
        LinkedHashSet<Long> attendees = new LinkedHashSet<>();
        attendees.add(hostMemberId);
        attendees.addAll(attendeeMemberIds);

        /* 신규 회의의 초기 상태와 비어 있는 영속성 값을 명시해 생성한다. */
        return new Meeting(
                null,
                companyId,
                projectId,
                teamId,
                meetingRoomId,
                hostMemberId,
                title.trim(),
                MeetingStatus.SCHEDULED,
                startAt,
                endAt,
                recordingConsent,
                relatedActionId,
                List.copyOf(attendees),
                null,
                null,
                null,
                null
        );
    }

    /*
     * 영속성 조회 결과로 회의 애그리거트를 복원한다.
     *
     * @return 데이터베이스 상태를 그대로 반영한 회의
     */
    public static Meeting reconstitute(
            Long id,
            Long companyId,
            Long projectId,
            Long teamId,
            Long meetingRoomId,
            Long hostMemberId,
            String title,
            MeetingStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean recordingConsent,
            Long relatedActionId,
            List<Long> attendeeMemberIds,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /* 저장된 모든 값을 손실 없이 애그리거트로 복원한다. */
        return new Meeting(
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
                createdAt,
                updatedAt
        );
    }

    /*
     * 회의가 점유할 30분 슬롯 시작 시각을 반환한다.
     *
     * @return startAt 이상 endAt 미만의 30분 슬롯 목록
     */
    public List<LocalDateTime> reservationSlotStarts() {
        /* 슬롯 계산 규칙을 전용 도메인 계산기에 위임한다. */
        return MeetingSlotGrid.slotStarts(startAt, endAt);
    }

    /* 특정 구성원이 예약된 참석자 명단에 포함됐는지 판단한다. */
    public boolean hasAttendee(Long memberId) {
        /* 구성원 엔티티를 조회하지 않고 애그리거트가 가진 식별자 명단만 사용한다. */
        return memberId != null && attendeeMemberIds.contains(memberId);
    }

    /* 특정 구성원이 회의 개설자인지 판단한다. */
    public boolean isHost(Long memberId) {
        /* null 요청자를 개설자로 해석하지 않고 저장된 개설자 식별자와 값으로 비교한다. */
        return memberId != null && hostMemberId.equals(memberId);
    }

    /* 예약 회의를 최초 입장 시각과 함께 진행 상태로 전이하고 재입장은 현재 상태를 유지한다. */
    public Meeting enter(LocalDateTime enteredAt) {
        /* 실제 시작 시각을 기록할 수 없는 null 값은 도메인 상태 전이에 사용할 수 없다. */
        if (enteredAt == null) {
            throw new IllegalArgumentException("회의 입장 시각은 필수입니다.");
        }

        /* 진행 중 회의의 재입장은 최초 startedAt을 바꾸지 않는 멱등 동작이다. */
        if (status == MeetingStatus.IN_PROGRESS) {
            return this;
        }

        /* 종료된 회의를 다시 진행 상태로 되돌리는 잘못된 내부 호출을 허용하지 않는다. */
        if (status == MeetingStatus.DONE) {
            throw new IllegalStateException("종료된 회의에는 입장할 수 없습니다.");
        }

        /* SCHEDULED 회의의 나머지 예약·참석자 값은 유지하고 상태와 최초 시작 시각만 변경한다. */
        return new Meeting(
                id,
                companyId,
                projectId,
                teamId,
                meetingRoomId,
                hostMemberId,
                title,
                MeetingStatus.IN_PROGRESS,
                startAt,
                endAt,
                recordingConsent,
                relatedActionId,
                attendeeMemberIds,
                enteredAt,
                endedAt,
                createdAt,
                updatedAt
        );
    }
}
