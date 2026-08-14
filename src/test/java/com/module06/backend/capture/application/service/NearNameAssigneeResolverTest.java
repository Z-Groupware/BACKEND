package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.Utterance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이름 근접 매칭 — STT 가 한 글자 잘못 들은 이름을 코드가 참석자 명단에 잇는다.
 *
 * <p>여기서 지켜야 하는 것은 <b>엉뚱한 사람에게 잇지 않는 것</b>이다. 2026-08-14 실측에서
 * 20 회 넘는 판정 중 오답이 0 건이었고, 그 성질은 담당자를 채우는 것보다 중요하다 —
 * 담당자가 비면 사람이 지정하지만, 틀린 담당자는 그대로 보드에 꽂힌다.
 *
 * <p>그래서 기권 쪽 경계를 정답 쪽보다 많이 고정한다.
 */
class NearNameAssigneeResolverTest {

    private static final long HYUNJI = 23L;      // 김현지 — 전사에서 "김현진"으로 들린다
    private static final long SUMIN = 31L;       // 정수민
    private static final LocalDate DUE = LocalDate.of(2026, 8, 20);

    private static final Map<Long, String> ROSTER = Map.of(HYUNJI, "김현지", SUMIN, "정수민");

    private final NearNameAssigneeResolver resolver = new NearNameAssigneeResolver();

    // ── 이어 주는 자리 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("근거 발화에 한 글자 틀린 이름이 있으면 참석자와 잇는다")
    void 한_글자_차이를_잇는다() {
        NearNameAssigneeResolver.Resolved resolved = resolveOne(
                abstained(12L),
                utterances(
                        utterance(12L, "김현진 님이 그 경로 담당이시죠?")));

        assertThat(resolved.nearMatched()).isTrue();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isEqualTo(HYUNJI);
    }

    @Test
    @DisplayName("근거 발화 ±3 안에 이름이 있으면 잇는다 — 배정은 여러 발화에 걸쳐 있다")
    void 근거_주변_발화의_이름도_잇는다() {
        /*
         * 실측에서 드러난 모양이다 — "누가"와 "무엇"이 다른 발화에 있고, L4 는 근거를 하나만
         * 고를 수 있어 "무엇"쪽을 골랐다. 그 발화에는 이름이 없다.
         */
        NearNameAssigneeResolver.Resolved resolved = resolveOne(
                abstained(9L),
                utterances(
                        utterance(7L, "김현진 님이 그 경로 담당이시죠?"),
                        utterance(8L, "언제까지 가능할까요?"),
                        utterance(9L, "VAD 절단점을 부르는 경로가 아직 비어있어요."))); // ← 근거

        assertThat(resolved.nearMatched()).isTrue();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isEqualTo(HYUNJI);
    }

    @Test
    @DisplayName("정확히 같은 이름이 함께 있으면 그쪽이 이긴다 — 비슷한 이름에 밀리지 않는다")
    void 정확히_같은_이름이_이긴다() {
        /* 김현지·김현진이 둘 다 참석한 회의. 전사가 "김현진"이면 그건 오인식이 아니다. */
        Map<Long, String> roster = Map.of(HYUNJI, "김현지", 24L, "김현진");

        NearNameAssigneeResolver.Resolved resolved = resolveOne(
                abstained(12L),
                utterances(utterance(12L, "김현진 님이 그 경로 담당이시죠?")),
                roster);

        assertThat(resolved.tuple().assigneeCandidateMemberId()).isEqualTo(24L);
    }

    @Test
    @DisplayName("담당자만 채우고 나머지 자리는 그대로 둔다")
    void 담당자_외에는_건드리지_않는다() {
        AssignmentTuple before = new AssignmentTuple(
                "VAD 절단점 경로 연결", null, AssigneeSource.EXPLICIT_CALL, DUE, 12L);

        AssignmentTuple after = resolveOne(before,
                utterances(utterance(12L, "김현진 님이 그 경로 담당이시죠?"))).tuple();

        assertThat(after.title()).isEqualTo(before.title());
        assertThat(after.dueDate()).isEqualTo(DUE);
        assertThat(after.evidenceUtteranceId()).isEqualTo(12L);
        /*
         * source 는 EXPLICIT_CALL 그대로다. 모델이 "명시적 호명이다"라고 판정한 것은 사실이고,
         * 바뀐 것은 그 이름을 명단에 이었다는 점뿐이다 — 그 사실은 nearMatched 가 남긴다.
         */
        assertThat(after.assigneeSource()).isEqualTo(AssigneeSource.EXPLICIT_CALL);
    }

