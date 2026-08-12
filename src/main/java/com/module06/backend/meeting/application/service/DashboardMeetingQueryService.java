package com.module06.backend.meeting.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort.MeetingRoomSnapshot;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort.ProjectSnapshot;
import com.module06.backend.meeting.application.query.GetDashboardMeetingsQuery;
import com.module06.backend.meeting.application.result.DashboardMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetDashboardMeetingsUseCase;
import com.module06.backend.meeting.domain.model.DashboardMeetingScope;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository.DashboardMeetingCandidate;
import com.module06.backend.meeting.domain.repository.DashboardMeetingRepository.DashboardMeetingCriteria;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingAttendeeReference;

/*
 * MEET-17 대시보드 최근 회의 카드 조회를 조율하는 애플리케이션 서비스다.
 *
 * MEET-02의 배치 조회 로직을 새로 만들지 않고 회의실·프로젝트 Port를 그대로 재사용하되,
 * 조회 조건과 결과 DTO만 대시보드 전용으로 분리한다. 참석자 수는 D가 소유한 참석자
 * 테이블을 직접 배치 조회해 세므로 별도 외부 Port를 타지 않는다.
 *
 * originLabel은 요청 전체에 적용되는 단일 값이다 — owner는 상수 "Owner", me는 요청자
 * 본인의 팀 이름(팀 없는 Owner는 "Owner", 그 외 팀 정보 누락은 null)이며 기존 구성원 조회
 * Port만으로 채울 수 있다. team의 origin은 명세에 정의가 없어 null로 둔다.
 * hostLabel의 "(팀장)" 표기는 개설자가 그 팀의 팀장인지 판별해야 하므로, B의 findTeams
 * 배치 계약(MEET-17 팀·host 라벨 연결)이 연결되기 전까지는 항상 null로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardMeetingQueryService implements GetDashboardMeetingsUseCase {

    /* limit이 생략됐을 때 적용하는 기본 반환 개수다. */
    private static final int DEFAULT_LIMIT = 5;

    /* 한 번에 반환을 허용하는 최대 개수다. */
    private static final int MAX_LIMIT = 20;

    /* 스코프·요청자 조건으로 최근 회의 후보를 조회하는 저장소다. */
    private final DashboardMeetingRepository dashboardMeetingRepository;

    /* 후보 회의의 참석자 식별자를 일괄 조회해 카드별 인원수를 세는 D 내부 저장소다. */
    private final MeetingQueryRepository meetingQueryRepository;

    /* 대시보드 카드에 표시할 회의실 이름을 조회하는 D 내부 Port다. */
    private final MeetingRoomQueryPort meetingRoomQueryPort;

    /* 대시보드 카드에 표시할 프로젝트 태그를 조회하는 C 연동 Port다. */
    private final ProjectQueryPort projectQueryPort;

    /* scope=me의 originLabel에 쓸 요청자 본인의 팀 이름을 조회하는 B 연동 Port다. */
    private final MemberQueryPort memberQueryPort;

    /* 인증 사용자의 요청 스코프에 해당하는 최근 회의를 표시 정보와 함께 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public DashboardMeetingListResult getDashboardMeetings(GetDashboardMeetingsQuery query) {
        /* 인증 식별자·scope·limit을 검증하고 생략된 limit 기본값을 확정한다. */
        int limit = validateAndResolveLimit(query);

        /* 역할과 scope 조합이 허용되는지 저장소 접근 전에 확인한다. */
        validateRoleScope(query);

        /* 회사·스코프·요청자 조건을 저장소에 적용해 최근 회의 후보만 조회한다. */
        List<DashboardMeetingCandidate> candidates = dashboardMeetingRepository.findDashboardMeetings(
                new DashboardMeetingCriteria(
                        query.companyId(),
                        query.scope(),
                        query.requesterMemberId(),
                        query.requesterTeamId(),
                        limit
                )
        );

        /* 후보가 없으면 참석자·회의실·프로젝트 조회 없이 빈 목록을 정상 반환한다. */
        if (candidates.isEmpty()) {
            return new DashboardMeetingListResult(List.of());
        }

        /* 후보 회의 식별자를 한 번에 전달해 회의별 참석자 수를 배치로 센다. */
        List<Long> meetingIds = candidates.stream().map(DashboardMeetingCandidate::meetingId).toList();
        Map<Long, Long> attendeeCounts = meetingQueryRepository
                .findMeetingAttendees(query.companyId(), meetingIds)
                .stream()
                .collect(Collectors.groupingBy(MeetingAttendeeReference::meetingId, Collectors.counting()));

        /* 중복 없는 회의실 표시 정보를 한 번의 Port 호출로 조회해 색인한다. */
        List<Long> meetingRoomIds = candidates.stream()
                .map(DashboardMeetingCandidate::meetingRoomId)
                .distinct()
                .toList();
        Map<Long, MeetingRoomSnapshot> meetingRooms = indexMeetingRooms(
                meetingRoomIds,
                meetingRoomQueryPort.findMeetingRooms(query.companyId(), meetingRoomIds)
        );

        /* 중복 없는 프로젝트 표시 정보를 한 번의 Port 호출로 조회해 색인한다. */
        List<Long> projectIds = candidates.stream()
                .map(DashboardMeetingCandidate::projectId)
                .distinct()
                .toList();
        Map<Long, ProjectSnapshot> projects = indexProjects(
                projectIds,
                projectQueryPort.findProjects(query.companyId(), projectIds)
        );

        /* originLabel은 회의마다 다르지 않고 요청 전체에 적용되는 단일 값이라 한 번만 계산한다. */
        String originLabel = resolveOriginLabel(query);

        /* 저장소가 보장한 정렬을 유지하며 카드별 표시 정보가 완성된 결과로 변환한다. */
        List<DashboardMeetingListResult.MeetingItem> meetings = candidates.stream()
                .map(candidate -> toResultItem(
                        candidate,
                        attendeeCounts.getOrDefault(candidate.meetingId(), 0L).intValue(),
                        meetingRooms.get(candidate.meetingRoomId()),
                        projects.get(candidate.projectId()),
                        originLabel
                ))
                .toList();

        return new DashboardMeetingListResult(meetings);
    }

    /* 인증 식별자와 scope를 검증하고 생략된 limit을 기본값으로 채운다. */
    private int validateAndResolveLimit(GetDashboardMeetingsQuery query) {
        /* 인증 회사·구성원을 식별할 수 없거나 scope가 없으면 저장소 접근 전에 거절한다. */
        if (query == null
                || query.companyId() == null
                || query.companyId() <= 0L
                || query.requesterMemberId() == null
                || query.requesterMemberId() <= 0L
                || query.scope() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* limit 생략은 기본 5건이며, 1~20 범위를 벗어나면 Z-001로 거절한다. */
        int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return limit;
    }

    /* owner는 OWNER만, team은 LEADER만 조회할 수 있고 me는 모든 인증 역할을 허용한다. */
    private void validateRoleScope(GetDashboardMeetingsQuery query) {
        if (query.scope() == DashboardMeetingScope.OWNER && !"OWNER".equals(query.requesterRole())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        if (query.scope() == DashboardMeetingScope.TEAM && !"LEADER".equals(query.requesterRole())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    /* owner는 상수, me는 요청자 본인의 팀 이름, team은 명세에 정의가 없어 null로 둔다. */
    private String resolveOriginLabel(GetDashboardMeetingsQuery query) {
        return switch (query.scope()) {
            case OWNER -> "Owner";
            case ME -> resolveRequesterTeamLabel(query);
            case TEAM -> null;
        };
    }

    /* 요청자 본인의 팀 이름을 조회한다. 팀이 없으면 Owner만 상수로 대체하고 그 외는 null이다. */
    private String resolveRequesterTeamLabel(GetDashboardMeetingsQuery query) {
        /* 팀이 없는 요청자는 구성원 조회 없이도 Owner 여부만으로 값이 정해진다. */
        if (query.requesterTeamId() == null) {
            return "OWNER".equals(query.requesterRole()) ? "Owner" : null;
        }

        /* 팀이 있으면 요청자 본인 한 명만 조회해 팀 이름을 읽는다 — 회의별 반복 조회가 아니다. */
        return memberQueryPort
                .findMembersIncludingDeleted(query.companyId(), List.of(query.requesterMemberId()))
                .stream()
                .findFirst()
                .map(MemberSnapshot::teamName)
                .orElse(null);
    }

    /* 요청한 회의실 전체가 회사 범위 결과에 존재하는지 확인하고 식별자 맵으로 만든다. */
    private Map<Long, MeetingRoomSnapshot> indexMeetingRooms(
            List<Long> requestedIds,
            List<MeetingRoomSnapshot> snapshots
    ) {
        Map<Long, MeetingRoomSnapshot> indexed = new LinkedHashMap<>();
        for (MeetingRoomSnapshot snapshot : snapshots) {
            indexed.put(snapshot.meetingRoomId(), snapshot);
        }

        /* 참조 회의실이 누락되면 불완전한 정상 응답 대신 데이터 계약 위반을 드러낸다. */
        if (indexed.size() != requestedIds.size() || !indexed.keySet().containsAll(requestedIds)) {
            throw new IllegalStateException("대시보드 회의가 참조하는 회의실 표시 정보를 조회할 수 없습니다.");
        }
        return Map.copyOf(indexed);
    }

    /* 요청한 프로젝트 전체가 회사 범위 결과에 존재하는지 확인하고 식별자 맵으로 만든다. */
    private Map<Long, ProjectSnapshot> indexProjects(
            List<Long> requestedIds,
            List<ProjectSnapshot> snapshots
    ) {
        Map<Long, ProjectSnapshot> indexed = new LinkedHashMap<>();
        for (ProjectSnapshot snapshot : snapshots) {
            indexed.put(snapshot.projectId(), snapshot);
        }

        /* 참조 프로젝트가 누락되면 임의 태그를 만들지 않고 데이터 계약 위반으로 실패한다. */
        if (indexed.size() != requestedIds.size() || !indexed.keySet().containsAll(requestedIds)) {
            throw new IllegalStateException("대시보드 회의가 참조하는 프로젝트 표시 정보를 조회할 수 없습니다.");
        }
        return Map.copyOf(indexed);
    }

    /* 후보 회의와 각 도메인의 표시값을 MEET-17 카드 한 건으로 변환한다. */
    private DashboardMeetingListResult.MeetingItem toResultItem(
            DashboardMeetingCandidate candidate,
            int attendeeCount,
            MeetingRoomSnapshot meetingRoom,
            ProjectSnapshot project,
            String originLabel
    ) {
        /* hostLabel의 "(팀장)" 판별은 B의 findTeams 배치 계약이 연결되기 전까지 null로 둔다. */
        return new DashboardMeetingListResult.MeetingItem(
                candidate.meetingId(),
                candidate.title(),
                project.tag(),
                candidate.status(),
                meetingRoom.name(),
                candidate.startAt(),
                attendeeCount,
                originLabel,
                null
        );
    }
}
