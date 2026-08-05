package com.module06.backend.meeting.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort;
import com.module06.backend.meeting.application.port.out.MeetingRoomQueryPort.MeetingRoomSnapshot;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort;
import com.module06.backend.meeting.application.port.out.ProjectQueryPort.ProjectSnapshot;
import com.module06.backend.meeting.application.query.GetUpcomingMeetingsQuery;
import com.module06.backend.meeting.application.result.UpcomingMeetingListResult;
import com.module06.backend.meeting.application.usecase.GetUpcomingMeetingsUseCase;
import com.module06.backend.meeting.domain.model.MeetingEntryPolicy;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.UpcomingMeetingSnapshot;

/*
 * MEET-03 내 예정 회의 목록 조회를 조율하는 애플리케이션 서비스다.
 *
 * 회의 후보와 참석자 수는 D 회의 저장소에서 읽고, 회의실과 프로젝트 표시 정보는 각 Port로
 * 일괄 조회해 회의별 반복 호출 없이 홈 대시보드 카드 결과를 완성한다.
 */
@Service
@RequiredArgsConstructor
public class UpcomingMeetingQueryService implements GetUpcomingMeetingsUseCase {

    /* Query Parameter가 생략됐을 때 적용하는 예정 회의 기본 조회 개수다. */
    private static final int DEFAULT_LIMIT = 5;

    /* 한 요청에서 허용하는 예정 회의 최대 조회 개수다. */
    private static final int MAX_LIMIT = 20;

    /* 회사·참석자·상태·종료 시각 조건으로 예정 회의를 조회하는 저장소다. */
    private final MeetingQueryRepository meetingQueryRepository;

    /* 회의 카드에 표시할 회의실 이름을 회사 범위에서 일괄 조회하는 D 내부 Port다. */
    private final MeetingRoomQueryPort meetingRoomQueryPort;

    /* 회의 카드에 표시할 프로젝트 태그·이름·색상을 일괄 조회하는 C 연동 Port다. */
    private final ProjectQueryPort projectQueryPort;

    /* 입장 가능 시간과 예정 회의 종료 조건을 같은 KST 현재 시각으로 판단하는 시계다. */
    private final Clock clock;

    /* 로그인 사용자가 참석자로 등록된 예정·진행 중 회의를 가까운 순서로 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public UpcomingMeetingListResult getUpcomingMeetings(GetUpcomingMeetingsQuery query) {
        /* 인증 식별자와 limit을 검증하고 생략된 limit에는 명세 기본값을 적용한다. */
        int limit = validateAndResolveLimit(query);

        /* 한 요청 안의 모든 시간 판정이 동일하도록 현재 시각을 한 번만 읽는다. */
        LocalDateTime now = LocalDateTime.now(clock);

        /* 참석자·회사·상태·종료 시각 조건을 저장소에서 적용한 회의 후보를 제한 조회한다. */
        List<UpcomingMeetingSnapshot> meetings = meetingQueryRepository.findUpcomingMeetings(
                query.companyId(),
                query.requesterMemberId(),
                now,
                limit
        );

        /* 예정 회의가 없으면 외부 표시 정보 Port를 호출하지 않고 빈 목록을 정상 반환한다. */
        if (meetings.isEmpty()) {
            return new UpcomingMeetingListResult(List.of());
        }

        /* 중복을 제거한 회의실 식별자를 D 내부 Port에 한 번에 전달한다. */
        List<Long> meetingRoomIds = meetings.stream()
                .map(UpcomingMeetingSnapshot::meetingRoomId)
                .distinct()
                .toList();
        Map<Long, MeetingRoomSnapshot> meetingRooms = indexMeetingRooms(
                meetingRoomIds,
                meetingRoomQueryPort.findMeetingRooms(query.companyId(), meetingRoomIds)
        );

        /* 중복을 제거한 프로젝트 식별자를 C 연동 Port에 한 번에 전달한다. */
        List<Long> projectIds = meetings.stream()
                .map(UpcomingMeetingSnapshot::projectId)
                .distinct()
                .toList();
        Map<Long, ProjectSnapshot> projects = indexProjects(
                projectIds,
                projectQueryPort.findProjects(query.companyId(), projectIds)
        );

        /* 저장소가 보장한 startAt·meetingId 순서를 유지하면서 카드 결과로 변환한다. */
        List<UpcomingMeetingListResult.MeetingItem> items = meetings.stream()
                .map(meeting -> toResultItem(
                        meeting,
                        query.requesterMemberId(),
                        now,
                        meetingRooms.get(meeting.meetingRoomId()),
                        projects.get(meeting.projectId())
                ))
                .toList();

