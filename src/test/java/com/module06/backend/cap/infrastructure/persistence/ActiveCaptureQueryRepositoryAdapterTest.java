package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.ActiveCaptureQueryRepository;
import com.module06.backend.meeting.domain.model.Meeting;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingAttendeeJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.entity.MeetingJpaEntity;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingAttendeeRepository;
import com.module06.backend.meeting.infrastructure.persistence.repository.SpringDataMeetingRepository;

/*
 * CAP-09 진행 중 캡처 조회 어댑터가 "참석자 + IN_PROGRESS 회의 + 캡처 존재" 3조건을 파생 쿼리
 * 조합으로 정확히 좁히는지 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("CAP-09 진행 중 캡처 조회 영속성 어댑터")
class ActiveCaptureQueryRepositoryAdapterTest {

    /* 애플리케이션 계층이 사용하는 실제 진행 중 캡처 조회 계약이다. */
    @Autowired
    private ActiveCaptureQueryRepository activeCaptureQueryRepository;

    /* 회의 행을 저장하고 초기화하는 기술 저장소다(회의 도메인 소유 write 모델). */
    @Autowired
    private SpringDataMeetingRepository springDataMeetingRepository;

    /* 참석자 행을 저장하고 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataMeetingAttendeeRepository springDataMeetingAttendeeRepository;

    /* 캡처 상태 행을 저장하고 초기화하는 기술 저장소다(CAP 소유). */
    @Autowired
    private SpringDataCaptureUploadStateRepository springDataCaptureUploadStateRepository;

    /* 다른 통합 테스트가 커밋했을 수 있는 데이터를 자식부터 초기화한다. */
    @BeforeEach
    void clear() {
        springDataCaptureUploadStateRepository.deleteAll();
        springDataMeetingAttendeeRepository.deleteAll();
        springDataMeetingRepository.deleteAll();
    }

    /* 참석 중 + IN_PROGRESS 회의 + 캡처가 있으면 캡처 상태가 반환되는지 검증한다. */
    @Test
    @DisplayName("참석 중인 진행 회의의 캡처 상태를 반환한다")
    void returnsActiveCaptureForAttendee() {
        /* 진행 중 회의를 만들고 7번을 참석자로, 세그먼트 2·마지막 청크 42인 캡처를 저장한다. */
        Long meetingId = saveMeeting(MeetingStatus.IN_PROGRESS);
        saveAttendee(meetingId, 7L);
        saveCapture(meetingId, 2, 1L, 42);

        /* 참석자 7번 기준으로 진행 중 캡처를 조회한다. */
        Optional<CaptureUploadState> result = activeCaptureQueryRepository.findActiveCaptureForMember(7L);

        /* 캡처의 회의·세그먼트·마지막 청크·녹음자 값이 그대로 반환돼야 한다. */
        assertThat(result).isPresent().get().satisfies(state -> {
            assertThat(state.getMeetingId()).isEqualTo(meetingId);
            assertThat(state.getSegmentSeq()).isEqualTo(2);
            assertThat(state.getLastSeq()).isEqualTo(42);
            assertThat(state.getRecorderPersonId()).isEqualTo(1L);
        });
    }

    /* 참석자가 아니면 같은 진행 캡처가 있어도 조회되지 않는지 검증한다. */
    @Test
    @DisplayName("비참석자에게는 진행 중 캡처가 조회되지 않는다")
    void hidesCaptureFromNonAttendee() {
        Long meetingId = saveMeeting(MeetingStatus.IN_PROGRESS);
        saveAttendee(meetingId, 7L);
        saveCapture(meetingId, 0, 1L, 10);

        /* 참석자가 아닌 999번은 빈 결과를 받아야 한다. */
        assertThat(activeCaptureQueryRepository.findActiveCaptureForMember(999L)).isEmpty();
    }

    /* 회의가 진행 중(IN_PROGRESS)이 아니면 캡처가 있어도 조회되지 않는지 검증한다. */
    @Test
    @DisplayName("진행 중이 아닌 회의의 캡처는 조회되지 않는다")
    void hidesCaptureWhenMeetingNotInProgress() {
        /* 예약(SCHEDULED) 상태 회의에 참석자와 캡처가 있어도 진행 중이 아니다. */
        Long meetingId = saveMeeting(MeetingStatus.SCHEDULED);
        saveAttendee(meetingId, 7L);
        saveCapture(meetingId, 0, 1L, 10);

        assertThat(activeCaptureQueryRepository.findActiveCaptureForMember(7L)).isEmpty();
    }

    /* 진행 회의에 참석 중이어도 캡처가 시작 전이면 조회되지 않는지 검증한다. */
    @Test
    @DisplayName("캡처가 시작되지 않은 진행 회의는 조회되지 않는다")
    void hidesWhenNoCaptureYet() {
        Long meetingId = saveMeeting(MeetingStatus.IN_PROGRESS);
        saveAttendee(meetingId, 7L);
        /* capture_upload_state를 저장하지 않는다 — 아직 presign 전. */

        assertThat(activeCaptureQueryRepository.findActiveCaptureForMember(7L)).isEmpty();
    }

    /* 주어진 상태의 회의를 저장하고 생성된 회의 ID를 반환한다. */
    private Long saveMeeting(MeetingStatus status) {
        /* 진행 중 상태를 지정하려고 create(SCHEDULED 고정) 대신 reconstitute로 상태를 명시한다. */
        Meeting meeting = Meeting.reconstitute(
                null, 10L, 12L, null, 2L, 1L, "굿즈 앱 주간 운영 점검", status,
                LocalDateTime.of(2026, 8, 6, 14, 0), LocalDateTime.of(2026, 8, 6, 15, 0),
                false, null, List.of(1L), null, null, null, null);
        return springDataMeetingRepository.save(MeetingJpaEntity.from(meeting)).getId();
    }

    /* 회의에 참석자 한 명을 저장한다. */
    private void saveAttendee(Long meetingId, Long memberId) {
        springDataMeetingAttendeeRepository.save(new MeetingAttendeeJpaEntity(meetingId, memberId));
    }

    /* 회의의 캡처 상태 한 행을 저장한다(녹음자·세그먼트·마지막 청크 지정). */
    private void saveCapture(Long meetingId, int segmentSeq, Long recorderId, int lastSeq) {
        CaptureUploadState state = CaptureUploadState.restore(meetingId, segmentSeq, recorderId, lastSeq, 0, 0L, 0L, null, null);
        springDataCaptureUploadStateRepository.save(CaptureUploadStateJpaEntity.fromDomain(state));
    }
}
