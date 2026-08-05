package com.module06.backend.meeting.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meeting.application.port.out.MemberQueryPort;
import com.module06.backend.meeting.application.port.out.MemberQueryPort.MemberSnapshot;
import com.module06.backend.meeting.application.port.in.MeetingQueryPort;
import com.module06.backend.meeting.application.query.GetMeetingAttendeesQuery;
import com.module06.backend.meeting.application.result.MeetingAttendeeReferenceResult;
import com.module06.backend.meeting.application.result.MeetingAttendeesResult;
import com.module06.backend.meeting.application.result.MeetingHistoryResult;
import com.module06.backend.meeting.application.result.MeetingTopicResult;
import com.module06.backend.meeting.application.result.ProjectMeetingHistoryResult;
import com.module06.backend.meeting.application.usecase.GetMeetingAttendeesUseCase;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository;
import com.module06.backend.meeting.domain.repository.MeetingQueryRepository.MeetingSnapshot;
import com.module06.backend.meeting.exception.MeetingErrorCode;

/*
 * 회의와 참석자 읽기 기능을 조율하는 애플리케이션 서비스다.
 *
 * RESULT-01과 E가 사용하는 공개 MeetingQueryPort가 같은 조회 저장소와 구성원 해석 규칙을
 * 재사용해 REST와 내부 도메인 연동의 결과가 갈라지지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class MeetingQueryService implements GetMeetingAttendeesUseCase, MeetingQueryPort {

    /* 회사 범위의 회의와 참석자 식별자를 읽는 조회 전용 저장소다. */
    private final MeetingQueryRepository meetingQueryRepository;

    /* 참석자 식별자를 이름과 팀 표시 정보로 일괄 해석하는 B도메인 연동 포트다. */
    private final MemberQueryPort memberQueryPort;

    /*
     * 회의 존재와 열람 권한을 검증한 뒤 개설자 우선 참석자 목록을 반환한다.
     *
     * @param query 인증 정보와 대상 회의 식별자를 합친 조회 조건
     * @return 개설자를 첫 번째로 포함한 회의 참석자 결과
     */
    @Override
    @Transactional(readOnly = true)
    public MeetingAttendeesResult getMeetingAttendees(GetMeetingAttendeesQuery query) {
        /* Controller 밖에서 호출돼도 잘못된 인증·식별자 값이 조회까지 도달하지 않게 한다. */
        validateRequiredValues(query);

        /* companyId 조건을 함께 사용해 타 회사 회의도 존재하지 않는 회의처럼 처리한다. */
        MeetingSnapshot meeting = meetingQueryRepository
                .findMeeting(query.companyId(), query.meetingId())
                .orElseThrow(() -> new BusinessException(MeetingErrorCode.MEETING_NOT_FOUND));

        /* OWNER·ADMIN 또는 회의 개설자·참석자만 명단을 열람할 수 있다. */
        if (!canReadMeeting(query, meeting)) {
            throw new BusinessException(MeetingErrorCode.MEETING_READ_FORBIDDEN);
        }

        /* B도메인에서 구성원 표시 정보를 한 번에 조회하고 누락 여부까지 검증한다. */
        Map<Long, MemberSnapshot> members = findAndValidateMembers(
                meeting.companyId(),
                meeting.attendeeMemberIds()
        );

        /* 데이터베이스 정렬과 무관하게 개설자가 항상 첫 번째가 되는 응답 순서를 만든다. */
        List<Long> orderedMemberIds = hostFirst(
                meeting.hostMemberId(),
                meeting.attendeeMemberIds()
        );

        /* 구성원 표시 정보와 회의 개설자 여부를 RESULT-01 결과로 변환한다. */
        List<MeetingAttendeesResult.Attendee> attendees = orderedMemberIds.stream()
                .map(members::get)
                .map(member -> new MeetingAttendeesResult.Attendee(
                        member.memberId(),
                        member.name(),
                        member.teamName(),
                        member.memberId().equals(meeting.hostMemberId())
                ))
                .toList();

        /* 대상 회의 식별자와 전체 참석자 목록을 반환한다. */
        return new MeetingAttendeesResult(meeting.meetingId(), attendees);
    }

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

    /* 필수 인증 값과 회의 식별자가 올바른지 확인한다. */
    private void validateRequiredValues(GetMeetingAttendeesQuery query) {
        /* 식별할 수 없는 요청은 공통 입력값 오류로 처리한다. */
        if (query == null
                || query.companyId() == null
                || query.requesterMemberId() == null
                || query.meetingId() == null
                || query.meetingId() <= 0L) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /* 요청자가 회사 범위 안의 대상 회의를 읽을 수 있는지 판단한다. */
    private boolean canReadMeeting(GetMeetingAttendeesQuery query, MeetingSnapshot meeting) {
        /* OWNER 또는 관리자 플래그가 있는 계정은 회사 내 회의 명단을 조회할 수 있다. */
        boolean elevated = query.requesterAdmin() || "OWNER".equals(query.requesterRole());

        /* 개설자와 참석자는 역할과 무관하게 자신이 포함된 회의를 열람할 수 있다. */
        boolean host = meeting.hostMemberId().equals(query.requesterMemberId());
        boolean attendee = meeting.attendeeMemberIds().contains(query.requesterMemberId());

        /* 세 조건 중 하나라도 만족하면 회의 명단 열람을 허용한다. */
        return elevated || host || attendee;
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
        /* RESULT-01과 동일한 개설자 우선 순서로 인수인계 스냅샷 참석자를 만든다. */
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
