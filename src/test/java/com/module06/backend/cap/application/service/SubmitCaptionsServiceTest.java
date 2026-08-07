package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.SubmitCaptionsCommand;
import com.module06.backend.cap.application.port.out.CaptionBroadcastPort;
import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.CaptionChunkRepository;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CAP-11 자막 청크 배치 전송 서비스의 회의 존재·참석자 검증, rms 필수(422), 저장·브로드캐스트 규칙을 검증한다.
 */
@DisplayName("CAP-11 자막 청크 배치 전송 서비스")
class SubmitCaptionsServiceTest {

    private static final Long MEETING_ID = 500L;
    private static final Long MEMBER_ID = 7L;

    // 저장·브로드캐스트 호출 기록(테스트별 새로).
    private final List<CaptionChunk> savedChunks = new ArrayList<>();
    private final List<CaptionChunk> broadcastChunks = new ArrayList<>();

    /* 회의가 없으면 CAP-002로 거절하는지 검증한다. */
    @Test
    @DisplayName("회의가 없으면 CAP-002로 거절한다")
    void rejectsWhenMeetingMissing() {
        SubmitCaptionsService service = service(false, true);

        assertErrorCode(() -> service.submitCaptions(command(chunk(1, "-12.4"))), "CAP-002");
        assertThat(savedChunks).isEmpty();
    }

    /* 참석자가 아니면 CAP-010으로 거절하는지 검증한다. */
    @Test
    @DisplayName("참석자가 아니면 CAP-010으로 거절한다")
    void rejectsWhenNotAttendee() {
        SubmitCaptionsService service = service(true, false);

        assertErrorCode(() -> service.submitCaptions(command(chunk(1, "-12.4"))), "CAP-010");
        assertThat(savedChunks).isEmpty();
    }

    /* rms가 없는 조각이 하나라도 있으면 배치 전체를 CAP-018(422)로 거절하고 아무것도 저장하지 않는지 검증한다. */
    @Test
    @DisplayName("rms 누락 조각이 있으면 배치 전체를 CAP-018로 거절한다")
    void rejectsWholeBatchWhenRmsMissing() {
        SubmitCaptionsService service = service(true, true);

        assertErrorCode(() -> service.submitCaptions(
                command(chunk(1, "-12.4"), chunk(2, null))), "CAP-018");
        assertThat(savedChunks).isEmpty();
        assertThat(broadcastChunks).isEmpty();
    }

    /* seq가 음수인 조각이 있으면 배치 전체를 CAP-011로 거절하는지 검증한다. */
    @Test
    @DisplayName("seq가 음수인 조각이 있으면 배치 전체를 CAP-011로 거절한다")
    void rejectsWholeBatchWhenSeqNegative() {
        SubmitCaptionsService service = service(true, true);

        assertErrorCode(() -> service.submitCaptions(
                command(chunk(1, "-12.4"), chunk(-1, "-8.1"))), "CAP-011");
        assertThat(savedChunks).isEmpty();
        assertThat(broadcastChunks).isEmpty();
    }

    /* 정상 배치는 전부 저장되고 새로 저장된 조각만 브로드캐스트되는지 검증한다. */
    @Test
    @DisplayName("정상 배치는 저장 후 새로 저장된 조각만 브로드캐스트한다")
    void savesAndBroadcastsNewChunks() {
        SubmitCaptionsService service = service(true, true);

        service.submitCaptions(command(chunk(41, "-12.4"), chunk(42, "-8.1")));

        assertThat(savedChunks).hasSize(2);
        assertThat(broadcastChunks).hasSize(2);
    }

    private SubmitCaptionsCommand.ChunkInput chunk(int seq, String rms) {
        return new SubmitCaptionsCommand.ChunkInput(seq, seq * 1000, seq * 1000 + 500, "text-" + seq,
                rms == null ? null : new BigDecimal(rms));
    }

    private SubmitCaptionsCommand command(SubmitCaptionsCommand.ChunkInput... chunks) {
        return new SubmitCaptionsCommand(MEETING_ID, MEMBER_ID, List.of(chunks));
    }

    // 회의 존재·참석자 여부를 지정해 서비스를 조립한다. 저장·브로드캐스트 호출을 기록한다(재전송 스킵 없음 — 매번 신규 저장).
    private SubmitCaptionsService service(boolean meetingExists, boolean attendee) {
        savedChunks.clear();
        broadcastChunks.clear();

        MeetingReferenceRepository meetingRef = new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return meetingExists;
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
        CaptionChunkRepository captionChunkRepository = new CaptionChunkRepository() {
            @Override
            public List<CaptionChunk> saveAllSkippingDuplicates(List<CaptionChunk> chunks) {
                savedChunks.addAll(chunks);
                return chunks;
            }

            @Override
            public List<CaptionChunk> findByMeetingId(Long meetingId) {
                return List.of();
            }
        };
        CaptionBroadcastPort broadcastPort = (meetingId, newChunks) -> broadcastChunks.addAll(newChunks);

        return new SubmitCaptionsService(meetingRef, captionChunkRepository, broadcastPort);
    }

    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
