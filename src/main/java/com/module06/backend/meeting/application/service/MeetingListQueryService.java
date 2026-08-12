package com.module06.backend.meeting.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.port.out.ActionQueryPort;
import com.module06.backend.meeting.application.port.out.ActionQueryPort.MeetingActionCount;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort.MeetingRoomSnapshot;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort.ProjectSnapshot;
import com.module06.backend.meeting.application.query.GetMeetingListQuery;
import com.module06.backend.meeting.application.result.MeetingListResult;
import com.module06.backend.meeting.application.usecase.GetMeetingListUseCase;
import com.module06.backend.meeting.domain.model.MeetingEntryPolicy;
import com.module06.backend.meeting.domain.model.MeetingListScope;
import com.module06.backend.meeting.domain.repository.MeetingListRepository;
import com.module06.backend.meeting.domain.repository.MeetingListRepository.MeetingListCriteria;
import com.module06.backend.meeting.domain.repository.MeetingListRepository.MeetingListSnapshot;
import com.module06.backend.meeting.domain.repository.MeetingListRepository.MeetingPage;
import com.module06.backend.meetingroom.exception.MeetingRoomErrorCode;
import com.module06.backend.project.exception.ProjectErrorCode;

/*
 * MEET-02 회의 목록 필터 조회를 조율하는 애플리케이션 서비스다.
 *
 * 인증 열람 범위와 필터를 저장소에 전달하고, 회의실·프로젝트·액션 표시 정보는 각 Port로
 * 일괄 조회해 회의별 반복 호출 없이 페이지 결과를 완성한다.
 */
@Service
@RequiredArgsConstructor
public class MeetingListQueryService implements GetMeetingListUseCase {

    /* 기간이 생략된 요청에 적용하는 최근 조회 개월 수다. */
    private static final int DEFAULT_MONTH_RANGE = 3;

    /* 페이지 번호가 생략됐을 때 적용하는 첫 페이지 번호다. */
    private static final int DEFAULT_PAGE = 0;

    /* 페이지 크기가 생략됐을 때 적용하는 기본 회의 개수다. */
    private static final int DEFAULT_SIZE = 20;

    /* 한 페이지에서 허용하는 최대 회의 개수다. */
    private static final int MAX_SIZE = 100;

    /* 회사·열람 범위·선택 필터로 회의 페이지를 조회하는 저장소다. */
    private final MeetingListRepository meetingListRepository;

    /* 회의 목록에 표시할 회의실 이름과 필터 소속을 조회하는 D 내부 Port다. */
    private final MeetingRoomQueryPort meetingRoomQueryPort;

    /* 회의 목록에 표시할 프로젝트 태그·이름과 필터 소속을 조회하는 C 연동 Port다. */
    private final ProjectQueryPort projectQueryPort;

    /* 회의 목록 카드에 표시할 회의별 전체 액션 수를 조회하는 C 연동 Port다. */
    private final ActionQueryPort actionQueryPort;

    /* 회의 목록 카드의 참석자 아바타 이름을 조회하는 B 연동 Port다. */
    private final MemberQueryPort memberQueryPort;

    /* 생략된 조회 기간과 입장 가능 여부 계산에 쓰는 동일한 KST 현재 시각의 시계다. */
    private final Clock clock;

    /* 인증 사용자에게 허용된 회의를 필터·페이징하여 표시 정보와 함께 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public MeetingListResult getMeetings(GetMeetingListQuery query) {
        /* 인증 식별자와 페이지 값을 검증하고 생략된 기간·페이지 기본값을 확정한다. */
        ResolvedQuery resolved = validateAndResolve(query);

        /* 명시된 프로젝트와 회의실이 실제 요청 회사에 속하는지 목록 조회 전에 확인한다. */
        validateFilterReferences(resolved);

        /* 회사와 권한 및 전체 필터를 데이터베이스에 적용해 필요한 한 페이지만 조회한다. */
        MeetingPage page = meetingListRepository.findMeetings(new MeetingListCriteria(
                resolved.companyId(),
                resolved.requesterMemberId(),
                resolved.companyWideRead(),
                resolved.projectId(),
                resolved.meetingRoomId(),
                resolved.from().atStartOfDay(),
                resolved.to().atTime(LocalTime.MAX),
                resolved.status(),
                resolved.scope(),
                resolved.page(),
                resolved.size()
        ));