    // ── 기권하는 자리 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("±3 밖의 이름으로는 잇지 않는다 — 회의 내내 불린 사람이 아무 배정에나 붙는다")
    void 탐색_범위_밖은_잇지_않는다() {
        List<Utterance> utterances = new ArrayList<>();
        utterances.add(utterance(1L, "김현진 님이 그 경로 담당이시죠?"));
        for (long id = 2L; id <= 6L; id++) {
            utterances.add(utterance(id, "네, 확인해 보겠습니다."));
        }

        NearNameAssigneeResolver.Resolved resolved = resolveOne(abstained(6L), utterances);

        assertThat(resolved.nearMatched()).isFalse();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isNull();
    }

    @Test
    @DisplayName("두 글자 이상 다르면 잇지 않는다 — 허용 거리는 한 글자다")
    void 두_글자_차이는_잇지_않는다() {
        NearNameAssigneeResolver.Resolved resolved = resolveOne(
                abstained(12L),
                utterances(utterance(12L, "김서준 님이 그 경로 담당이시죠?")));

        assertThat(resolved.nearMatched()).isFalse();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isNull();
    }

    @Test
    @DisplayName("같은 거리의 참석자가 둘이면 기권한다 — 하나를 고르면 검증할 수 없는 추측이다")
    void 후보가_둘이면_기권한다() {
        /* 김현지·김현진이 둘 다 참석했고 전사는 "김현수" — 둘 다 한 글자 차이다. */
        Map<Long, String> roster = Map.of(HYUNJI, "김현지", 24L, "김현진");

        NearNameAssigneeResolver.Resolved resolved = resolveOne(
                abstained(12L),
                utterances(utterance(12L, "김현수 님이 그 경로 담당이시죠?")),
                roster);

        assertThat(resolved.nearMatched()).isFalse();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isNull();
    }

    @Test
    @DisplayName("1인칭 배정은 건드리지 않는다 — 이름이 아니라 화자가 필요한 경로다")
    void 일인칭은_건드리지_않는다() {
        AssignmentTuple tuple = new AssignmentTuple(
                "회기 테스트 추가", null, AssigneeSource.FIRST_PERSON, null, 54L);

        NearNameAssigneeResolver.Resolved resolved = resolveOne(tuple,
                // 주변에 이름이 있어도 줍지 않는다. "제가"가 누군지는 speakerMemberId 가 정한다.
                utterances(
                        utterance(53L, "김현진 님도 같이 보시죠."),
                        utterance(54L, "회기 테스트 하나만 넣고 닫겠습니다.")));

        assertThat(resolved.nearMatched()).isFalse();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isNull();
    }

    @Test
    @DisplayName("판정 불가(source=null)는 건드리지 않는다 — 호명인지조차 모르는 발화다")
    void source가_없으면_건드리지_않는다() {
        AssignmentTuple tuple = new AssignmentTuple("차단 문구 반영", null, null, null, 65L);

        NearNameAssigneeResolver.Resolved resolved = resolveOne(tuple,
                utterances(utterance(65L, "김현진 님이 그 경로 담당이시죠?")));

        assertThat(resolved.nearMatched()).isFalse();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isNull();
    }

    @Test
    @DisplayName("모델이 정한 담당자를 덮어쓰지 않는다")
    void 이미_담당자가_있으면_덮어쓰지_않는다() {
        AssignmentTuple tuple = new AssignmentTuple(
                "차단 문구 반영", SUMIN, AssigneeSource.EXPLICIT_CALL, null, 12L);

        NearNameAssigneeResolver.Resolved resolved = resolveOne(tuple,
                utterances(utterance(12L, "김현진 님이 그 경로 담당이시죠?")));

        assertThat(resolved.nearMatched()).isFalse();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isEqualTo(SUMIN);
    }

