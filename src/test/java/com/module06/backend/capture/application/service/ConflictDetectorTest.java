package com.module06.backend.capture.application.service;

import java.time.LocalDate;
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
 * L6 규칙·모순 검사 — 데이터만 보고 확실히 말할 수 있는 것만 본다(모델 아님).
 *
 * <p>여기서 지켜야 하는 것은 <b>없는 모순을 만들어내지 않는 것</b>이다. 오탐이 쌓이면
 * 자동확정이 거의 안 나오고, 그러면 게이트가 있으나 마나가 된다.
 */
class ConflictDetectorTest {

    private static final long ALICE = 7L;
    private static final long BOB = 8L;
    private static final long OUTSIDER = 99L;
    private static final LocalDate MEETING_DATE = LocalDate.of(2026, 8, 5);
    private static final Set<Long> ROSTER = Set.of(ALICE, BOB);

    private final ConflictDetector detector = new ConflictDetector();

    @Test
    @DisplayName("멀쩡한 tuple 에는 모순을 만들어내지 않는다")
    void 정상_tuple은_모순이_없다() {
        List<ConflictType> conflicts = detect(
                tuple(1L, "로드맵 초안", ALICE, AssigneeSource.EXPLICIT_CALL, LocalDate.of(2026, 8, 7), 100L));

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("같은 근거 발화에서 나온 tuple 이 둘이면 양쪽 다 중복으로 표시한다")
    void 같은_근거의_tuple은_중복이다() {
        // L2 가 주제 경계에 오버랩 3발화를 얹으므로, 경계 발화 하나가 두 주제에서 각각
        // 항목이 되고 각각 tuple 이 될 수 있다 — 드문 사고가 아니라 정상 동작의 부산물이다.
        Map<Long, List<ConflictType>> result = detector.detect(
                List.of(stored(1L, tuple(1L, "로드맵 초안", ALICE, AssigneeSource.EXPLICIT_CALL, null, 100L)),
                        stored(2L, tuple(2L, "로드맵 정리", ALICE, AssigneeSource.EXPLICIT_CALL, null, 100L))),
                utterances(), ROSTER, MEETING_DATE);

        // 어느 쪽이 원본인지 코드가 알 수 없다. 하나를 골라 남기면 그 선택이 곧 추측이다.
        assertThat(result.get(1L)).contains(ConflictType.DUPLICATE_EVIDENCE);
        assertThat(result.get(2L)).contains(ConflictType.DUPLICATE_EVIDENCE);
    }

    @Test
    @DisplayName("근거 발화가 다르면 제목이 비슷해도 중복이 아니다")
    void 근거가_다르면_중복이_아니다() {
        Map<Long, List<ConflictType>> result = detector.detect(
                List.of(stored(1L, tuple(1L, "로드맵 초안", ALICE, AssigneeSource.EXPLICIT_CALL, null, 100L)),
                        stored(2L, tuple(2L, "로드맵 초안", ALICE, AssigneeSource.EXPLICIT_CALL, null, 101L))),
                utterances(), ROSTER, MEETING_DATE);

        assertThat(result.get(1L)).isEmpty();
        assertThat(result.get(2L)).isEmpty();
    }

    @Test
    @DisplayName("기한이 회의 날짜보다 과거면 모순이다")
    void 회의보다_이른_기한은_모순이다() {
        List<ConflictType> conflicts = detect(
                tuple(1L, "로드맵 초안", ALICE, AssigneeSource.EXPLICIT_CALL,
                        MEETING_DATE.minusDays(1), 100L));

        assertThat(conflicts).containsExactly(ConflictType.DUE_BEFORE_MEETING);
    }

    @Test
    @DisplayName("회의 당일 마감은 모순이 아니다 — '오늘 안에 해주세요'는 흔한 말이다")
    void 회의_당일_기한은_정상이다() {
        List<ConflictType> conflicts = detect(
                tuple(1L, "로드맵 초안", ALICE, AssigneeSource.EXPLICIT_CALL, MEETING_DATE, 100L));

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("회의 날짜를 모르면 기한을 검사하지 않는다 — 오늘로 재면 재실행에서 전부 과거가 된다")
    void 회의_날짜가_없으면_기한을_안_본다() {
        Map<Long, List<ConflictType>> result = detector.detect(
                List.of(stored(1L, tuple(1L, "로드맵 초안", ALICE, AssigneeSource.EXPLICIT_CALL,
                        LocalDate.of(2020, 1, 1), 100L))),
                utterances(), ROSTER, null);

        assertThat(result.get(1L)).isEmpty();
    }

    @Test
    @DisplayName("담당자가 참석자 명단 밖이면 모순이다")
    void 명단_밖_담당자는_모순이다() {
        List<ConflictType> conflicts = detect(
                tuple(1L, "로드맵 초안", OUTSIDER, AssigneeSource.EXPLICIT_CALL, null, 100L));

        assertThat(conflicts).containsExactly(ConflictType.ASSIGNEE_NOT_IN_ROSTER);
    }

    @Test
    @DisplayName("1인칭인데 근거 발화의 화자를 모르면 모순이다")
    void 화자_미상의_1인칭은_모순이다() {
        // "제가 할게요"의 '제가'가 누군지 모르는 상태다. 담당자가 채워져 있다면 다른 데서 온 추론이다.
        // L1 이 판정을 포기한 발화(102)에서 나온다 — CAP-11 전에는 전원이 이 상태다.
        List<ConflictType> conflicts = detect(
                tuple(1L, "로드맵 초안", ALICE, AssigneeSource.FIRST_PERSON, null, 102L));

        assertThat(conflicts).containsExactly(ConflictType.FIRST_PERSON_WITHOUT_SPEAKER);
    }

    @Test
    @DisplayName("1인칭이어도 화자가 확정된 발화면 모순이 아니다")
    void 화자가_확정된_1인칭은_정상이다() {
        List<ConflictType> conflicts = detect(
                tuple(1L, "로드맵 초안", ALICE, AssigneeSource.FIRST_PERSON, null, 100L));

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("명시적 호명은 화자를 몰라도 모순이 아니다 — 발화 자체에 담당자가 들어 있다")
    void 명시적_호명은_화자와_무관하다() {
        List<ConflictType> conflicts = detect(
                tuple(1L, "로드맵 초안", ALICE, AssigneeSource.EXPLICIT_CALL, null, 102L));

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("판정 근거는 있는데 담당자가 없으면 모순이다 — 두 값이 서로를 부정한다")
    void 담당자_없는_판정근거는_모순이다() {
        List<ConflictType> conflicts = detect(
                tuple(1L, "로드맵 초안", null, AssigneeSource.EXPLICIT_CALL, null, 100L));

        assertThat(conflicts).containsExactly(ConflictType.SOURCE_WITHOUT_ASSIGNEE);
    }

    @Test
    @DisplayName("담당자도 근거도 없는 tuple 은 모순이 아니다 — 배정 미정은 정상이다")
    void 담당자_미정은_모순이_아니다() {
        List<ConflictType> conflicts = detect(tuple(1L, "로드맵 초안", null, null, null, 100L));

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("모순이 없는 tuple 도 결과에 담는다 — '검사했고 깨끗함'과 '안 봄'은 다르다")
    void 모순이_없어도_결과에_담는다() {
        Map<Long, List<ConflictType>> result = detector.detect(
                List.of(stored(1L, tuple(1L, "로드맵 초안", ALICE, AssigneeSource.EXPLICIT_CALL, null, 100L))),
                utterances(), ROSTER, MEETING_DATE);

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L)).isEmpty();
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private List<ConflictType> detect(AssignmentTuple tuple) {
        return detector.detect(List.of(stored(1L, tuple)), utterances(), ROSTER, MEETING_DATE).get(1L);
    }

    private static StoredTuple stored(long id, AssignmentTuple tuple) {
        // 근접 매칭 여부는 L6 판정에 쓰이지 않는다(L7 만 본다) — 기본을 false 로 둔다.
        return new StoredTuple(id, tuple, 1, "제품 로드맵", true, false);
    }

    private static AssignmentTuple tuple(long ignoredSeq, String title, Long assignee,
                                         AssigneeSource source, LocalDate dueDate, Long evidence) {
        return new AssignmentTuple(title, assignee, source, dueDate, evidence);
    }

    /* 발화 100·101 은 화자가 확정됐고, 102 는 L1 이 판정을 포기했다. */
    private static List<Utterance> utterances() {
        return List.of(
                new Utterance(100L, ALICE, 0, 3_000, "제가 초안 만들게요", null),
                new Utterance(101L, BOB, 5_000, 8_000, "저도 돕겠습니다", null),
                new Utterance(102L, null, 9_000, 12_000, "제가 정리할게요", null));
    }
}
