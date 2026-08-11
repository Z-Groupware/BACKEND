package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.usecase.GetPartUploadStatusUseCase;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.ProjectTeamReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CAP-08 청크 업로드 상태 조회 서비스의 회의 존재·녹음자 검증과 missingSeqs/resumeFromSeq/gapMs 계산 규칙을
 * 검증하는 단위 테스트다.
 */
@DisplayName("CAP-08 청크 업로드 상태 조회 서비스")
class CapturePartStatusServiceTest {

    /* 회의가 없으면 녹음자 검증 전에 CAP-002로 끝나는지 검증한다. */
    @Test
    @DisplayName("회의가 없으면 CAP-002로 거절한다")
    void rejectsWhenMeetingMissing() {
        CapturePartStatusService service = new CapturePartStatusService(
                meetingRef(false, false), accessGuard(meetingRef(false, false)), stateRepo(Optional.empty()), recordingParts(List.of()));

        assertErrorCode(() -> service.getPartUploadStatus(500L, 7L), "CAP-002");
    }

    /* 상태행이 없으면(=presign 전) 녹음자가 없으므로 CAP-004로 거절하는지 검증한다. */
    @Test
    @DisplayName("상태행이 없으면 CAP-004로 거절한다")
    void rejectsWhenNoState() {
        CapturePartStatusService service = new CapturePartStatusService(
                meetingRef(true, true), accessGuard(meetingRef(true, true)), stateRepo(Optional.empty()), recordingParts(List.of()));

        assertErrorCode(() -> service.getPartUploadStatus(500L, 7L), "CAP-004");
    }

    /* 참석자가 아니면 상태행을 읽기 전에 CAP-010으로 거절하는지 검증한다(IDOR 갭 보완). */
    @Test
    @DisplayName("참석자가 아니면 CAP-010으로 거절한다")
    void rejectsWhenNotAttendee() {
        CaptureUploadState state = CaptureUploadState.restore(500L, 0, 7L, 3, 0, 0L, null, null);
        CapturePartStatusService service = new CapturePartStatusService(
                meetingRef(true, false), accessGuard(meetingRef(true, false)), stateRepo(Optional.of(state)), recordingParts(List.of(1, 2, 3)));

        // 회의도 있고 상태행도 있지만, caller가 참석자 명단에 없으면 녹음자 검증 전에 막혀야 한다.
        assertErrorCode(() -> service.getPartUploadStatus(500L, 7L), "CAP-010");
    }

    /* 현재 녹음자가 아니면 CAP-004로 거절하는지 검증한다. */
    @Test
    @DisplayName("현재 녹음자가 아니면 CAP-004로 거절한다")
    void rejectsWhenNotRecorder() {
        CaptureUploadState state = CaptureUploadState.restore(500L, 0, 7L, 3, 0, 0L, null, null);
        CapturePartStatusService service = new CapturePartStatusService(
                meetingRef(true, true), accessGuard(meetingRef(true, true)), stateRepo(Optional.of(state)), recordingParts(List.of(1, 2, 3)));

        // 녹음자는 7번인데 9번이 조회 시도
        assertErrorCode(() -> service.getPartUploadStatus(500L, 9L), "CAP-004");
    }

    /* 정상: 현재 세그먼트의 빠진 순번·재개 순번·gapMs가 규칙대로 계산되는지 검증한다. */
    @Test
    @DisplayName("빠진 순번·resumeFromSeq·gapMs를 규칙대로 계산한다")
    void computesMissingResumeAndGap() {
        // 세그먼트 2, lastSeq 5, blocksFormed 3, 녹음자 7. 업로드된 순번은 1·2·4 → 3·5가 빠짐.
        CaptureUploadState state = CaptureUploadState.restore(500L, 2, 7L, 5, 3, 0L, null, null);
        CapturePartStatusService service = new CapturePartStatusService(
                meetingRef(true, true), accessGuard(meetingRef(true, true)), stateRepo(Optional.of(state)), recordingParts(List.of(1, 2, 4)));

        GetPartUploadStatusUseCase.Result result = service.getPartUploadStatus(500L, 7L);

        assertThat(result.segmentSeq()).isEqualTo(2);
        assertThat(result.lastSeq()).isEqualTo(5);
        assertThat(result.missingSeqs()).containsExactly(3, 5);
        assertThat(result.blocksFormed()).isEqualTo(3);
        assertThat(result.resumeFromSeq()).isEqualTo(6);
        assertThat(result.gapMs()).isZero();
    }

