package com.module06.backend.capture.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.service.SpeakerLabelAnchorResolver.Anchor;
import com.module06.backend.capture.application.service.SpeakerLabelAnchorResolver.Signal;
import com.module06.backend.capture.domain.model.CaptionChunk;
import com.module06.backend.capture.domain.model.Utterance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 화자 분리 라벨을 사람에 닻 내리는 판정.
 *
 * <p>여기서 라벨 하나를 잘못 닻 내리면 발화 하나가 아니라 <b>그 사람의 회의 전체가 함께
 * 틀린다.</b> 그래서 이 파일의 무게중심은 확정 경로가 아니라 <b>기권 경계</b>에 있다 —
 * 신호가 약하거나 갈리거나 서로를 부정할 때 닻을 안 내리는지가 핵심이다.
 */
class SpeakerLabelAnchorResolverTest {

    private static final long ALICE = 7L;
    private static final long BOB = 8L;
    private static final long CAROL = 9L;

    private static final Map<Long, String> NAMES = names();

    private final SpeakerLabelAnchorResolver resolver = new SpeakerLabelAnchorResolver();

    // ── 신호 1 · 자막 겹침 ────────────────────────────────────────────────

    @Test
    @DisplayName("자막이 한 라벨만 지배적으로 덮으면 그 라벨로 닻을 내린다 — 온라인 회의 모양")
    void 자막이_한_라벨을_덮으면_닻을_내린다() {
        /*
         * 각자 자기 기기로 참여하는 회의에서는 host 마이크가 host 목소리만 잡는다. 그래서
         * host 자막이 한 라벨에만 몰리고 나머지는 0 에 가깝다.
         */
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 10_000, "spk_0", "가"),
                        labelled(2L, 10_000, 20_000, "spk_1", "나"),
                        labelled(3L, 20_000, 30_000, "spk_0", "다")),
                List.of(caption(ALICE, 0, 10_000), caption(ALICE, 20_000, 30_000)),
                Set.of(ALICE, BOB), Map.of());

        assertThat(anchors.get("spk_0")).isEqualTo(new Anchor(ALICE, Signal.CAPTION_OVERLAP));
    }

    @Test
    @DisplayName("⚠ 자막이 여러 라벨을 고르게 덮으면 기권한다 — 대면 회의의 방 마이크다")
    void 자막이_고르게_덮으면_기권한다() {
        /*
         * host 노트북 마이크 하나로 녹음하는 대면 회의에서는 host 브라우저 음성인식이 방 안
         * 모두를 받아쓴다. 이때 닻을 내리면 아무 라벨이나 host 가 되고, 회의 전체가 host
         * 것이 된다 — 예전 「전원 자막」 게이트가 막으려던 실패 그대로다.
         *
         * 회의 종류 플래그 없이 비율의 모양만으로 이 상황이 구분된다.
         */
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 10_000, "spk_0", "가"),
                        labelled(2L, 10_000, 20_000, "spk_1", "나"),
                        labelled(3L, 20_000, 30_000, "spk_2", "다")),
                List.of(caption(ALICE, 0, 30_000)),
                Set.of(ALICE, BOB, CAROL), Map.of());

        assertThat(anchors).isEmpty();
    }

    @Test
    @DisplayName("절반을 못 덮으면 기권한다 — 스치듯 겹친 것으로 확정하지 않는다")
    void 겹침이_모자라면_기권한다() {
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 10_000, "spk_0", "가"),
                        labelled(2L, 10_000, 40_000, "spk_0", "나"),
                        labelled(3L, 40_000, 50_000, "spk_1", "다")),
                // spk_0 의 40초 중 10초(25%)만 덮는다.
                List.of(caption(ALICE, 0, 10_000)),
                Set.of(ALICE, BOB), Map.of());

        assertThat(anchors).isEmpty();
    }

    // ── 신호 2 · 호명 응답 ────────────────────────────────────────────────

    @Test
    @DisplayName("이름이 불린 직후 답한 라벨이 반복되면 그 사람으로 본다")
    void 호명_응답이_쌓이면_닻을_내린다() {
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 3_000, "spk_0", "이태연씨 이거 해줄 수 있어요?"),
                        labelled(2L, 3_000, 6_000, "spk_1", "네 제가 하겠습니다"),
                        labelled(3L, 10_000, 13_000, "spk_0", "이태연씨 그건 어떻게 됐어요?"),
                        labelled(4L, 13_000, 16_000, "spk_1", "내일까지 정리하겠습니다")),
                List.of(), Set.of(ALICE, BOB), NAMES);

        assertThat(anchors.get("spk_1")).isEqualTo(new Anchor(BOB, Signal.RESPONSE_TO_CALL));
    }

    @Test
    @DisplayName("표가 하나면 닻을 내리지 않는다 — 제3자가 대신 답하거나 언급인 경우가 흔하다")
    void 표가_하나면_기권한다() {
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 3_000, "spk_0", "이태연씨가 지난주에 했었죠"),
                        labelled(2L, 3_000, 6_000, "spk_1", "네 맞아요")),
                List.of(), Set.of(ALICE, BOB), NAMES);

        assertThat(anchors).isEmpty();
    }

    @Test
    @DisplayName("표가 갈리면 기권한다 — 둘 중 누구인지 모르는 것이지 1등인 것이 아니다")
    void 표가_갈리면_기권한다() {
        List<Utterance> utterances = new ArrayList<>();
        // spk_1 뒤에 이태연 2표, 김현지 2표. 배수를 못 채운다.
        addCallAndAnswer(utterances, 0, "이태연씨 확인 부탁해요", "spk_1");
        addCallAndAnswer(utterances, 20_000, "이태연씨 어떠세요", "spk_1");
        addCallAndAnswer(utterances, 40_000, "김현지씨 확인 부탁해요", "spk_1");
        addCallAndAnswer(utterances, 60_000, "김현지씨 어떠세요", "spk_1");

        Map<String, Anchor> anchors = resolver.resolve(
                utterances, List.of(), Set.of(ALICE, BOB, CAROL), NAMES);

        assertThat(anchors).isEmpty();
    }

    @Test
    @DisplayName("같은 라벨이 이어 말한 것은 응답이 아니다 — 자기 이름을 부르고 자기가 답한 꼴이 된다")
    void 같은_라벨의_연속은_응답이_아니다() {
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 3_000, "spk_0", "이태연씨 이거 봐주세요"),
                        labelled(2L, 3_000, 6_000, "spk_0", "아니 제가 할게요"),
                        labelled(3L, 10_000, 13_000, "spk_0", "이태연씨 이것도요"),
                        labelled(4L, 13_000, 16_000, "spk_0", "이것도 제가 하죠")),
                List.of(), Set.of(ALICE, BOB), NAMES);

        assertThat(anchors).isEmpty();
    }

    @Test
    @DisplayName("이름을 부른 적 있는 라벨에는 그 사람으로 닻을 내리지 않는다")
    void 자기_이름을_부른_라벨은_배제한다() {
        /*
         * 사람은 자기 이름을 잘 부르지 않는다. 두 신호가 서로를 부정하는 셈이라 어느 쪽이
         * 맞는지 정할 근거가 없다.
         */
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 3_000, "spk_1", "이태연씨 이거 해주세요"),
                        labelled(2L, 3_000, 6_000, "spk_0", "네"),
                        labelled(3L, 10_000, 13_000, "spk_0", "이태연씨 다 됐나요"),
                        labelled(4L, 13_000, 16_000, "spk_1", "네 다 했습니다"),
                        labelled(5L, 20_000, 23_000, "spk_0", "이태연씨 고마워요"),
                        labelled(6L, 23_000, 26_000, "spk_1", "아닙니다")),
                List.of(), Set.of(ALICE, BOB), NAMES);

        // spk_1 이 이태연을 부른 적이 있으므로(발화 1) 표가 2개여도 닻을 내리지 않는다.
        assertThat(anchors.get("spk_1")).isNull();
    }

    // ── 신호 3 · 소거법 · 모순 ────────────────────────────────────────────

    @Test
    @DisplayName("라벨과 사람이 하나씩 남으면 소거법으로 채운다")
    void 마지막_하나를_소거법으로_채운다() {
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 10_000, "spk_0", "가"),
                        labelled(2L, 10_000, 20_000, "spk_1", "나")),
                List.of(caption(ALICE, 0, 10_000)),
                Set.of(ALICE, BOB), Map.of());

        assertThat(anchors.get("spk_1")).isEqualTo(new Anchor(BOB, Signal.ELIMINATION));
    }

    @Test
    @DisplayName("라벨 수와 참석자 수가 다르면 소거법을 쓰지 않는다")
    void 수가_다르면_소거법을_쓰지_않는다() {
        /*
         * 라벨이 더 적으면 남은 라벨 하나에 두 사람이 섞여 있을 수 있다. 거기에 남은 사람을
         * 넣으면 남의 발화가 그 사람 것이 된다.
         */
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 10_000, "spk_0", "가"),
                        labelled(2L, 10_000, 20_000, "spk_1", "나")),
                List.of(caption(ALICE, 0, 10_000)),
                Set.of(ALICE, BOB, CAROL), Map.of());

        assertThat(anchors).containsOnlyKeys("spk_0");
    }

    @Test
    @DisplayName("라벨이 넘칠 때는 한 사람이 여러 라벨을 가져도 둔다 — 과분할은 정상이다")
    void 라벨이_넘치면_중복을_허용한다() {
        /*
         * 화자 분리는 같은 사람을 여러 라벨로 쪼개는 쪽으로 틀린다(마이크 거리·목소리 크기가
         * 바뀌면 그렇다). 참석자 2명인데 라벨이 3개면 그게 실제로 일어난 것이므로 버리지 않는다.
         *
         * 이태연이 spk_1·spk_2 두 라벨로 쪼개졌고, 양쪽 모두 호명 뒤에 답했다.
         */
        List<Utterance> utterances = new ArrayList<>();
        addCallAndAnswer(utterances, 0, "이태연씨 확인 부탁해요", "spk_1");
        addCallAndAnswer(utterances, 10_000, "이태연씨 어떠세요", "spk_1");
        addCallAndAnswer(utterances, 20_000, "이태연씨 이것도요", "spk_2");
        addCallAndAnswer(utterances, 30_000, "이태연씨 부탁해요", "spk_2");

        Map<String, Anchor> anchors = resolver.resolve(
                utterances, List.of(), Set.of(ALICE, BOB), NAMES);

        assertThat(anchors).containsOnlyKeys("spk_1", "spk_2");
        assertThat(anchors.values()).allSatisfy(anchor -> assertThat(anchor.memberId()).isEqualTo(BOB));
    }

    @Test
    @DisplayName("라벨이 안 넘치는데 한 사람이 둘이면 전부 비운다 — 둘 중 하나가 틀렸는데 모른다")
    void 라벨이_안_넘치면_중복을_버린다() {
        /*
         * 자막 겹침은 spk_0 을 박종준이라 하고, 호명 응답은 spk_1 을 박종준이라 한다.
         * 라벨 2개 · 참석자 2명이라 쪼개질 이유가 없는데 겹쳤으므로 둘 중 하나가 틀렸고,
         * 어느 쪽인지 정할 근거가 없다.
         */
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 10_000, "spk_0", "박종준씨 확인 부탁해요"),
                        labelled(2L, 10_000, 20_000, "spk_1", "네 알겠습니다"),
                        labelled(3L, 20_000, 30_000, "spk_0", "박종준씨 어떠세요"),
                        labelled(4L, 30_000, 40_000, "spk_1", "다 됐습니다")),
                List.of(caption(ALICE, 0, 10_000), caption(ALICE, 20_000, 30_000)),
                Set.of(ALICE, BOB), NAMES);

        assertThat(anchors).isEmpty();
    }

    // ── 경계 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("라벨이 없으면 빈 결과다 — 화자 분리를 안 쓴 회의")
    void 라벨이_없으면_비운다() {
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(new Utterance(1L, null, 0, 10_000, "가", null)),
                List.of(caption(ALICE, 0, 10_000)),
                Set.of(ALICE, BOB), NAMES);

        assertThat(anchors).isEmpty();
    }

    @Test
    @DisplayName("이름표가 없어도 자막 겹침 신호는 그대로 돈다")
    void 이름표가_없어도_나머지_신호는_돈다() {
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(labelled(1L, 0, 10_000, "spk_0", "가"),
                        labelled(2L, 10_000, 20_000, "spk_1", "나"),
                        labelled(3L, 20_000, 30_000, "spk_2", "다")),
                List.of(caption(ALICE, 0, 10_000)),
                Set.of(ALICE, BOB, CAROL), Map.of());

        assertThat(anchors.get("spk_0")).isEqualTo(new Anchor(ALICE, Signal.CAPTION_OVERLAP));
    }

    @Test
    @DisplayName("끝 오프셋이 없는 발화는 겹침 계산에서 빠진다 — 길이를 모르면 비율을 못 낸다")
    void 길이를_모르는_발화는_겹침에서_뺀다() {
        Map<String, Anchor> anchors = resolver.resolve(
                List.of(new Utterance(1L, null, 0, null, "가", "spk_0"),
                        labelled(2L, 10_000, 20_000, "spk_1", "나")),
                List.of(caption(ALICE, 0, 5_000)),
                Set.of(ALICE, BOB), Map.of());

        // spk_0 은 분모가 0 이라 후보가 못 된다. 소거법도 남은 라벨이 둘이라 안 돈다.
        assertThat(anchors).isEmpty();
    }

    /* spk_0 이 부르고 지정한 라벨이 답하는 한 쌍. 표를 쌓는 테스트에서 쓴다. */
    private static void addCallAndAnswer(List<Utterance> into, int atMs, String call, String answerLabel) {
        long id = into.size() + 1L;
        into.add(labelled(id, atMs, atMs + 3_000, "spk_0", call));
        into.add(labelled(id + 1, atMs + 3_000, atMs + 6_000, answerLabel, "네 알겠습니다"));
    }

    private static Utterance labelled(long id, int startMs, int endMs, String label, String text) {
        return new Utterance(id, null, startMs, endMs, text, label);
    }

    private static CaptionChunk caption(long memberId, int startMs, int endMs) {
        return new CaptionChunk(memberId, startMs, endMs, new BigDecimal("-18.00"));
    }

    private static Map<Long, String> names() {
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(ALICE, "박종준");
        names.put(BOB, "이태연");
        names.put(CAROL, "김현지");
        return names;
    }
}
