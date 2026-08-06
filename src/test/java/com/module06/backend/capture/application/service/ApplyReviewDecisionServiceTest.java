package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.module06.backend.capture.application.port.out.ActionReviewApplyPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.ReviewLogRepository;
import com.module06.backend.capture.application.result.ReviewDecisionOutcome;
import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase.ReviewDecisionCommand;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.RejectReason;
import com.module06.backend.capture.domain.model.ReviewDecision;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RVW-02 · 액션 항목 수정·반려.
 *
 * <p><b>라벨이 이 API 의 본체다.</b> 화면에서는 "담당자 바꾸고 저장"이지만, 이 저장소가 그
 * 순간에 얻는 것은 {AI 입력 → 사람이 인정한 정답} 한 쌍이다. 그게 특화 모델의 유일한 연료이고
 * 지나간 회의는 다시 만들 수 없다 — 그래서 여기 검증의 절반이 "무엇이 라벨에 남는가"다.
 */
class ApplyReviewDecisionServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long ACTION = 8821L;
    private static final long ME = 12L;
    private static final long ALICE = 42L;
    private static final long BOB = 43L;

    @Test
    @DisplayName("CONFIRM 도 라벨로 남는다 — 맞힌 것을 안 남기면 라벨셋에 오답만 쌓인다")
    void 무수정_승인도_라벨로_남는다() {
        RecordingReviewLog logs = new RecordingReviewLog();
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        ReviewDecisionOutcome outcome = service(target(), logs, vectors)
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        assertThat(outcome.reviewStatus()).isEqualTo("HUMAN_CONFIRMED");
        assertThat(outcome.labelLogged()).isTrue();
        assertThat(logs.entries).hasSize(1);
        assertThat(logs.entries.get(0).decision()).isEqualTo(ReviewDecision.CONFIRM);
        // 사유 없는 CONFIRM 은 액션을 만든 계층(L4)의 정답 라벨이다. layer 가 없으면
        // few-shot 조회(AI-09)가 이 행을 못 찾는다.
        assertThat(logs.entries.get(0).layer()).isEqualTo(LayerName.L4);
        // human_value 는 null 이다 — llm_output 과 같다는 뜻이고, 같은 값을 두 번 적으면
        // "고쳤는데 우연히 같았다"와 구분되지 않는다.
        assertThat(logs.entries.get(0).humanValue()).isNull();
    }

    @Test
    @DisplayName("CONFIRM 은 few-shot 예시로 예약된다 — AI 가 맞힌 사례가 가장 좋은 예시다")
    void 무수정_승인은_벡터로_예약된다() {
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        ReviewDecisionOutcome outcome = service(target(), new RecordingReviewLog(), vectors)
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        assertThat(outcome.vectorQueued()).isTrue();
        assertThat(vectors.entries).hasSize(1);
        // 임베딩 대상은 근거 발화 원문이다. tuple 을 임베딩하면 검색 쿼리(새 발화)와 키가
        // 다른 공간에 놓여 유사도가 망가진다(V5.10).
        assertThat(vectors.entries.get(0).inputText()).isEqualTo("서준님이 정리해주세요.");
        assertThat(vectors.entries.get(0).layer()).isEqualTo(LayerName.L4);
    }

    @Test
    @DisplayName("담당자를 고치면 action 에 반영되고 사유가 계층을 정한다")
    void 담당자_수정이_반영되고_계층이_기록된다() {
        RecordingApplyPort applied = new RecordingApplyPort();
        RecordingReviewLog logs = new RecordingReviewLog();

        service(target(), applied, logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, RejectReason.WRONG_ASSIGNEE, BOB, null));

        assertThat(applied.assigneeMemberId).isEqualTo(BOB);
        assertThat(applied.reviewStatus).isEqualTo("HUMAN_CONFIRMED");
        // WRONG_ASSIGNEE 는 지시어 해소(L1.5)가 사람을 잘못 짚은 것이다.
        assertThat(logs.entries.get(0).layer()).isEqualTo(LayerName.L1_5);
        // 고친 값이 라벨의 정답이다.
        assertThat(logs.entries.get(0).humanValue()).contains("\"assigneeMemberId\":43");
    }

    @Test
    @DisplayName("고치지 않은 칸도 human_value 에 담는다 — 한 행만 보고 정답을 복원할 수 있어야 한다")
    void 고치지_않은_칸은_현재_값으로_채운다() {
        RecordingReviewLog logs = new RecordingReviewLog();

        // 기한만 고쳤다. 담당자는 그대로다.
        service(target(), logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, RejectReason.WRONG_DUE, null,
                        LocalDate.of(2026, 8, 20)));

        String humanValue = logs.entries.get(0).humanValue();
        assertThat(humanValue).contains("\"dueDate\":\"2026-08-20\"")
                // 안 고친 담당자도 담긴다 — 바뀐 칸만 담으면 다른 표를 다시 읽어야 정답이 된다.
                .contains("\"assigneeMemberId\":42");
    }

    @Test
    @DisplayName("반려는 값을 고치지 않고 상태만 바꾼다 — llm_output 과 action 이 갈리면 안 된다")
    void 반려는_값을_고치지_않는다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        ReviewDecisionOutcome outcome = service(target(), applied, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.REJECT, RejectReason.DUPLICATE, BOB,
                        LocalDate.of(2026, 8, 20)));

        assertThat(outcome.reviewStatus()).isEqualTo("REJECTED");
        // 담당자·기한을 함께 보내와도 무시한다.
        assertThat(applied.assigneeMemberId).isNull();
        assertThat(applied.dueDate).isNull();
    }

    @Test
    @DisplayName("반려는 few-shot 예시로 예약하지 않는다 — '이건 아니다'는 정답 tuple 이 아니다")
    void 반려는_벡터로_예약하지_않는다() {
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        ReviewDecisionOutcome outcome = service(target(), new RecordingReviewLog(), vectors)
                .apply(command(ReviewDecision.REJECT, RejectReason.HALLUCINATION, null, null));

        // 라벨은 남는다(gold set 으로는 유효하다). 예시로만 쓰지 않는다.
        assertThat(outcome.labelLogged()).isTrue();
        assertThat(outcome.vectorQueued()).isFalse();
        assertThat(vectors.entries).isEmpty();
    }

    @Test
    @DisplayName("수정·반려에 사유가 없으면 422 — 사유 없는 라벨은 어느 계층을 고칠지 못 가리킨다")
    void 사유가_없는_수정은_거절한다() {
        assertThatThrownBy(() -> service(target(), new RecordingReviewLog(), new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, null, BOB, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("CONFIRM 에 사유가 붙으면 422 — '맞혔는데 틀렸다'는 모순이다")
    void 사유가_붙은_승인은_거절한다() {
        assertThatThrownBy(() -> service(target(), new RecordingReviewLog(), new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, RejectReason.DUPLICATE, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("참석자 명단 밖 담당자는 422 — 틀린 배정을 정답 라벨로 학습시키면 안 된다")
    void 명단_밖_담당자는_거절한다() {
        RecordingApplyPort applied = new RecordingApplyPort();

        assertThatThrownBy(() -> service(target(), applied, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.MODIFY, RejectReason.WRONG_ASSIGNEE, 999L, null)))
                .isInstanceOf(BusinessException.class);

        // action 을 건드리기 전에 막는다.
        assertThat(applied.called).isFalse();
    }

    @Test
    @DisplayName("그 회의의 액션이 아니면 404 — 회의 관문은 actionId 를 보지 않는다")
    void 없는_액션은_404() {
        assertThatThrownBy(() -> new ApplyReviewDecisionService(
                new FakeQueryPort(null), new RecordingApplyPort(), new RecordingReviewLog(),
                new RecordingVectorRepository(), accessibleGuard(), roster(), new ObjectMapper())
                .apply(command(ReviewDecision.CONFIRM, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("다른 회사 회의는 관문에서 막는다 — 조회조차 하지 않는다")
    void 다른_회사_회의는_막는다() {
        FakeQueryPort port = new FakeQueryPort(target());

        assertThatThrownBy(() -> new ApplyReviewDecisionService(
                port, new RecordingApplyPort(), new RecordingReviewLog(),
                new RecordingVectorRepository(),
                new MeetingAccessGuard((companyId, meetingId) -> false), roster(), new ObjectMapper())
                .apply(command(ReviewDecision.CONFIRM, null, null, null)))
                .isInstanceOf(BusinessException.class);

        assertThat(port.called).isFalse();
    }

    @Test
    @DisplayName("수동 추가 액션은 예시로 예약하지 않는다 — AI 입력이 없어 {입력→정답} 쌍이 없다")
    void 수동_추가_액션은_벡터로_예약하지_않는다() {
        ActionReviewQueryPort.ReviewTarget manual = new ActionReviewQueryPort.ReviewTarget(
                ACTION, ALICE, LocalDate.of(2026, 8, 8), "직접 추가한 일", true, "PENDING",
                8812L, "서준님이 정리해주세요.", null, null);
        RecordingReviewLog logs = new RecordingReviewLog();
        RecordingVectorRepository vectors = new RecordingVectorRepository();

        ReviewDecisionOutcome outcome = service(manual, logs, vectors)
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        assertThat(outcome.vectorQueued()).isFalse();
        // 라벨은 남고 is_manual 로 표시된다 — gold set 라벨로는 유효하다.
        assertThat(logs.entries.get(0).manual()).isTrue();
        // AI 원본이 없으므로 llm_output 은 빈 객체다. null 을 넣을 수 없는 컬럼이고,
        // is_manual 이 그 빈 값의 뜻을 함께 말해준다.
        assertThat(logs.entries.get(0).llmOutput()).isEqualTo("{}");
        assertThat(logs.entries.get(0).modelName()).isNull();
    }

    @Test
    @DisplayName("근거 발화가 없으면 예시로 예약하지 않는다 — 임베딩할 텍스트가 없다")
    void 근거가_없으면_벡터로_예약하지_않는다() {
        ActionReviewQueryPort.ReviewTarget noEvidence = new ActionReviewQueryPort.ReviewTarget(
                ACTION, ALICE, LocalDate.of(2026, 8, 8), "로드맵 초안 작성", false, "PENDING",
                null, null, "제품 로드맵", aiValue());

        ReviewDecisionOutcome outcome = service(noEvidence, new RecordingReviewLog(),
                new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        assertThat(outcome.labelLogged()).isTrue();
        assertThat(outcome.vectorQueued()).isFalse();
    }

    @Test
    @DisplayName("input_context 에 참석자 명단과 근거 발화를 담는다 — 재현할 수 없는 값이다")
    void 재현할_수_없는_값을_라벨에_담는다() {
        RecordingReviewLog logs = new RecordingReviewLog();

        service(target(), logs, new RecordingVectorRepository())
                .apply(command(ReviewDecision.CONFIRM, null, null, null));

        String context = logs.entries.get(0).inputContext();
        assertThat(context).contains("\"roster\"")
                .contains("김서준")
                .contains("서준님이 정리해주세요.")
                // few-shot 을 쓴 적이 없다는 사실도 값으로 남긴다 — 나중에 켠 뒤 비교할 때
                // 이 구분이 필요하다.
                .contains("\"usedFewShot\":false");
        // 모델·프롬프트 버전은 tuple 에서 온다. 어느 버전이 틀렸는지 모르면 개선 여부를
        // 비교할 수 없다.
        assertThat(logs.entries.get(0).modelName()).isEqualTo("gemini-2.5-flash");
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private ApplyReviewDecisionService service(ActionReviewQueryPort.ReviewTarget target,
                                               RecordingReviewLog logs,
                                               RecordingVectorRepository vectors) {
        return service(target, new RecordingApplyPort(), logs, vectors);
    }

    private ApplyReviewDecisionService service(ActionReviewQueryPort.ReviewTarget target,
                                               RecordingApplyPort applied,
                                               RecordingReviewLog logs,
                                               RecordingVectorRepository vectors) {
        return new ApplyReviewDecisionService(
                new FakeQueryPort(target), applied, logs, vectors,
                accessibleGuard(), roster(), new ObjectMapper());
    }

    private static MeetingAccessGuard accessibleGuard() {
        return new MeetingAccessGuard((companyId, meetingId) -> true);
    }

    /* 참석자 명단. 명단 밖 탈출구(personId=null)를 포함한다 — 실제 명단의 모양이다. */
    private static MeetingParticipantProvider roster() {
        return meetingId -> List.of(
                new AiLayerPort.Participant(ALICE, "김서준"),
                new AiLayerPort.Participant(BOB, "박도현"),
                new AiLayerPort.Participant(null, "명단 외"));
    }

    private static ReviewDecisionCommand command(ReviewDecision decision, RejectReason reason,
                                                 Long assignee, LocalDate dueDate) {
        return new ReviewDecisionCommand(COMPANY, MEETING, ACTION, ME, decision, reason, assignee, dueDate);
    }

    private static ActionReviewQueryPort.ReviewTarget target() {
        return new ActionReviewQueryPort.ReviewTarget(
                ACTION, ALICE, LocalDate.of(2026, 8, 8), "로드맵 초안 작성", false, "PENDING",
                8812L, "서준님이 정리해주세요.", "제품 로드맵", aiValue());
    }

    private static ActionReviewQueryPort.AiValue aiValue() {
        return new ActionReviewQueryPort.AiValue(
                "로드맵 초안 작성", ALICE, AssigneeSource.EXPLICIT_CALL,
                LocalDate.of(2026, 8, 8), "gemini-2.5-flash", "v1");
    }

    // ── 가짜 구현 ───────────────────────────────────────────────────────────────

    private static final class FakeQueryPort implements ActionReviewQueryPort {

        private final ReviewTarget target;
        private boolean called;

        private FakeQueryPort(ReviewTarget target) {
            this.target = target;
        }

        @Override
        public Optional<ReviewTarget> findOne(long companyId, long meetingId, long actionId) {
            called = true;
            return Optional.ofNullable(target);
        }

        @Override
        public List<ReviewAction> findByMeeting(long companyId, long meetingId, String reviewStatus) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingApplyPort implements ActionReviewApplyPort {

        private boolean called;
        private Long assigneeMemberId;
        private LocalDate dueDate;
        private String reviewStatus;

        @Override
        public void apply(long companyId, long actionId, Long assigneeMemberId,
                          LocalDate dueDate, String reviewStatus) {
            this.called = true;
            this.assigneeMemberId = assigneeMemberId;
            this.dueDate = dueDate;
            this.reviewStatus = reviewStatus;
        }
    }

    private static final class RecordingReviewLog implements ReviewLogRepository {

        private final List<ReviewLogEntry> entries = new ArrayList<>();

        @Override
        public long append(ReviewLogEntry entry) {
            entries.add(entry);
            return 900L + entries.size();
        }
    }

    private static final class RecordingVectorRepository
            implements com.module06.backend.capture.application.port.out.TupleVectorRepository {

        private final List<VectorEntry> entries = new ArrayList<>();

        @Override
        public void enqueue(VectorEntry entry) {
            entries.add(entry);
        }
    }
}
