package com.module06.backend.meeting.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.ActionQueryPort.UndispatchedActionMeeting;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort.MeetingRoomSnapshot;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort.ProjectSnapshot;
import com.module06.backend.meeting.application.port.out.SummaryStatusQueryPort;
import com.module06.backend.meeting.application.query.GetMeetingDetailQuery;
import com.module06.backend.meeting.application.result.MeetingDetailResult;
import com.module06.backend.meeting.application.usecase.GetMeetingDetailUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingSummaryStatus;
import com.module06.backend.meeting.domain.repository.MeetingDetailRepository;
import com.module06.backend.meeting.domain.repository.MeetingDetailRepository.MeetingDetailSnapshot;
import com.module06.backend.meeting.exception.MeetingErrorCode;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;
import com.module06.backend.project.exception.ProjectErrorCode;

/*
 * MEET-04 회의 상세의 테넌트 격리·열람 권한·표시 정보 조합을 수행하는 서비스다.
 *
 * 회의 원본은 D 저장소에서 읽고 프로젝트·회의실·구성원 이름은 각 도메인 Port로 조회해
 * 다른 도메인의 엔티티나 영속성 구현을 직접 참조하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MeetingDetailQueryService implements GetMeetingDetailUseCase {

    /* 회사 범위의 상세 회의 원본과 참석자 식별자를 읽는 저장소다. */
    private final MeetingDetailRepository meetingDetailRepository;

    /* 프로젝트 태그·이름·색상을 원본 도메인에서 조회하는 C 연동 Port다. */
    private final ProjectQueryPort projectQueryPort;

    /* 비활성 이력을 포함한 회의실 이름·위치를 조회하는 D 내부 Port다. */
    private final MeetingRoomQueryPort meetingRoomQueryPort;

    /* 개설자와 참석자의 이름·팀·직급 표시 정보를 조회하는 B 연동 Port다. */
    private final MemberQueryPort memberQueryPort;

    /* 미확정 액션 배너에 쓸 회의별 분배 대기 건수를 조회하는 C 연동 Port다. */
    private final ActionQueryPort actionQueryPort;

    /* 요약 중단·실패 배너에 쓸 회의별 요약 상태를 조회하는 A 연동 Port다. */
    private final SummaryStatusQueryPort summaryStatusQueryPort;

    /* 열람 권한을 검증하고 회의 메타와 연결 리소스 표시 정보를 상세 결과로 조립한다. */
    @Override
    @Transactional(readOnly = true)
    public MeetingDetailResult getMeetingDetail(GetMeetingDetailQuery query) {
        /* 인증·대상 식별자가 올바르지 않으면 저장소와 외부 Port를 호출하지 않는다. */
        validateRequiredValues(query);

        /* companyId를 조회 조건에 포함해 타 회사 회의도 존재하지 않는 회의로 숨긴다. */
        MeetingDetailSnapshot meeting = meetingDetailRepository
                .findMeetingDetail(query.companyId(), query.meetingId())
                .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

        /* MEMBER이면서 개설자·참석자도 아니면 상세 열람을 거절한다. */
        if (!canReadMeeting(query, meeting)) {
            throw new BusinessException(MeetingErrorCode.MEETING_READ_FORBIDDEN);
        }

        /* 프로젝트는 과거 회의 보존을 위해 soft delete 포함 배치 조회 계약으로 해석한다. */
        ProjectSnapshot project = projectQueryPort
                .findProjects(meeting.companyId(), List.of(meeting.projectId()))
                .stream()
                .filter(snapshot -> snapshot.projectId().equals(meeting.projectId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        /* 회의실은 비활성화된 과거 회의실도 포함하는 회사 범위 조회 계약으로 해석한다. */
        MeetingRoomSnapshot meetingRoom = meetingRoomQueryPort
                .findMeetingRooms(meeting.companyId(), List.of(meeting.meetingRoomId()))
                .stream()
                .filter(snapshot -> snapshot.meetingRoomId().equals(meeting.meetingRoomId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND));

        /* 개설자를 첫 번째로 둔 전체 참석자 표시 정보를 B Port 한 번으로 일괄 조회한다. */
        List<Long> orderedMemberIds = hostFirst(
                meeting.hostMemberId(),
                meeting.attendeeMemberIds()
        );
        Map<Long, MemberSnapshot> members = findAndValidateMembers(
                meeting.companyId(),
                orderedMemberIds
        );
        MemberSnapshot host = members.get(meeting.hostMemberId());

        /* 종료 전 회의는 액션·요약 자체가 없으므로 C·A Port를 부르지 않고 기본값으로 확정한다. */
        long pendingActionCount = resolvePendingActionCount(meeting);
        MeetingSummaryStatus summaryStatus = resolveSummaryStatus(meeting);

        /* D 회의 메타와 다섯 Port의 표시 정보를 외부 응답과 독립적인 애플리케이션 결과로 만든다. */
        return new MeetingDetailResult(
                meeting.meetingId(),
                meeting.title(),
                meeting.status(),
                meeting.startAt(),
                meeting.endAt(),
                meeting.startedAt(),
                meeting.endedAt(),
                meeting.recordingConsent(),
                pendingActionCount,
                summaryStatus,
                new MeetingDetailResult.Project(
                        project.projectId(),
                        project.tag(),
                        project.name(),
                        project.color()
                ),
                new MeetingDetailResult.MeetingRoom(
                        meetingRoom.meetingRoomId(),
                        meetingRoom.name(),
                        meetingRoom.location()
                ),
                new MeetingDetailResult.Host(host.memberId(), host.name()),
                orderedMemberIds.stream()
                        .map(members::get)
                        .map(member -> new MeetingDetailResult.Attendee(
                                member.memberId(),
                                member.name(),
                                member.teamName(),
                                member.positionName()
                        ))
                        .toList(),
                meeting.createdAt()
        );
    }

    /* 회의가 끝나지 않았으면 분배할 액션 자체가 없으므로 C Port 없이 0건으로 확정한다. */
    private long resolvePendingActionCount(MeetingDetailSnapshot meeting) {
        if (meeting.status() != MeetingStatus.DONE) {
            return 0L;
        }

        /* MEET-10과 같은 계약이다 — 회의 ID 하나짜리 목록을 넘기면 분배 대기 건수만 있는 행이 온다. */
        return actionQueryPort
                .findMeetingsWithUndispatchedActions(meeting.companyId(), List.of(meeting.meetingId()))
                .stream()
                .findFirst()
                .map(UndispatchedActionMeeting::undispatchedCount)
                .orElse(0L);
    }

    /* 회의가 끝나지 않았으면 요약 자체가 없으므로 A Port 없이 NONE으로 확정한다. */
    private MeetingSummaryStatus resolveSummaryStatus(MeetingDetailSnapshot meeting) {
        if (meeting.status() != MeetingStatus.DONE) {
            return MeetingSummaryStatus.NONE;
        }

        /* MEET-15와 같은 계약이다 — 중단·실패가 아니면 회의 ID가 결과에 아예 없다. */
        boolean stalled = summaryStatusQueryPort
                .findStalledSummaries(meeting.companyId(), List.of(meeting.meetingId()))
                .stream()
                .findAny()
                .isPresent();

        /* 종료됐고 중단도 아니면 A가 PROCESSING·DONE을 구분해 주지 않아 실제 상태를 모른다 — 추측 대신 null. */
        return stalled ? MeetingSummaryStatus.STALLED : null;
    }

    /* Controller 밖의 내부 호출에서도 인증 식별자와 회의 식별자의 기본 계약을 지킨다. */
    private void validateRequiredValues(GetMeetingDetailQuery query) {
        /* 회사·요청자·회의 중 하나라도 식별할 수 없으면 공통 입력 오류로 거절한다. */
        if (query == null
                || query.companyId() == null
                || query.companyId() <= 0L
                || query.requesterMemberId() == null
                || query.requesterMemberId() <= 0L
                || query.meetingId() == null
                || query.meetingId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 인증 사용자가 상세 회의를 읽을 수 있는 권한 범위에 속하는지 판단한다. */
    private boolean canReadMeeting(GetMeetingDetailQuery query, MeetingDetailSnapshot meeting) {
        /* 개설자와 참석자는 역할과 관계없이 자신이 포함된 회의를 열람한다. */
        boolean host = meeting.hostMemberId().equals(query.requesterMemberId());
        boolean attendee = meeting.attendeeMemberIds().contains(query.requesterMemberId());

        /*
         * OWNER·LEADER는 팀·개설자 무관하게 같은 회사의 전체 회의를 열람한다. Authority에는
         * ADMIN이 없고 어드민은 member.is_admin 겸직 플래그라, MEMBER이면서 어드민 겸직인
         * 사람도 이 경로로 전체 열람이 가능해야 한다.
         *
         * "MEMBER가 아니면 통과"라는 부정형 대신 알려진 역할만 명시적으로 허용한다 —
         * requesterRole()이 null이거나 향후 예상 못한 값이 들어와도 안전하게 거절되도록
         * 하기 위함이다.
         */
        boolean elevated = query.requesterAdmin()
                || "OWNER".equals(query.requesterRole())
                || "LEADER".equals(query.requesterRole());

        return elevated || host || attendee;
    }

    /* 개설자를 첫 번째로 두고 저장된 나머지 참석자 순서를 유지한다. */
    private List<Long> hostFirst(Long hostMemberId, List<Long> attendeeMemberIds) {
        /* 개설자를 먼저 넣은 뒤 중복 개설자를 제외한 참석자를 순서대로 추가한다. */
        List<Long> orderedMemberIds = new ArrayList<>();
        orderedMemberIds.add(hostMemberId);
        attendeeMemberIds.stream()
                .filter(memberId -> !hostMemberId.equals(memberId))
                .forEach(orderedMemberIds::add);

        /* Port 호출과 응답 조립 중 참석자 순서가 바뀌지 않도록 불변 목록을 반환한다. */
        return List.copyOf(orderedMemberIds);
    }

    /* 삭제된 과거 참석자를 포함한 전체 구성원 표시 정보를 한 번에 조회하고 누락 여부를 검증한다. */
    private Map<Long, MemberSnapshot> findAndValidateMembers(Long companyId, List<Long> memberIds) {
        /* Port 결과 순서에 의존하지 않도록 구성원 식별자를 키로 표시 정보를 색인한다. */
        Map<Long, MemberSnapshot> members = new LinkedHashMap<>();
        for (MemberSnapshot member : memberQueryPort.findMembersIncludingDeleted(companyId, memberIds)) {
            members.put(member.memberId(), member);
        }

        /* 다른 회사·존재하지 않는 구성원처럼 조회되지 않은 식별자가 있으면 상세 응답을 거절한다. */
        if (members.size() != memberIds.size() || !members.keySet().containsAll(memberIds)) {
            throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
        }

        /* 조회 이후 표시 정보 맵이 바뀌지 않도록 방어적 불변 복사본을 반환한다. */
        return Map.copyOf(members);
    }
}
