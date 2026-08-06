package com.module06.backend.capture.application.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.out.AssignmentTupleRepository.StoredTuple;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.ConflictType;
import com.module06.backend.capture.domain.model.Utterance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L7 자동확정 게이트 — 4조건 + L6 모순 없음.
 *
 * <p>여기서 지켜야 하는 것은 <b>조건 하나라도 못 만족하면 반드시 걸러내는 것</b>이다.
 * 새는 경로가 하나라도 있으면 검증되지 않은 배정이 「AI 확신도 높음」으로 올라가고,
 * 사람은 그걸 보고 그냥 넘긴다.
 *
 * <p>모델이 말한 확신도를 쓰지 않는다 — 자기보고 신뢰도는 실제 정확도와 맞지 않는다.
 */
class AutoConfirmGateTest {

    private static final long ALICE = 7L;
    private static final long OUTSIDER = 99L;
    private static final Set<Long> ROSTER = Set.of(ALICE);

    private final AutoConfirmGate gate = new AutoConfirmGate();

    @Test
    @DisplayName("네 조건을 전부 만족하고 모순이 없으면 자동확정한다")
    void 네_조건을_다_만족하면_자동확정이다() {
        AutoConfirmGate.Verdict verdict = evaluate(
                tuple(ALICE, AssigneeSource.EXPLICIT_CALL, 100L), true, List.of());

        assertThat(verdict.autoConfirmed()).isTrue();
        assertThat(verdict.signals().allPassed()).isTrue();
    }

    @Test
    @DisplayName("근거 발화가 없으면 통과시키지 않는다 — 사람이 검토할 재료가 없다")
    void 근거가_없으면_막는다() {
        AutoConfirmGate.Verdict verdict = evaluate(
                tuple(ALICE, AssigneeSource.EXPLICIT_CALL, null), true, List.of());

        assertThat(verdict.autoConfirmed()).isFalse();
        assertThat(verdict.signals().hasEvidence()).isFalse();
    }

    @Test
    @DisplayName("담당자가 명단 밖이면 통과시키지 않는다")
    void 명단_밖_담당자는_막는다() {
        AutoConfirmGate.Verdict verdict = evaluate(
                tuple(OUTSIDER, AssigneeSource.EXPLICIT_CALL, 100L), true, List.of());

        assertThat(verdict.autoConfirmed()).isFalse();
        assertThat(verdict.signals().assigneeInRoster()).isFalse();
    }

    @Test
    @DisplayName("담당자가 미정(unknown_person)이면 통과시키지 않는다")
    void 담당자_미정은_막는다() {
        AutoConfirmGate.Verdict verdict = evaluate(
                tuple(null, AssigneeSource.EXPLICIT_CALL, 100L), true, List.of());

        assertThat(verdict.autoConfirmed()).isFalse();
        assertThat(verdict.signals().assigneeInRoster()).isFalse();
    }

    @Test
    @DisplayName("1인칭인데 근거 발화의 화자가 미정이면 통과시키지 않는다")
    void 화자_미상의_1인칭은_막는다() {
        // "제가 할게요"의 '제가'가 누군지 모르면 배정 근거가 성립하지 않는다.
        // CAP-11 전에는 L1 이 전원 판정을 포기하므로 1인칭이 전부 여기서 걸린다 — 정상이다.
        AutoConfirmGate.Verdict verdict = evaluate(
                tuple(ALICE, AssigneeSource.FIRST_PERSON, 102L), true, List.of());

        assertThat(verdict.autoConfirmed()).isFalse();
        assertThat(verdict.signals().assigneeSourceOk()).isFalse();
    }

    @Test
    @DisplayName("1인칭이어도 화자가 확정됐으면 통과한다")
    void 화자가_확정된_1인칭은_통과한다() {
        AutoConfirmGate.Verdict verdict = evaluate(
                tuple(ALICE, AssigneeSource.FIRST_PERSON, 100L), true, List.of());

        assertThat(verdict.signals().assigneeSourceOk()).isTrue();
        assertThat(verdict.autoConfirmed()).isTrue();
    }

    @Test
    @DisplayName("판정 근거를 모르면(source=null) 통과시키지 않는다")
    void 판정근거_미상은_막는다() {
        AutoConfirmGate.Verdict verdict = evaluate(tuple(ALICE, null, 100L), true, List.of());

        assertThat(verdict.signals().assigneeSourceOk()).isFalse();
        assertThat(verdict.autoConfirmed()).isFalse();
    }

    @Test
    @DisplayName("L5 두 관점이 갈렸으면 통과시키지 않는다")
    void 관점이_갈리면_막는다() {
        AutoConfirmGate.Verdict verdict = evaluate(
                tuple(ALICE, AssigneeSource.EXPLICIT_CALL, 100L), false, List.of());

        assertThat(verdict.autoConfirmed()).isFalse();
        assertThat(verdict.signals().viewsAgree()).isFalse();
    }

    @Test
    @DisplayName("L5 를 아직 안 돌았으면(NULL) 통과시키지 않는다 — 미검증은 통과와 다르다")
    void 미검증은_막는다() {
        AutoConfirmGate.Verdict verdict = evaluate(
                tuple(ALICE, AssigneeSource.EXPLICIT_CALL, 100L), null, List.of());

        assertThat(verdict.autoConfirmed()).isFalse();
        assertThat(verdict.signals().viewsAgree()).isFalse();
    }

    @Test
    @DisplayName("신호 넷을 다 통과해도 L6 모순이 있으면 자동확정하지 않는다")
    void 모순이_있으면_막는다() {
        AutoConfirmGate.Verdict verdict = evaluate(
                tuple(ALICE, AssigneeSource.EXPLICIT_CALL, 100L), true,
                List.of(ConflictType.DUPLICATE_EVIDENCE));

        // 신호는 전부 통과했다 — 그래서 "신호가 헐거웠던 건"과 "모순 때문에 걸린 건"을
        // 나중에 따로 셀 수 있다. 그래서 둘을 한 값으로 합치지 않는다.
        assertThat(verdict.signals().allPassed()).isTrue();
        assertThat(verdict.autoConfirmed()).isFalse();
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private AutoConfirmGate.Verdict evaluate(AssignmentTuple tuple, Boolean verifyAgree,
                                             List<ConflictType> conflicts) {
        StoredTuple stored = new StoredTuple(1L, tuple, 1, "제품 로드맵", verifyAgree);
        return gate.evaluate(List.of(stored), Map.of(1L, conflicts), utterances(), ROSTER).get(1L);
    }

    private static AssignmentTuple tuple(Long assignee, AssigneeSource source, Long evidence) {
        return new AssignmentTuple("로드맵 초안 작성", assignee, source, null, evidence);
    }

    /* 발화 100 은 화자가 확정됐고, 102 는 L1 이 판정을 포기했다. */
    private static List<Utterance> utterances() {
        return List.of(
                new Utterance(100L, ALICE, 0, 3_000, "제가 초안 만들게요"),
                new Utterance(102L, null, 9_000, 12_000, "제가 정리할게요"));
    }
}
