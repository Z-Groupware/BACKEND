package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.ReviewActionCreatePort;
import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.port.out.TranscriptRepository.NewUtterance;
import com.module06.backend.capture.domain.model.Utterance;
import com.module06.backend.capture.application.result.ReviewActionAdded;
import com.module06.backend.capture.application.usecase.AddReviewActionUseCase.AddReviewActionCommand;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RVW-03 · 액션 직접 추가.
 *
 * <p>검증의 축은 <b>무엇을 만들어 넘기는가</b>와 <b>무엇을 막는가</b>다. 만들어진 액션이 회의에
 * 매달리지 않으면(sourceMeetingId 누락) 방금 넣은 사람 눈에도 안 보이고, 담당자를 안 막으면
 * 아무의 보드에도 가지 않는 액션이 조용히 쌓인다.
 */
class AddReviewActionServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long PROJECT = 31L;
    private static final long ME = 99L;
    private static final long ATTENDEE = 42L;
    private static final LocalDate DUE = LocalDate.of(2026, 8, 8);

    @Test
    @DisplayName("추가한 액션은 회의에 매달린다 — 안 매달리면 검토 화면에서 안 보인다")
    void 추가한_액션은_회의와_프로젝트를_갖는다() {
        RecordingCreatePort actions = new RecordingCreatePort();

        ReviewActionAdded added = service(actions).add(command(ATTENDEE, DUE));

        assertThat(actions.created).hasSize(1);
        ReviewActionCreatePort.ManualAction manual = actions.created.get(0);
        assertThat(manual.meetingId()).isEqualTo(MEETING);
        assertThat(manual.projectId()).isEqualTo(PROJECT);
        assertThat(manual.companyId()).isEqualTo(COMPANY);
        assertThat(manual.assigneeMemberId()).isEqualTo(ATTENDEE);
        assertThat(manual.dueDate()).isEqualTo(DUE);

        /*
         * 화면이 이 두 값으로 「AI 확신도」 배지를 뗀다. 수동 추가 건은 검토 대상이 아니라
         * 이미 확정된 것이다 — 사람이 방금 직접 썼다.
         */
        assertThat(added.isManual()).isTrue();
        assertThat(added.reviewStatus()).isEqualTo("HUMAN_CONFIRMED");
    }

    @Test
    @DisplayName("담당자가 없으면 막는다 — 아무의 보드에도 가지 않는 액션이 쌓인다")
    void 담당자가_없으면_추가하지_않는다() {
        RecordingCreatePort actions = new RecordingCreatePort();

        assertThatThrownBy(() -> service(actions).add(command(null, DUE)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_MANUAL_FIELD_REQUIRED);
        assertThat(actions.created).isEmpty();
    }

    @Test
    @DisplayName("기한이 없으면 막는다 — 서버가 날짜를 지어내지 않는다")
    void 기한이_없으면_추가하지_않는다() {
        RecordingCreatePort actions = new RecordingCreatePort();

        /*
         * AI 경로는 프로젝트 마감일로 채우고 dueDateDefaulted 로 그 사실을 남긴다. 수동 추가에서
         * 같은 짓을 하면 사용자가 정한 기한인지 서버가 채운 값인지 화면에서 구분되지 않는다.
         */
        assertThatThrownBy(() -> service(actions).add(command(ATTENDEE, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_MANUAL_FIELD_REQUIRED);
        assertThat(actions.created).isEmpty();
    }

    @Test
    @DisplayName("참석자 명단 밖 담당자는 막는다 — RVW-02 와 같은 규칙이다")
    void 명단_밖_담당자는_추가하지_않는다() {
        RecordingCreatePort actions = new RecordingCreatePort();

        /*
         * 여기만 열어두면 명단 밖 담당자를 넣는 방법이 "직접 추가"로 남는다. 회의에 없던 사람의
         * 보드로 가는 액션은 어느 경로로 만들어졌든 같은 문제다.
         */
        assertThatThrownBy(() -> service(actions).add(command(777L, DUE)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_ASSIGNEE_NOT_IN_ROSTER);
        assertThat(actions.created).isEmpty();
    }

    @Test
    @DisplayName("명단 밖 탈출구는 담당자가 될 수 없다 — 사람이 아니라 자리다")
    void 탈출구는_담당자가_아니다() {
        RecordingCreatePort actions = new RecordingCreatePort();

        // personId=null 인 탈출구는 명단에 있지만 실제 사람이 아니다. null 담당자는 필수 검사가
        // 먼저 막는다 — 그 자리를 가리키는 방법 자체가 없어야 한다.
        assertThatThrownBy(() -> service(actions).add(command(null, DUE)))
                .isInstanceOf(BusinessException.class);
        assertThat(actions.created).isEmpty();
    }

    @Test
    @DisplayName("다른 회의의 근거 발화는 막는다 — 남의 회의 원문이 검토 화면에 인용된다")
    void 이_회의의_발화가_아니면_추가하지_않는다() {
        RecordingCreatePort actions = new RecordingCreatePort();

        /*
         * 검증 없이 저장하면 그 id 가 액션에 박히고, RVW-01 이 그 id 로 원문을 조인해 보여준다 —
         * 다른 회사 회의의 발화 내용이 우리 화면에 인용된다. 화면에 뿌려지는 순간이 유출이다.
         */
        AddReviewActionService service = new AddReviewActionService(
                actions, transcripts(false), guard(),
                meetingId -> Optional.of(PROJECT), roster());

        assertThatThrownBy(() -> service.add(commandWithEvidence(9_999L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_EVIDENCE_NOT_IN_MEETING);
        assertThat(actions.created).isEmpty();
    }

    @Test
    @DisplayName("이 회의의 발화면 근거로 붙는다")
    void 이_회의의_발화는_근거로_붙는다() {
        RecordingCreatePort actions = new RecordingCreatePort();

        new AddReviewActionService(actions, transcripts(true), guard(),
                meetingId -> Optional.of(PROJECT), roster())
                .add(commandWithEvidence(8_812L));

        assertThat(actions.created.get(0).evidenceTranscriptId()).isEqualTo(8_812L);
    }

    @Test
    @DisplayName("근거를 안 넣는 것은 정상이다 — 회의록에 없는 할 일도 추가할 수 있어야 한다")
    void 근거가_없어도_추가한다() {
        RecordingCreatePort actions = new RecordingCreatePort();

        // "없으면 거절"이 아니라 "있으면 이 회의 것이어야 한다"로 막는다(명세의 요청 예시도 null 이다).
        new AddReviewActionService(actions, transcripts(false), guard(),
                meetingId -> Optional.of(PROJECT), roster())
                .add(command(ATTENDEE, DUE));

        assertThat(actions.created).hasSize(1);
        assertThat(actions.created.get(0).evidenceTranscriptId()).isNull();
    }

    @Test
    @DisplayName("회의의 프로젝트를 못 읽으면 만들지 않는다 — 엉뚱한 보드에 꽂힌다")
    void 프로젝트를_못_읽으면_추가하지_않는다() {
        RecordingCreatePort actions = new RecordingCreatePort();

        AddReviewActionService service = new AddReviewActionService(
                actions, transcripts(true), guard(), meetingId -> Optional.empty(), roster());

        assertThatThrownBy(() -> service.add(command(ATTENDEE, DUE)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(actions.created).isEmpty();
    }

    private AddReviewActionService service(RecordingCreatePort actions) {
        return new AddReviewActionService(
                actions, transcripts(true), guard(), meetingId -> Optional.of(PROJECT), roster());
    }

    /* 근거 발화 존재 여부만 답한다 — 이 서비스가 읽는 것이 그것뿐이다. */
    private TranscriptRepository transcripts(boolean inMeeting) {
        return new TranscriptRepository() {

            @Override
            public boolean existsInMeeting(long meetingId, long transcriptId) {
                return inMeeting;
            }

            @Override
            public List<Utterance> findByMeetingOrderByOffset(long meetingId) {
                throw new UnsupportedOperationException();
            }

            /* STT 적재 경로다 — 이 서비스(RVW-03)는 정본을 쓰지 않는다. */
            @Override
            public int replaceBlockTranscript(long meetingId, int sttBlockSeq,
                                              List<NewUtterance> utterances) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int applySpeakerAttributions(long meetingId,
                                                List<SpeakerAttributionResolver.Attribution> attributions) {
                throw new UnsupportedOperationException();
            }

            /* ANLZ-05 조회 경로다 — 이 서비스는 지나지 않는다. */
            @Override
            public List<UtteranceView> findPage(long meetingId,
                                                com.module06.backend.capture.domain.model.TranscriptCursor cursor,
                                                int limit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<UtteranceView> findByMeetingAndIds(long meetingId, List<Long> transcriptIds) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private AddReviewActionCommand commandWithEvidence(Long evidenceTranscriptId) {
        return new AddReviewActionCommand(
                COMPANY, MEETING, ATTENDEE, "결제 실패 케이스 정리", null, DUE,
                evidenceTranscriptId, ME);
    }

    /* 회사 스코프 관문은 여기서 볼 대상이 아니다(MeetingAccessGuardTest 가 본다). */
    private MeetingAccessGuard guard() {
        return new MeetingAccessGuard((companyId, meetingId) -> true);
    }

    private MeetingParticipantProvider roster() {
        return meetingId -> List.of(
                new AiLayerPort.Participant(ATTENDEE, "김서준"),
                new AiLayerPort.Participant(null, "명단 외"));
    }

    private AddReviewActionCommand command(Long assignee, LocalDate dueDate) {
        return new AddReviewActionCommand(
                COMPANY, MEETING, assignee, "결제 실패 케이스 정리", null, dueDate, null, ME);
    }

    /* 무엇을 실어 넘겼는지가 검증 대상이다 — 특히 회의·프로젝트다. */
    private static final class RecordingCreatePort implements ReviewActionCreatePort {

        private final List<ManualAction> created = new ArrayList<>();

        @Override
        public long createManual(ManualAction action) {
            created.add(action);
            return 8_901L;
        }
    }
}