        /* 결과가 없으면 표시 정보 Port를 추가 호출하지 않고 페이지 메타와 빈 배열을 반환한다. */
        if (page.meetings().isEmpty()) {
            return new MeetingListResult(
                    List.of(),
                    new MeetingListResult.Page(
                            resolved.page(),
                            resolved.size(),
                            page.totalElements(),
                            page.totalPages()
                    )
            );
        }

        /* 현재 페이지의 중복 없는 회의실 표시 정보를 한 번의 Port 호출로 조회해 색인한다. */
        List<Long> meetingRoomIds = page.meetings().stream()
                .map(MeetingListSnapshot::meetingRoomId)
                .distinct()
                .toList();
        Map<Long, MeetingRoomSnapshot> meetingRooms = indexMeetingRooms(
                meetingRoomIds,
                meetingRoomQueryPort.findMeetingRooms(resolved.companyId(), meetingRoomIds)
        );

        /* 현재 페이지의 중복 없는 프로젝트 표시 정보를 한 번의 Port 호출로 조회해 색인한다. */
        List<Long> projectIds = page.meetings().stream()
                .map(MeetingListSnapshot::projectId)
                .distinct()
                .toList();
        Map<Long, ProjectSnapshot> projects = indexProjects(
                projectIds,
                projectQueryPort.findProjects(resolved.companyId(), projectIds)
        );

        /* 현재 페이지의 회의 식별자만 C에 한 번 전달해 전체 액션 수를 배치 조회한다. */
        List<Long> meetingIds = page.meetings().stream()
                .map(MeetingListSnapshot::meetingId)
                .toList();
        Map<Long, Long> actionCounts = indexActionCounts(
                meetingIds,
                actionQueryPort.countActionsByMeetings(resolved.companyId(), meetingIds)
        );

        /* 페이지 전체 회의의 참석자 식별자를 한 데 모아 B에 한 번만 전달해 이름을 조회한다. */
        List<Long> attendeeMemberIds = page.meetings().stream()
                .flatMap(meeting -> meeting.attendeeMemberIds().stream())
                .distinct()
                .toList();
        Map<Long, MemberSnapshot> members = indexMembers(
                attendeeMemberIds,
                memberQueryPort.findMembersIncludingDeleted(resolved.companyId(), attendeeMemberIds)
        );

        /* isHost·entryAvailable 판정이 응답 조립 도중 흔들리지 않도록 같은 순간을 한 번만 읽는다. */
        LocalDateTime now = LocalDateTime.now(clock);

        /* 저장소가 보장한 내림차순을 유지하며 표시 정보가 완성된 응답 행으로 변환한다. */
        List<MeetingListResult.MeetingItem> meetings = page.meetings().stream()
                .map(meeting -> toResultItem(
                        meeting,
                        meetingRooms.get(meeting.meetingRoomId()),
                        projects.get(meeting.projectId()),
                        actionCounts.getOrDefault(meeting.meetingId(), 0L),
                        resolved.requesterMemberId(),
                        now,
                        members
                ))
                .toList();

