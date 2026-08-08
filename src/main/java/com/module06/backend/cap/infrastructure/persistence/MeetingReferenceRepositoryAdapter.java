package com.module06.backend.cap.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 MeetingReferenceRepository 계약을 JPA로 구현하는 어댑터.
*/
@Component
@RequiredArgsConstructor
public class MeetingReferenceRepositoryAdapter implements MeetingReferenceRepository {

    private final SpringDataCapMeetingReferenceRepository springDataCapMeetingReferenceRepository;
    private final SpringDataCapMeetingAttendeeReferenceRepository springDataCapMeetingAttendeeReferenceRepository;

    // meeting 테이블에 그 id로 SELECT — 존재 여부만 확인
    @Override
    public boolean existsById(Long meetingId) {
        return springDataCapMeetingReferenceRepository.existsById(meetingId);
    }

    // meeting_attendee 복합키(meetingId, memberId)로 SELECT — 참석자 명단에 있는지 확인
    @Override
    public boolean isAttendee(Long meetingId, Long memberId) {
        return springDataCapMeetingAttendeeReferenceRepository.existsById(new MeetingAttendeeId(meetingId, memberId));
    }

    // meeting.host_member_id와 비교 — 이 사람이 회의 담당자(Host)인지 확인
    @Override
    public boolean isHost(Long meetingId, Long memberId) {
        return springDataCapMeetingReferenceRepository.findById(meetingId)
                .map(meeting -> memberId != null && memberId.equals(meeting.getHostMemberId()))
                .orElse(false);
    }

    // meeting 테이블에서 company_id(조직 id)만 뽑아옴 — S3 키 조립용
    @Override
    public Optional<Long> findCompanyId(Long meetingId) {
        return springDataCapMeetingReferenceRepository.findById(meetingId).map(CapMeetingReferenceEntity::getCompanyId);
    }

    // meeting_attendee 복합키의 meetingId로 카운트 — CAP-13 SSE participant 이벤트의 totalCount
    @Override
    public int countAttendees(Long meetingId) {
        return springDataCapMeetingAttendeeReferenceRepository.countByIdMeetingId(meetingId);
    }

    // meeting 테이블에서 project_id만 뽑아옴 — access-guard의 프로젝트 멤버 판정용
    @Override
    public Optional<Long> findProjectId(Long meetingId) {
        return springDataCapMeetingReferenceRepository.findById(meetingId).map(CapMeetingReferenceEntity::getProjectId);
    }
}