    /* 모든 순번이 올라와 있으면 missingSeqs가 비고 resumeFromSeq는 lastSeq+1인지 검증한다. */
    @Test
    @DisplayName("빠진 순번이 없으면 missingSeqs는 비어있다")
    void noMissingWhenAllPresent() {
        CaptureUploadState state = CaptureUploadState.restore(500L, 0, 7L, 3, 0, 0L, null, null);
        CapturePartStatusService service = new CapturePartStatusService(
                meetingRef(true, true), accessGuard(meetingRef(true, true)), stateRepo(Optional.of(state)), recordingParts(List.of(1, 2, 3)));

        GetPartUploadStatusUseCase.Result result = service.getPartUploadStatus(500L, 7L);

        assertThat(result.missingSeqs()).isEmpty();
        assertThat(result.resumeFromSeq()).isEqualTo(4);
    }

    /* 아직 아무것도 안 올라왔으면(lastSeq=0) missingSeqs 비고 resumeFromSeq=1인지 검증한다. */
    @Test
    @DisplayName("아무것도 안 올라왔으면 resumeFromSeq는 1이다")
    void resumeFromOneWhenEmpty() {
        CaptureUploadState state = CaptureUploadState.restore(500L, 0, 7L, 0, 0, 0L, null, null);
        CapturePartStatusService service = new CapturePartStatusService(
                meetingRef(true, true), accessGuard(meetingRef(true, true)), stateRepo(Optional.of(state)), recordingParts(List.of()));

        GetPartUploadStatusUseCase.Result result = service.getPartUploadStatus(500L, 7L);

        assertThat(result.missingSeqs()).isEmpty();
        assertThat(result.resumeFromSeq()).isEqualTo(1);
        assertThat(result.lastSeq()).isZero();
    }

    // 회의 존재 여부만 고정 반환하는 회의 참조 저장소 대역.
    private MeetingReferenceRepository meetingRef(boolean exists, boolean attendee) {
        return new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return exists;
            }

            @Override
            public boolean isAttendee(Long meetingId, Long memberId) {
                return attendee;
            }

            @Override
            public boolean isHost(Long meetingId, Long memberId) {
                return false;
            }

            @Override
            public Optional<Long> findCompanyId(Long meetingId) {
                return Optional.of(1L);
            }

            @Override
            public int countAttendees(Long meetingId) {
                return 0;
            }

            @Override
            public Optional<Long> findProjectId(Long meetingId) {
                return Optional.empty();
            }
        };
    }

    // 주어진 회의 참조 대역으로 가드를 조립한다(프로젝트 멤버 판정은 이 서비스와 무관해 항상 false).
    private CapMeetingAccessGuard accessGuard(MeetingReferenceRepository meetingRef) {
        ProjectTeamReferenceRepository projectTeamRef = (projectId, teamId) -> false;
        return new CapMeetingAccessGuard(meetingRef, projectTeamRef);
    }

    // 지정한 캡처 상태를 반환하는 상태 저장소 대역(save는 조회 경로에서 쓰지 않음).
    private CaptureUploadStateRepository stateRepo(Optional<CaptureUploadState> state) {
        return new CaptureUploadStateRepository() {
            @Override
            public Optional<CaptureUploadState> findByMeetingId(Long meetingId) {
                return state;
            }

            @Override
            public CaptureUploadState save(CaptureUploadState value) {
                return value;
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public Optional<Integer> tryReserveNextBlockSeq(Long meetingId, int expectedBlocksFormed) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
    }

    // 현재 세그먼트에 존재하는 순번을 고정 반환하는 청크 저장소 대역(save는 조회 경로에서 쓰지 않음).
    private RecordingPartRepository recordingParts(List<Integer> presentSeqs) {
        return new RecordingPartRepository() {
            @Override
            public RecordingPart save(RecordingPart recordingPart) {
                return recordingPart;
            }

            @Override
            public List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq) {
                return presentSeqs;
            }

            @Override
            public void deleteByMeetingId(Long meetingId) {
            }

            @Override
            public List<RecordingPart> findInSegmentBetweenSeqs(Long meetingId, int segmentSeq, int fromSeq,
                                                                 int toSeq) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }
        };
    }

    // 실행 결과가 예상 서비스 오류 코드인지 검증한다.
    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