    @Test
    @DisplayName("L1.5 주석에 적힌 이름으로는 잇지 않는다 — 전사에 불린 이름만 본다")
    void L1_5_주석의_이름은_줍지_않는다() {
        /*
         * 주석에는 참석자 이름이 정확한 표기로 들어간다. 그걸 근거로 이으면 담당자를 이은
         * 것이 근접 매칭인지 L1.5 인지 구분할 수 없고, 실측에서 L1.5 는 담당자 판정을 바꾸지
         * 못한 계층이다. 그 결론을 이 코드가 흐리게 만들지 않는다.
         */
        NearNameAssigneeResolver.Resolved resolved = resolveOne(
                abstained(65L),
                utterances(utterance(65L,
                        "그건 아까 차단 문구로 같이 해주세요. [지시어 \"그건\" → 정수민]")));

        assertThat(resolved.nearMatched()).isFalse();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isNull();
    }

    @Test
    @DisplayName("근거 발화가 발화 목록에 없으면 기권한다")
    void 근거_발화를_못_찾으면_기권한다() {
        NearNameAssigneeResolver.Resolved resolved = resolveOne(
                abstained(999L),
                utterances(utterance(12L, "김현진 님이 그 경로 담당이시죠?")));

        assertThat(resolved.nearMatched()).isFalse();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isNull();
    }

    @Test
    @DisplayName("두 글자 이름은 정확히 같을 때만 잇는다 — 절반이 달라도 같게 보면 아무 낱말이나 걸린다")
    void 짧은_이름은_한_글자_차이를_허용하지_않는다() {
        Map<Long, String> roster = Map.of(HYUNJI, "이수");

        assertThat(resolveOne(abstained(12L),
                utterances(utterance(12L, "이순 님이 맡아 주세요.")), roster)
                .tuple().assigneeCandidateMemberId()).isNull();

        assertThat(resolveOne(abstained(12L),
                utterances(utterance(12L, "이수 님이 맡아 주세요.")), roster)
                .tuple().assigneeCandidateMemberId()).isEqualTo(HYUNJI);
    }

    @Test
    @DisplayName("참석자 명단이 비면 아무것도 잇지 않는다")
    void 명단이_비면_그대로_돌려준다() {
        NearNameAssigneeResolver.Resolved resolved = resolveOne(
                abstained(12L),
                utterances(utterance(12L, "김현진 님이 그 경로 담당이시죠?")),
                Map.of());

        assertThat(resolved.nearMatched()).isFalse();
        assertThat(resolved.tuple().assigneeCandidateMemberId()).isNull();
    }

    @Test
    @DisplayName("tuple 순서를 그대로 돌려준다 — 호출자가 근거 발화로 확정 항목을 되짚는다")
    void 순서를_유지한다() {
        List<AssignmentTuple> tuples = List.of(abstained(12L), abstained(65L), abstained(9L));

        List<NearNameAssigneeResolver.Resolved> resolved = resolver.resolve(tuples,
                utterances(utterance(12L, "김현진 님이 그 경로 담당이시죠?")), ROSTER);

        assertThat(resolved).hasSize(3);
        assertThat(resolved.stream().map(r -> r.tuple().evidenceUtteranceId()).toList())
                .containsExactly(12L, 65L, 9L);
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    /* L4 가 담당자를 비운 채 돌려준 tuple. 호명인 것은 알아봤고 이름을 못 이은 상태다. */
    private static AssignmentTuple abstained(long evidenceUtteranceId) {
        return new AssignmentTuple("VAD 절단점 경로 연결", null,
                AssigneeSource.EXPLICIT_CALL, null, evidenceUtteranceId);
    }

    private NearNameAssigneeResolver.Resolved resolveOne(AssignmentTuple tuple,
                                                         List<Utterance> utterances) {
        return resolveOne(tuple, utterances, ROSTER);
    }

    private NearNameAssigneeResolver.Resolved resolveOne(AssignmentTuple tuple,
                                                         List<Utterance> utterances,
                                                         Map<Long, String> roster) {
        return resolver.resolve(List.of(tuple), utterances, roster).get(0);
    }

    private static List<Utterance> utterances(Utterance... utterances) {
        return List.of(utterances);
    }

    /* 화자는 전부 미정(NULL)이다 — 자막이 없는 조건이 실측의 조건이고, 근접 매칭은 화자를 안 본다. */
    private static Utterance utterance(long id, String text) {
        return new Utterance(id, null, (int) id * 1_000, (int) id * 1_000 + 900, text);
    }
}
