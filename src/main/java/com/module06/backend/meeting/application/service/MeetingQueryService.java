package com.module06.backend.meeting.application.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;
import com.module06.backend.meeting.application.port.in.MeetingQueryPort;
import com.module06.backend.meeting.application.result.MeetingAttendeeReferenceResult;
import com.module06.backend.meeting.application.result.MeetingHistoryResult;
import com.module06.backend.meeting.application.result.MeetingTopicResult;
import com.module06.backend.meeting.application.result.ProjectMeetingHistoryResult;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingSnapshot;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * 회의 읽기 기능을 조율하는 애플리케이션 서비스다.
 *
 * E·C가 사용하는 공개 MeetingQueryPort를 구현하며, 참석자 이름·팀 해석은 B도메인 포트로
 * 매번 조회해 회의 도메인이 구성원 표시 정보를 캐시하지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class MeetingQueryService implements MeetingQueryPort {

    /* 회사 범위의 회의와 참석자 식별자를 읽는 조회 전용 저장소다. */
    private final MeetingQueryRepository meetingQueryRepository;

    /* 참석자 식별자를 이름과 팀 표시 정보로 일괄 해석하는 B도메인 연동 포트다. */
    private final MemberQueryPort memberQueryPort;

    /* 회사 범위의 출처 회의와 참석자 표시 정보를 E finalize 스냅샷용으로 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public Optional<MeetingHistoryResult> findMeeting(Long companyId, Long meetingId) {
        /* 잘못된 테넌트나 식별자는 다른 도메인 Adapter가 빈 결과로 처리할 수 있게 Optional.empty를 반환한다. */
        if (companyId == null || companyId <= 0L || meetingId == null || meetingId <= 0L) {
            return Optional.empty();
        }

        /* 회사 조건이 적용된 회의 조회 결과를 참석자 이름이 포함된 히스토리 결과로 변환한다. */
        return meetingQueryRepository.findMeeting(companyId, meetingId)
                .map(meeting -> toMeetingHistoryResult(
                        meeting,
                        findAndValidateMembers(meeting.companyId(), meeting.attendeeMemberIds())
                ));
    }

    /* 프로젝트에 연결된 회의를 E 타임라인 계약의 시간순 결과로 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public List<ProjectMeetingHistoryResult> findProjectMeetingsOrdered(Long companyId, Long projectId) {
        /* 테넌트나 프로젝트를 식별할 수 없으면 조회 없이 빈 타임라인을 반환한다. */
        if (companyId == null || companyId <= 0L || projectId == null || projectId <= 0L) {
            return List.of();
        }

        /* Repository가 보장한 startAt·id 순서를 유지하면서 애플리케이션 결과로 변환한다. */
        return meetingQueryRepository.findProjectMeetingsOrdered(companyId, projectId)
                .stream()
                .map(meeting -> new ProjectMeetingHistoryResult(
                        meeting.meetingId(),
                        meeting.title(),
                        meeting.startAt(),
                        meeting.hostMemberId(),
                        meeting.status()
                ))
                .toList();
    }

    /* 프로젝트 목록에 표시할 취소되지 않은 회의 수를 회사 범위에서 일괄 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countMeetingsByProjectIds(Long companyId, List<Long> projectIds) {
        /* 잘못된 테넌트나 빈 프로젝트 목록은 저장소 IN 조회 없이 빈 결과로 처리한다. */
        if (companyId == null || companyId <= 0L || projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }

        /* null·0·음수 식별자를 제외하고 요청 순서를 유지한 채 중복 프로젝트를 한 번만 조회한다. */
        List<Long> distinctProjectIds = projectIds.stream()
                .filter(projectId -> projectId != null && projectId > 0L)
                .distinct()
                .toList();

        /* 유효한 프로젝트가 하나도 없으면 데이터베이스를 호출하지 않고 빈 결과를 반환한다. */
        if (distinctProjectIds.isEmpty()) {
            return Map.of();
        }

        /* 저장소의 실제 집계 결과를 받아 회의가 없는 요청 프로젝트도 명시적인 0으로 채운다. */
        Map<Long, Long> storedCounts = meetingQueryRepository.countMeetingsByProjectIds(
                companyId,
                distinctProjectIds
        );
        Map<Long, Long> completedCounts = new LinkedHashMap<>();
        distinctProjectIds.forEach(projectId -> completedCounts.put(
                projectId,
                storedCounts.getOrDefault(projectId, 0L)
        ));

        /* C가 프로젝트 목록 응답을 조립하는 동안 결과가 변경되지 않도록 불변 맵으로 반환한다. */
        return Collections.unmodifiableMap(completedCounts);
    }

    /* 회사 범위에 속한 여러 회의의 대주제와 소주제를 E 배치 계약으로 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public List<MeetingTopicResult> findMeetingTopics(Long companyId, List<Long> meetingIds) {
        /* 테넌트나 회의 목록이 없으면 저장소 IN 조회 없이 빈 결과를 반환한다. */
        if (companyId == null || companyId <= 0L || meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }

        /* 영속성 어댑터가 회사 경계와 200개 배치를 적용한 안건을 외부 Port 결과로 변환한다. */
        return meetingQueryRepository.findMeetingTopics(companyId, meetingIds)
                .stream()
                .map(topic -> new MeetingTopicResult(
                        topic.meetingId(),
                        topic.topicId(),
                        topic.parentTopicId(),
                        topic.type(),
                        topic.content(),
                        topic.sortOrder()
                ))
                .toList();
    }

    /* 회사 범위에 속한 여러 회의의 참석자 식별자 쌍을 E 배치 계약으로 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public List<MeetingAttendeeReferenceResult> findMeetingAttendees(Long companyId, List<Long> meetingIds) {
        /* 테넌트나 회의 목록이 없으면 저장소 IN 조회 없이 빈 결과를 반환한다. */
        if (companyId == null || companyId <= 0L || meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }

        /* 영속성 어댑터가 200개씩 분할하고 회사 범위를 적용한 결과를 외부 Port용 값으로 변환한다. */
        return meetingQueryRepository.findMeetingAttendees(companyId, meetingIds)
                .stream()
                .map(reference -> new MeetingAttendeeReferenceResult(
                        reference.meetingId(),
                        reference.memberId()
                ))
                .toList();
    }

    /* 참석자 표시 정보를 일괄 조회하고 모든 식별자가 해석됐는지 확인한다. */
    private Map<Long, MemberSnapshot> findAndValidateMembers(Long companyId, List<Long> memberIds) {
        /* 호출 순서와 무관하게 식별자로 빠르게 찾을 수 있도록 결과를 맵으로 정리한다. */
        Map<Long, MemberSnapshot> members = new LinkedHashMap<>();
        for (MemberSnapshot member : memberQueryPort.findActiveMembers(companyId, memberIds)) {
            members.put(member.memberId(), member);
        }

        /* 타 회사·삭제 구성원·누락 결과를 조용히 숨기지 않고 명단 계약 오류로 처리한다. */
        if (members.size() != memberIds.size() || !members.keySet().containsAll(memberIds)) {
            throw new BusinessException(MeetingErrorCode.INVALID_ATTENDEES);
        }

        /* 응답 조립 도중 조회 결과가 변경되지 않도록 불변 맵으로 반환한다. */
        return Map.copyOf(members);
    }

    /* 개설자를 첫 번째로 두고 나머지 참석자의 저장소 조회 순서를 유지한다. */
    private List<Long> hostFirst(Long hostMemberId, List<Long> attendeeMemberIds) {
        /* 개설자를 먼저 추가한 뒤 동일 식별자를 제외한 참석자를 순서대로 추가한다. */
        List<Long> ordered = new ArrayList<>();
        ordered.add(hostMemberId);
        attendeeMemberIds.stream()
                .filter(memberId -> !memberId.equals(hostMemberId))
                .forEach(ordered::add);

        /* 외부에서 순서를 바꾸지 못하도록 불변 목록으로 반환한다. */
        return List.copyOf(ordered);
    }

    /* 회의 조회 모델과 구성원 표시 정보를 E 단건 회의 히스토리 결과로 변환한다. */
    private MeetingHistoryResult toMeetingHistoryResult(
            MeetingSnapshot meeting,
            Map<Long, MemberSnapshot> members
    ) {
        /* 개설자 우선 순서로 인수인계 스냅샷 참석자를 만든다. */
        List<MeetingHistoryResult.Attendee> attendees = hostFirst(
                meeting.hostMemberId(),
                meeting.attendeeMemberIds()
        ).stream()
                .map(members::get)
                .map(member -> new MeetingHistoryResult.Attendee(
                        member.memberId(),
                        member.name(),
                        member.teamName()
                ))
                .toList();

        /* D가 실제로 소유하는 회의 메타와 B에서 해석한 참석자 표시 정보만 반환한다. */
        return new MeetingHistoryResult(
                meeting.meetingId(),
                meeting.projectId(),
                meeting.title(),
                meeting.status(),
                meeting.startAt(),
                meeting.endAt(),
                meeting.startedAt(),
                meeting.endedAt(),
                meeting.hostMemberId(),
                attendees
        );
    }
}