        /* 조회 결과가 없을 수도 있는 정상 목록 계약으로 전체 카드를 반환한다. */
        return new UpcomingMeetingListResult(items);
    }

    /* 인증 식별자와 조회 개수를 검증하고 실제 적용할 limit을 반환한다. */
    private int validateAndResolveLimit(GetUpcomingMeetingsQuery query) {
        /* 인증 주체를 식별할 수 없으면 저장소 조회 전에 공통 입력 오류로 거절한다. */
        if (query == null
                || query.companyId() == null
                || query.companyId() <= 0L
                || query.requesterMemberId() == null
                || query.requesterMemberId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* Controller 기본값과 무관하게 내부 호출에서 null이면 동일한 기본 5건을 적용한다. */
        int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();

        /* 1~20 범위를 벗어난 조회는 명세의 Z-001 입력값 오류로 거절한다. */
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        /* 검증을 통과한 조회 개수를 저장소 제한에 사용하도록 반환한다. */
        return limit;
    }

    /* 요청한 회의실 전체가 회사 범위 조회 결과에 존재하는지 확인하고 식별자 맵으로 만든다. */
    private Map<Long, MeetingRoomSnapshot> indexMeetingRooms(
            List<Long> requestedIds,
            List<MeetingRoomSnapshot> snapshots
    ) {
        /* 조회 순서와 무관하게 회의 카드가 식별자로 표시값을 찾을 수 있도록 맵을 만든다. */
        Map<Long, MeetingRoomSnapshot> indexed = new LinkedHashMap<>();
        for (MeetingRoomSnapshot snapshot : snapshots) {
            indexed.put(snapshot.meetingRoomId(), snapshot);
        }

        /* 회의가 참조하는 회의실이 누락되면 불완전한 정상 응답 대신 데이터 계약 위반을 드러낸다. */
        if (indexed.size() != requestedIds.size() || !indexed.keySet().containsAll(requestedIds)) {
            throw new IllegalStateException("예정 회의가 참조하는 회의실 표시 정보를 조회할 수 없습니다.");
        }

        /* 응답 조립 중 외부에서 값을 바꾸지 못하도록 불변 맵으로 반환한다. */
        return Map.copyOf(indexed);
    }

    /* 요청한 프로젝트 전체가 회사 범위 조회 결과에 존재하는지 확인하고 식별자 맵으로 만든다. */
    private Map<Long, ProjectSnapshot> indexProjects(
            List<Long> requestedIds,
            List<ProjectSnapshot> snapshots
    ) {
        /* 조회 순서와 무관하게 회의 카드가 식별자로 표시값을 찾을 수 있도록 맵을 만든다. */
        Map<Long, ProjectSnapshot> indexed = new LinkedHashMap<>();
        for (ProjectSnapshot snapshot : snapshots) {
            indexed.put(snapshot.projectId(), snapshot);
        }

        /* 프로젝트 표시 정보가 누락되면 임의 태그·색상을 만들지 않고 데이터 계약 위반으로 실패한다. */
        if (indexed.size() != requestedIds.size() || !indexed.keySet().containsAll(requestedIds)) {
            throw new IllegalStateException("예정 회의가 참조하는 프로젝트 표시 정보를 조회할 수 없습니다.");
        }

        /* 응답 조립 중 외부에서 값을 바꾸지 못하도록 불변 맵으로 반환한다. */
        return Map.copyOf(indexed);
    }

    /* 회의 조회 모델과 외부 표시값을 MEET-03 카드 한 건으로 변환한다. */
    private UpcomingMeetingListResult.MeetingItem toResultItem(
            UpcomingMeetingSnapshot meeting,
            Long requesterMemberId,
            LocalDateTime now,
            MeetingRoomSnapshot meetingRoom,
            ProjectSnapshot project
    ) {
        /* MEET-07과 동일한 입장 허용 시간 정책으로 프론트 버튼 활성화 값을 계산한다. */
        boolean entryAvailable = MeetingEntryPolicy.isEntryAvailable(
                meeting.startAt(),
                meeting.endAt(),
                now
        );

        /* 회의 원본과 각 Port의 표시 정보를 엔티티 참조 없는 결과 값으로 조립한다. */
        return new UpcomingMeetingListResult.MeetingItem(
                meeting.meetingId(),
                meeting.title(),
                meeting.status(),
                meeting.startAt(),
                meeting.endAt(),
                meeting.attendeeCount(),
                meeting.hostMemberId().equals(requesterMemberId),
                entryAvailable,
                new UpcomingMeetingListResult.MeetingRoom(
                        meetingRoom.meetingRoomId(),
                        meetingRoom.name()
                ),
                new UpcomingMeetingListResult.Project(
                        project.projectId(),
                        project.tag(),
                        project.name(),
                        project.color()
                )
        );
    }
}
