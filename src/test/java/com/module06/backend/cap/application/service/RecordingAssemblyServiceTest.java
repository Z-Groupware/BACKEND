package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.StartRecordingAssemblyCommand;
import com.module06.backend.cap.application.port.out.RecordingAssemblyPort;
import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CAP-05 녹음 종료/조립 서비스의 인가·seq 연속성 검증·조립 트리거 규칙을 검증하는 단위 테스트다.
 */
@DisplayName("CAP-05 녹음 종료/조립 서비스")
class RecordingAssemblyServiceTest {

    // 조립 포트가 호출됐는지 기록하는 플래그(테스트별로 새로 만든다).
    private final boolean[] assemblyTriggered = new boolean[1];

    /* 본문 lastSeq가 상한을 넘으면 인가 전에 CAP-011로 거절하는지 검증한다(DoS 방어). */
    @Test
    @DisplayName("범위 밖 lastSeq는 CAP-011로 거절한다")
    void rejectsOutOfRangeLastSeq() {
        RecordingAssemblyService service = service(true, true,
                Optional.of(state(0, 7L)), Map.of());

        assertErrorCode(() -> service.startRecordingAssembly(
                cmd(0, CaptureUploadState.MAX_SEQ + 1)), "CAP-011");
        assertThat(assemblyTriggered[0]).isFalse();
    }

    /* 회의가 없으면 CAP-002로 거절하는지 검증한다. */
    @Test
    @DisplayName("회의가 없으면 CAP-002로 거절한다")
    void rejectsWhenMeetingMissing() {
        RecordingAssemblyService service = service(false, false, Optional.empty(), Map.of());

        assertErrorCode(() -> service.startRecordingAssembly(cmd(0, 3)), "CAP-002");
    }

    /* 참석자가 아니면 CAP-010으로 거절하는지 검증한다. */
    @Test
    @DisplayName("참석자가 아니면 CAP-010으로 거절한다")
    void rejectsWhenNotAttendee() {
        RecordingAssemblyService service = service(true, false,
                Optional.of(state(0, 7L)), Map.of(0, List.of(1, 2, 3)));

        assertErrorCode(() -> service.startRecordingAssembly(cmd(0, 3)), "CAP-010");
    }

    /* 현재 녹음자가 아니면 CAP-004로 거절하는지 검증한다. */
    @Test
    @DisplayName("현재 녹음자가 아니면 CAP-004로 거절한다")
    void rejectsWhenNotRecorder() {
        RecordingAssemblyService service = service(true, true,
                Optional.of(state(0, 7L)), Map.of(0, List.of(1, 2, 3)));

        // 녹음자는 7번인데 9번이 종료 시도
        assertErrorCode(() -> service.startRecordingAssembly(new StartRecordingAssemblyCommand(500L, 9L, 0, 3)),
                "CAP-004");
    }

    /* 현재 세그먼트에 구멍이 있으면 CAP-012로 막고 조립을 트리거하지 않는지 검증한다. */
    @Test
    @DisplayName("중간 순번이 빠졌으면 CAP-012로 막고 조립을 트리거하지 않는다")
    void rejectsWhenSeqHasHole() {
        // lastSeq=4인데 3번이 빠짐
        RecordingAssemblyService service = service(true, true,
                Optional.of(state(0, 7L)), Map.of(0, List.of(1, 2, 4)));

        assertErrorCode(() -> service.startRecordingAssembly(cmd(0, 4)), "CAP-012");
        assertThat(assemblyTriggered[0]).isFalse();
    }

    /* 마지막 세그먼트가 lastSeq에 못 미치면(꼬리 유실) CAP-012로 막는지 검증한다. */
    @Test
    @DisplayName("마지막 순번까지 안 올라왔으면 CAP-012로 막는다")
    void rejectsWhenTailMissing() {
        // present는 1..3뿐인데 클라가 lastSeq=5라고 주장
        RecordingAssemblyService service = service(true, true,
                Optional.of(state(0, 7L)), Map.of(0, List.of(1, 2, 3)));

        assertErrorCode(() -> service.startRecordingAssembly(cmd(0, 5)), "CAP-012");
        assertThat(assemblyTriggered[0]).isFalse();
    }

