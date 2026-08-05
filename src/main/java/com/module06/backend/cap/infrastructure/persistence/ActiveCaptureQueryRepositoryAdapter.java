package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.ActiveCaptureQueryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/* comment.
    ActiveCaptureQueryRepository 계약을 파생 쿼리 3개의 조합으로 구현하는 어댑터.
    QUERY_002(신규 @Query/네이티브 SQL 금지) 때문에 세 테이블을 한 방 조인으로 못 쓰고, 파생 메서드로
    단계를 나눈다. 각 단계의 IN 집합은 "참석 중 + 진행 중 + 캡처 존재"로 빠르게 좁혀져 보통 0~1건이다.

      1) 토큰 사용자가 참석 중인 회의 후보(meeting_attendee)
      2) 그중 status=IN_PROGRESS인 회의만(meeting)
      3) 그중 캡처가 존재하는 가장 최근 상태 하나(capture_upload_state)
*/
@Repository
public class ActiveCaptureQueryRepositoryAdapter implements ActiveCaptureQueryRepository {

    // meeting.status ENUM 문자열. 회의 도메인 MeetingStatus enum에 의존하지 않으려고 read-model 안에서 상수로 둔다.
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final SpringDataCapMeetingAttendeeReferenceRepository attendeeRepository;
    private final SpringDataCapMeetingReferenceRepository meetingRepository;
    private final SpringDataCaptureUploadStateRepository captureUploadStateRepository;

    public ActiveCaptureQueryRepositoryAdapter(
            SpringDataCapMeetingAttendeeReferenceRepository attendeeRepository,
            SpringDataCapMeetingReferenceRepository meetingRepository,
            SpringDataCaptureUploadStateRepository captureUploadStateRepository) {
        this.attendeeRepository = attendeeRepository;
        this.meetingRepository = meetingRepository;
        this.captureUploadStateRepository = captureUploadStateRepository;
    }

    @Override
    public Optional<CaptureUploadState> findActiveCaptureForMember(Long memberId) {
        // 1) 참석 중인 회의 후보
        List<Long> attendedMeetingIds = attendeeRepository.findByIdMemberId(memberId).stream()
                .map(entity -> entity.getId().getMeetingId())
                .toList();
        if (attendedMeetingIds.isEmpty()) {
            return Optional.empty();
        }

        // 2) 그중 진행 중(IN_PROGRESS)인 회의만 (id 컬럼만 뽑는 프로젝션)
        List<Long> inProgressMeetingIds = meetingRepository.findByIdInAndStatus(attendedMeetingIds, STATUS_IN_PROGRESS)
                .stream()
                .map(SpringDataCapMeetingReferenceRepository.MeetingIdView::getId)
                .toList();
        if (inProgressMeetingIds.isEmpty()) {
            return Optional.empty();
        }

        // 3) 그중 캡처가 시작된(=capture_upload_state 행이 있는) 가장 최근 것 하나
        return captureUploadStateRepository.findFirstByMeetingIdInOrderByCreatedAtDesc(inProgressMeetingIds)
                .map(CaptureUploadStateJpaEntity::toDomain);
    }
}
