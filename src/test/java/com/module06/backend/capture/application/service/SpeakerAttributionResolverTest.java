package com.module06.backend.capture.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.service.SpeakerAttributionResolver.Attribution;
import com.module06.backend.capture.domain.model.CaptionChunk;
import com.module06.backend.capture.domain.model.SpeakerSource;
import com.module06.backend.capture.domain.model.Utterance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L1 화자 귀속 판정 — rms·명단으로 하는 코드 판정이다(모델 아님).
 *
 * <p>이 판정이 틀리면 그 발화가 근거인 액션이 엉뚱한 사람에게 배정되고, 받은 사람은 자기 일이
 * 아닌 것을 처리해야 한다. 그래서 <b>애매할 때 포기하는지</b>가 확정하는지보다 중요하다 —
 * 아래 테스트 절반이 포기 경로다.
 */
class SpeakerAttributionResolverTest {

    private static final long ALICE = 7L;
    private static final long BOB = 8L;
    private static final long CAROL = 9L;

    private final SpeakerAttributionResolver resolver = new SpeakerAttributionResolver();

    @Test
    @DisplayName("창 안 유일한 후보가 있고 전원이 자막을 보냈으면 화자로 정한다")
    void 유일한_후보를_화자로_정한다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00")),
                Set.of(ALICE));

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("자막을 안 켠 참석자가 있으면 후보가 한 명이어도 포기한다")
    void 전원이_자막을_보내지_않으면_단독_후보를_믿지_않는다() {
        // 참석자 3명, 자막은 앨리스만 보낸다. 이때 모든 구간의 유일한 후보가 앨리스가 되고,
        // 확정해 버리면 밥·캐롤의 발화까지 전부 앨리스 것이 된다 — 회의 전체 규모의 오귀속이다.
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00")),
                Set.of(ALICE, BOB, CAROL));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("전원이 자막을 보낸 회의에서는 후보가 한 명이어도 확정한다")
    void 전원_자막이면_단독_후보를_확정한다() {
        // 앨리스·밥 둘 다 자막을 보내는 회의다. 이 구간에 밥의 자막이 없다는 것은
        // "밥은 이때 말하지 않았다"는 뜻이므로 앨리스로 확정할 수 있다.
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 60_000, 62_000, "-19.00")),
                Set.of(ALICE, BOB));

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("자막이 아예 없으면 전원 판정 포기다 — CAP-11 미구현 상태의 정상 동작")
    void 자막이_없으면_전원_포기한다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000), utterance(2L, 20_000, 22_000)),
                List.of(),
                Set.of(ALICE, BOB));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("±1.5초 창을 벗어난 자막은 후보가 아니다")
    void 시간창을_벗어난_자막은_무시한다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                // 13.5초에서 끝나는 창의 바로 밖(13.6초 시작)
                List.of(caption(ALICE, 13_600, 15_000, "-10.00"),
                        caption(BOB, 10_000, 12_000, "-30.00")),
                Set.of(ALICE, BOB));

        // 앨리스가 훨씬 컸지만 창 밖이다. 밥만 후보로 남고, 전원 자막이므로 확정된다.
        assertThat(result).containsExactly(new Attribution(1L, BOB, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("창 경계에 걸친 자막은 포함으로 본다 — 겹치기만 하면 후보다")
    void 창에_걸친_자막은_후보다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                // 창은 8.5초~13.5초. 이 자막은 13.5초에 시작해 경계에 닿는다.
                List.of(caption(ALICE, 13_500, 20_000, "-10.00")),
                Set.of(ALICE));

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("시작 오프셋이 없는 발화는 판정하지 않는다 — 창을 만들 수 없다")
    void 오프셋이_없는_발화는_포기한다() {
        List<Attribution> result = resolver.resolve(
                List.of(new Utterance(1L, null, null, null, "위치를 모르는 발화")),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 60_000, 62_000, "-30.00")),
                Set.of(ALICE, BOB));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("종료 오프셋이 없으면 시작 지점만으로 창을 만든다")
    void 종료_오프셋이_없어도_판정한다() {
        List<Attribution> result = resolver.resolve(
                // endOffsetMs = null → 길이 0 으로 보고 10초 ±1.5초 창
                List.of(new Utterance(1L, null, 10_000, null, "종료 시각이 없는 발화")),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 60_000, 62_000, "-30.00")),
                Set.of(ALICE, BOB));

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("참석자 명단이 비어 있으면 아무것도 확정하지 않는다")
    void 참석자_명단이_비면_포기한다() {
        // 명단을 못 읽은 상황이다. "전원 자막"도 소거법도 판단할 분모가 없다.
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00")),
                Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("명단 밖 자막은 후보가 아니다 — 참석자가 아닌 사람이 화자로 확정되면 안 된다")
    void 명단_밖_자막은_후보가_아니다() {
        // CAROL 은 참석자가 아닌데 자막이 들어와 있고, 게다가 가장 크다.
        // (회의 도중 나간 사람의 늦게 도착한 자막 · 잘못된 세션에 실린 자막이 이 모양이다.)
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(CAROL, 10_000, 12_000, "-10.00"),
                        caption(ALICE, 10_000, 12_000, "-18.00")),
                Set.of(ALICE));

        // CAROL 이 아니라 ALICE 다. 명단 밖 자막이 걸러지고 남은 한 명으로 판정한다.
        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("명단 밖 자막은 '전원 자막' 판단도 부풀리지 않는다")
    void 명단_밖_자막은_전원_자막_판단에_끼지_않는다() {
        // BOB 은 자막을 안 켰고, 대신 명단 밖 CAROL 의 자막이 들어와 있다.
        // 후보는 ALICE 한 명뿐인데, 전원 자막이 아니므로 단독 후보를 믿으면 안 된다.
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(CAROL, 10_000, 12_000, "-30.00")),
                Set.of(ALICE, BOB));

        assertThat(result).isEmpty();
    }

    private static Utterance utterance(long id, int startMs, int endMs) {
        return new Utterance(id, null, startMs, endMs, "발화 " + id);
    }

    private static CaptionChunk caption(long memberId, int startMs, int endMs, String rms) {
        return new CaptionChunk(memberId, startMs, endMs, new BigDecimal(rms));
    }
}