    /* 단일 세그먼트가 연속이면 조립을 트리거하고 ASSEMBLING을 반환하는지 검증한다. */
    @Test
    @DisplayName("연속이면 조립을 트리거하고 ASSEMBLING을 반환한다")
    void triggersAssemblyWhenContiguous() {
        RecordingAssemblyService service = service(true, true,
                Optional.of(state(0, 7L)), Map.of(0, List.of(1, 2, 3)));

        StartRecordingAssemblyUseCase.Result result = service.startRecordingAssembly(cmd(0, 3));

        assertThat(result.status()).isEqualTo("ASSEMBLING");
        assertThat(result.missingSeqs()).isEmpty();
        assertThat(assemblyTriggered[0]).isTrue();
    }

    /* 여러 세그먼트가 각각 연속이면 통과하는지 검증한다(중간 세그먼트는 내부 연속성만). */
    @Test
    @DisplayName("여러 세그먼트가 각각 연속이면 조립을 트리거한다")
    void triggersAssemblyAcrossSegments() {
        // 세그먼트0: 1,2,3(이어받기로 넘어감) / 세그먼트1: 1,2 (마지막, lastSeq=2)
        RecordingAssemblyService service = service(true, true,
                Optional.of(state(1, 7L)), Map.of(0, List.of(1, 2, 3), 1, List.of(1, 2)));

        StartRecordingAssemblyUseCase.Result result = service.startRecordingAssembly(cmd(1, 2));

        assertThat(result.status()).isEqualTo("ASSEMBLING");
        assertThat(assemblyTriggered[0]).isTrue();
    }

    /* 중간 세그먼트에 내부 구멍이 있으면 CAP-012로 막는지 검증한다. */
    @Test
    @DisplayName("중간 세그먼트에 구멍이 있으면 CAP-012로 막는다")
    void rejectsWhenIntermediateSegmentHasHole() {
        // 세그먼트0: 1,3 (2 빠짐) / 세그먼트1: 1
        RecordingAssemblyService service = service(true, true,
                Optional.of(state(1, 7L)), Map.of(0, List.of(1, 3), 1, List.of(1)));

        assertErrorCode(() -> service.startRecordingAssembly(cmd(1, 1)), "CAP-012");
        assertThat(assemblyTriggered[0]).isFalse();
    }

    // meetingId 500, callerId 7 고정 명령.
    private StartRecordingAssemblyCommand cmd(int lastSegmentSeq, int lastSeq) {
        return new StartRecordingAssemblyCommand(500L, 7L, lastSegmentSeq, lastSeq);
    }

    // 세그먼트/녹음자 지정한 캡처 상태.
    private CaptureUploadState state(int segmentSeq, Long recorderId) {
        return CaptureUploadState.restore(500L, segmentSeq, recorderId, 0, 0, null, null);
    }

    // 지정한 회의 존재/참석 여부·상태·세그먼트별 순번으로 서비스를 조립한다. 조립 포트는 호출 여부를 기록한다.
    private RecordingAssemblyService service(boolean exists, boolean attendee,
                                            Optional<CaptureUploadState> state,
                                            Map<Integer, List<Integer>> seqsBySegment) {
        assemblyTriggered[0] = false;
        MeetingReferenceRepository meetingRef = new MeetingReferenceRepository() {
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
        };
        CaptureUploadStateRepository stateRepo = new CaptureUploadStateRepository() {
            @Override
            public Optional<CaptureUploadState> findByMeetingId(Long meetingId) {
                return state;
            }

            @Override
            public CaptureUploadState save(CaptureUploadState value) {
                return value;
            }
        };
        RecordingPartRepository partRepo = new RecordingPartRepository() {
            @Override
            public RecordingPart save(RecordingPart recordingPart) {
                return recordingPart;
            }

            @Override
            public List<Integer> findSeqsInSegment(Long meetingId, int segmentSeq) {
                return seqsBySegment.getOrDefault(segmentSeq, List.of());
            }
        };
        RecordingAssemblyPort assemblyPort = (meetingId, lastSegmentSeq, lastSeq) -> assemblyTriggered[0] = true;
        return new RecordingAssemblyService(meetingRef, stateRepo, partRepo, assemblyPort);
    }

    // 실행 결과가 예상 서비스 오류 코드인지 검증한다.
    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
