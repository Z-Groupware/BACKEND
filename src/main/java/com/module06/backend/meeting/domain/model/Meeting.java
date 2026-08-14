package com.module06.backend.meeting.domain.model;

import java.time.Duration;
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

    /* 회의실 예약 없이 개설된 비대면 회의인지 나타낸다(MEET-18). */
    private final boolean isOnline;

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

    /* 회의 취소 시각이며 취소되지 않은 회의에서는 null이다. */
    private final LocalDateTime canceledAt;

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
            boolean isOnline,
            boolean recordingConsent,
            Long relatedActionId,
            List<Long> attendeeMemberIds,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime canceledAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /* 모든 생성·전이 경로가 이 생성자를 지나므로 상태와 취소 시각의 불변식을 여기서 한 번만 검증한다. */
        validateCancellationState(status, canceledAt);

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
        this.isOnline = isOnline;
        this.recordingConsent = recordingConsent;
        this.relatedActionId = relatedActionId;
        this.attendeeMemberIds = List.copyOf(attendeeMemberIds);
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.canceledAt = canceledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /*
     * 회의 상태와 취소 시각이 서로 어긋나지 않는지 검증한다.
     *
     * CANCELED인데 취소 시각이 없으면 취소 이력과 알림 이벤트의 시간 계약이 비고,
     * 취소되지 않았는데 취소 시각만 있으면 조회 응답이 취소된 회의처럼 보인다.
     * 두 값은 항상 함께 결정되므로 어느 쪽으로도 어긋난 애그리거트를 만들 수 없게 막는다.
     *
     * @param status 복원하거나 전이한 회의 상태
     * @param canceledAt 회의 취소 시각
     * @throws IllegalArgumentException 상태와 취소 시각이 서로 어긋나는 경우
     */
    private static void validateCancellationState(MeetingStatus status, LocalDateTime canceledAt) {
        /* 취소 상태는 취소 시각을 반드시 동반해야 한다. */
        if (status == MeetingStatus.CANCELED && canceledAt == null) {
            throw new IllegalArgumentException("CANCELED 회의에는 canceledAt이 필요합니다.");
        }

        /* 취소되지 않은 상태에 취소 시각이 남아 있으면 취소 여부 판단이 상태와 갈린다. */
        if (status != MeetingStatus.CANCELED && canceledAt != null) {
            throw new IllegalArgumentException("취소되지 않은 회의에는 canceledAt을 설정할 수 없습니다.");
        }
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
                false,
                recordingConsent,
                relatedActionId,
                List.copyOf(attendees),
                null,
                null,
                null,
                null,
                null
        );
    }

    /*
     * 검증이 끝난 요청으로 회의실과 예약 시간 없이 새로운 비대면 회의를 생성한다(MEET-18).
     *
     * 물리 회의 전용인 meetingRoomId·startAt·endAt은 애초에 받지 않고 null로 고정한다.
     *
     * @return 회의실·예약 시간이 없는 SCHEDULED 온라인 회의
     */
    public static Meeting createOnline(
            Long companyId,
            Long projectId,
            Long teamId,
            Long hostMemberId,
            String title,
            boolean recordingConsent,
            Long relatedActionId,
            List<Long> attendeeMemberIds
    ) {
        /* 개설자를 먼저 넣고 요청 참석자를 추가해 순서를 유지하면서 중복을 제거한다. */
        LinkedHashSet<Long> attendees = new LinkedHashSet<>();
        attendees.add(hostMemberId);
        attendees.addAll(attendeeMemberIds);

        /* 신규 온라인 회의의 초기 상태와 비어 있는 예약·영속성 값을 명시해 생성한다. */
        return new Meeting(
                null,
                companyId,
                projectId,
                teamId,
                null,
                hostMemberId,
                title.trim(),
                MeetingStatus.SCHEDULED,
                null,
                null,
                true,
                recordingConsent,
                relatedActionId,
                List.copyOf(attendees),
                null,
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
        /* 이 오버로드는 취소 시각을 null로 고정하므로 CANCELED를 넘기면 상태와 취소 시각이 어긋난다.
           취소된 회의는 canceledAt을 받는 오버로드로만 복원해야 하며, 여기서 먼저 막아 원인을 분명히 남긴다. */
        if (status == MeetingStatus.CANCELED) {
            throw new IllegalArgumentException("CANCELED 회의 복원에는 canceledAt이 필요합니다.");
        }

        /* 취소 시각 컬럼 도입 전 호출부는 취소되지 않은 회의로 안전하게 복원한다.
           온라인 여부를 모르는 옛 호출부는 물리 회의(false)로 복원한다. */
        return reconstitute(
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
                null,
                false,
                createdAt,
                updatedAt
        );
    }

    /* 취소 시각을 포함한 영속성 조회 결과로 회의 애그리거트를 복원한다. */
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
            LocalDateTime canceledAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /* 온라인 여부 컬럼 도입 전 호출부는 물리 회의(false)로 복원한다. */
        return reconstitute(
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
                false,
                createdAt,
                updatedAt
        );
    }

    /* 취소 시각과 온라인 여부를 포함한 영속성 조회 결과로 회의 애그리거트를 복원한다(MEET-18). */
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
            LocalDateTime canceledAt,
            boolean isOnline,
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
                isOnline,
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

    /* 예약 상태 회의의 메타와 예약 범위를 검증된 최종 값으로 변경한다. */
    public Meeting updateSchedule(
            Long projectId,
            Long meetingRoomId,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean recordingConsent
    ) {
        /* 시작된 회의가 애플리케이션 서비스를 우회해 예약 상태로 되돌아가지 못하게 한다. */
        if (status != MeetingStatus.SCHEDULED) {
            throw new IllegalStateException("시작된 회의는 수정할 수 없습니다.");
        }

        /* 필수 메타와 예약 경계가 없는 상태는 영속성 NOT NULL 계약을 깨므로 거절한다. */
        if (projectId == null
                || meetingRoomId == null
                || title == null
                || title.isBlank()
                || startAt == null
                || endAt == null) {
            throw new IllegalArgumentException("회의 수정 필수값이 누락되었습니다.");
        }

        /* 참석자·개설자·상태·실측 시각은 유지하고 MEET-05가 소유한 값만 교체한다. */
        return new Meeting(
                id,
                companyId,
                projectId,
                teamId,
                meetingRoomId,
                hostMemberId,
                title.trim(),
                status,
                startAt,
                endAt,
                isOnline,
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

        /* 종료·취소된 회의를 다시 진행 상태로 되돌리는 잘못된 내부 호출을 허용하지 않는다. */
        if (status != MeetingStatus.SCHEDULED) {
            throw new IllegalStateException("종료되거나 취소된 회의에는 입장할 수 없습니다.");
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
                isOnline,
                recordingConsent,
                relatedActionId,
                attendeeMemberIds,
                enteredAt,
                endedAt,
                canceledAt,
                createdAt,
                updatedAt
        );
    }

    /* 진행 중 회의를 실제 종료 시각과 함께 완료 상태로 전이한다. */
    public Meeting complete(LocalDateTime completedAt) {
        /* 이미 완료된 회의를 다시 완료해 후속 분석 이벤트가 중복 발행되는 것을 막는다. */
        if (status == MeetingStatus.DONE) {
            throw new IllegalStateException("이미 종료된 회의입니다.");
        }

        /* 입장되지 않았거나 취소된 회의는 종료 상태로 건너뛸 수 없다. */
        if (status != MeetingStatus.IN_PROGRESS) {
            throw new IllegalStateException("시작되지 않은 회의입니다.");
        }

        /* 종료 시각이 없거나 실제 시작보다 빠르면 실측 회의 시간을 만들 수 없다. */
        if (completedAt == null || startedAt == null || completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("회의 종료 시각은 실제 시작 시각 이후여야 합니다.");
        }

        /* 예약·참석자 원본은 보존하고 완료 상태와 실제 종료·수정 시각만 변경한다. */
        return new Meeting(
                id,
                companyId,
                projectId,
                teamId,
                meetingRoomId,
                hostMemberId,
                title,
                MeetingStatus.DONE,
                startAt,
                endAt,
                isOnline,
                recordingConsent,
                relatedActionId,
                attendeeMemberIds,
                startedAt,
                completedAt,
                canceledAt,
                createdAt,
                completedAt
        );
    }

    /* 시작 전 회의를 취소 시각과 함께 CANCELED로 전이하고 재취소는 현재 상태를 유지한다. */
    public Meeting cancel(LocalDateTime requestedCanceledAt) {
        /* 이미 취소된 회의는 최초 취소 시각을 바꾸지 않는 멱등 동작이다. */
        if (status == MeetingStatus.CANCELED) {
            return this;
        }

        /* 실제 시작 또는 종료된 회의를 취소해 확정된 회의 이력을 되돌릴 수 없다. */
        if (status != MeetingStatus.SCHEDULED) {
            throw new IllegalStateException("이미 시작된 회의는 취소할 수 없습니다.");
        }

        /* 취소 시각이 없으면 취소 이력과 이벤트의 시간 계약을 만들 수 없다. */
        if (requestedCanceledAt == null) {
            throw new IllegalArgumentException("회의 취소 시각은 필수입니다.");
        }

        /* 예약·참석자 원본은 보존하고 상태와 취소·수정 시각만 변경한다. */
        return new Meeting(
                id,
                companyId,
                projectId,
                teamId,
                meetingRoomId,
                hostMemberId,
                title,
                MeetingStatus.CANCELED,
                startAt,
                endAt,
                isOnline,
                recordingConsent,
                relatedActionId,
                attendeeMemberIds,
                startedAt,
                endedAt,
                requestedCanceledAt,
                createdAt,
                requestedCanceledAt
        );
    }

    /* 실제 입장부터 종료까지 흐른 시간을 명세의 내림 분 단위로 계산한다. */
    public long actualDurationMinutes() {
        /* 완료되지 않은 회의에서는 성공 응답용 실측 시간을 만들 수 없다. */
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            throw new IllegalStateException("완료된 회의의 실제 시작·종료 시각이 필요합니다.");
        }

        /* Duration의 분 변환으로 60초 미만 잔여 시간을 버려 명세의 정수 분을 반환한다. */
        return Duration.between(startedAt, endedAt).toMinutes();
    }
}