        /* 회의 목록과 요청·전체 결과 기준 페이지 메타데이터를 함께 반환한다. */
        return new MeetingListResult(
                meetings,
                new MeetingListResult.Page(
                        resolved.page(),
                        resolved.size(),
                        page.totalElements(),
                        page.totalPages()
                )
        );
    }

    /* 인증 식별자·페이지·기간을 검증하고 생략된 값이 채워진 내부 Query를 반환한다. */
    private ResolvedQuery validateAndResolve(GetMeetingListQuery query) {
        /* 인증 회사와 구성원을 식별할 수 없으면 저장소 접근 전에 공통 입력 오류로 거절한다. */
        if (query == null
                || query.companyId() == null
                || query.companyId() <= 0L
                || query.requesterMemberId() == null
                || query.requesterMemberId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 선택 식별자는 null 또는 양수만 허용해 잘못된 조회 조건이 Port로 전달되지 않게 한다. */
        if (isInvalidOptionalId(query.projectId()) || isInvalidOptionalId(query.meetingRoomId())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 내부 호출에서 페이지 값이 생략돼도 REST 기본값과 동일하게 처리한다. */
        int page = query.page() == null ? DEFAULT_PAGE : query.page();
        int size = query.size() == null ? DEFAULT_SIZE : query.size();

        /* 음수 페이지나 1~100 범위를 벗어난 페이지 크기는 Z-001 입력 오류로 거절한다. */
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 현재 날짜를 한 번만 읽고 생략된 기간을 최근 3개월부터 오늘까지로 계산한다. */
        LocalDate today = LocalDate.now(clock);
        LocalDate from = query.from() == null ? today.minusMonths(DEFAULT_MONTH_RANGE) : query.from();
        LocalDate to = query.to() == null ? today : query.to();

        /* 시작 날짜가 종료 날짜보다 늦으면 범위가 성립하지 않으므로 Z-001로 거절한다. */
        if (from.isAfter(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 검증된 인증·필터·페이지 값만 저장소와 외부 Port에 전달하도록 묶어 반환한다. */
        return new ResolvedQuery(
                query.companyId(),
                query.requesterMemberId(),
                query.companyWideRead(),
                query.projectId(),
                query.meetingRoomId(),
                from,
                to,
                query.status(),
                query.scope(),
                page,
                size
        );
    }

    /* 명시된 프로젝트와 회의실 필터가 요청 회사의 리소스인지 배치 조회 계약으로 확인한다. */
    private void validateFilterReferences(ResolvedQuery query) {
        /* 프로젝트가 지정되면 soft delete 여부와 무관한 회사 범위 표시 조회로 소속을 확인한다. */
        if (query.projectId() != null
                && projectQueryPort.findProjects(query.companyId(), List.of(query.projectId())).stream()
                .noneMatch(project -> project.projectId().equals(query.projectId()))) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        /* 회의실이 지정되면 비활성 여부와 무관한 회사 범위 표시 조회로 소속을 확인한다. */
        if (query.meetingRoomId() != null
                && meetingRoomQueryPort.findMeetingRooms(query.companyId(), List.of(query.meetingRoomId())).stream()
                .noneMatch(room -> room.meetingRoomId().equals(query.meetingRoomId()))) {
            throw new BusinessException(MeetingRoomErrorCode.MEETING_ROOM_NOT_FOUND);
        }
    }

    /* 선택 식별자가 존재할 때 양수가 아닌 값인지 판단한다. */
    private boolean isInvalidOptionalId(Long id) {
        /* null은 필터 생략이고 0 이하 값만 잘못된 식별자로 처리한다. */
        return id != null && id <= 0L;
    }

    /* 요청한 회의실 전체가 회사 범위 결과에 존재하는지 확인하고 식별자 맵으로 만든다. */
    private Map<Long, MeetingRoomSnapshot> indexMeetingRooms(
            List<Long> requestedIds,
            List<MeetingRoomSnapshot> snapshots
    ) {
        /* 조회 순서와 무관하게 목록 행이 식별자로 표시값을 찾을 수 있도록 맵을 만든다. */
        Map<Long, MeetingRoomSnapshot> indexed = new LinkedHashMap<>();
        for (MeetingRoomSnapshot snapshot : snapshots) {
            indexed.put(snapshot.meetingRoomId(), snapshot);
        }

        /* 참조 회의실이 누락되면 불완전한 정상 응답 대신 데이터 계약 위반을 드러낸다. */
        if (indexed.size() != requestedIds.size() || !indexed.keySet().containsAll(requestedIds)) {
            throw new IllegalStateException("회의가 참조하는 회의실 표시 정보를 조회할 수 없습니다.");
        }

        /* 응답 조립 중 외부에서 값을 바꾸지 못하도록 불변 맵으로 반환한다. */
        return Map.copyOf(indexed);
    }

    /* 요청한 프로젝트 전체가 회사 범위 결과에 존재하는지 확인하고 식별자 맵으로 만든다. */
    private Map<Long, ProjectSnapshot> indexProjects(
            List<Long> requestedIds,
            List<ProjectSnapshot> snapshots
    ) {
        /* 조회 순서와 무관하게 목록 행이 식별자로 표시값을 찾을 수 있도록 맵을 만든다. */
        Map<Long, ProjectSnapshot> indexed = new LinkedHashMap<>();
        for (ProjectSnapshot snapshot : snapshots) {
            indexed.put(snapshot.projectId(), snapshot);
        }

        /* 참조 프로젝트가 누락되면 임의 표시값을 만들지 않고 데이터 계약 위반으로 실패한다. */
        if (indexed.size() != requestedIds.size() || !indexed.keySet().containsAll(requestedIds)) {
            throw new IllegalStateException("회의가 참조하는 프로젝트 표시 정보를 조회할 수 없습니다.");
        }

        /* 응답 조립 중 외부에서 값을 바꾸지 못하도록 불변 맵으로 반환한다. */
        return Map.copyOf(indexed);
    }

    /* C가 반환한 액션이 존재하는 회의만 현재 페이지 범위의 식별자 맵으로 만든다. */
    private Map<Long, Long> indexActionCounts(
            List<Long> requestedIds,
            List<MeetingActionCount> actionCounts
    ) {
        /* C 계약상 빠진 회의는 액션 0건이며 요청 페이지 밖 식별자는 응답에 반영하지 않는다. */
        Map<Long, Long> indexed = new LinkedHashMap<>();
        for (MeetingActionCount actionCount : actionCounts) {
            if (requestedIds.contains(actionCount.meetingId())) {
                indexed.put(actionCount.meetingId(), actionCount.actionCount());
            }
        }

        /* 응답 조립 중 집계값이 변경되지 않도록 불변 맵으로 반환한다. */
        return Map.copyOf(indexed);
    }

    /* 요청한 참석자 전체가 회사 범위 결과에 존재하는지 확인하고 식별자 맵으로 만든다. */
    private Map<Long, MemberSnapshot> indexMembers(
            List<Long> requestedIds,
            List<MemberSnapshot> snapshots
    ) {
        /* 조회 순서와 무관하게 목록 행이 식별자로 표시값을 찾을 수 있도록 맵을 만든다. */
        Map<Long, MemberSnapshot> indexed = new LinkedHashMap<>();
        for (MemberSnapshot snapshot : snapshots) {
            indexed.put(snapshot.memberId(), snapshot);
        }

        /* 참조 참석자가 누락되면 임의 표시값을 만들지 않고 데이터 계약 위반으로 실패한다. */
        if (indexed.size() != requestedIds.size() || !indexed.keySet().containsAll(requestedIds)) {
            throw new IllegalStateException("회의가 참조하는 참석자 표시 정보를 조회할 수 없습니다.");
        }

        /* 응답 조립 중 외부에서 값을 바꾸지 못하도록 불변 맵으로 반환한다. */
        return Map.copyOf(indexed);
    }

    /* 회의 조회 모델과 각 도메인의 표시값을 MEET-02 결과 한 건으로 변환한다. */
    private MeetingListResult.MeetingItem toResultItem(
            MeetingListSnapshot meeting,
            MeetingRoomSnapshot meetingRoom,
            ProjectSnapshot project,
            long actionCount,
            Long requesterMemberId,
            LocalDateTime now,
            Map<Long, MemberSnapshot> members
    ) {
        /* 카드 아바타는 host를 포함한 전체 참석자를 DB가 반환한 memberId 오름차순 그대로 담는다. */
        List<MeetingListResult.Attendee> attendees = meeting.attendeeMemberIds().stream()
                .map(members::get)
                .map(member -> new MeetingListResult.Attendee(member.memberId(), member.name()))
                .toList();

        /* 명세에 필요한 값만 엔티티 참조 없이 중첩 결과로 조립한다. */
        return new MeetingListResult.MeetingItem(
                meeting.meetingId(),
                meeting.title(),
                meeting.status(),
                meeting.startAt(),
                meeting.endAt(),
                meeting.attendeeMemberIds().size(),
                actionCount,
                meeting.hostMemberId().equals(requesterMemberId),
                MeetingEntryPolicy.isEntryAvailable(meeting.startAt(), meeting.endAt(), now),
                (int) Duration.between(meeting.startAt(), meeting.endAt()).toMinutes(),
                attendees,
                new MeetingListResult.MeetingRoom(meetingRoom.meetingRoomId(), meetingRoom.name()),
                new MeetingListResult.Project(project.projectId(), project.tag(), project.name())
        );
    }

    /* 검증과 기본값 처리가 끝난 서비스 내부 조회 조건이다. */
    private record ResolvedQuery(
            Long companyId,
            Long requesterMemberId,
            boolean companyWideRead,
            Long projectId,
            Long meetingRoomId,
            LocalDate from,
            LocalDate to,
            com.module06.backend.meeting.domain.model.MeetingStatus status,
            MeetingListScope scope,
            int page,
            int size
    ) {
    }
}
