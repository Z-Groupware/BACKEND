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
    @DisplayName("창 안에서 rms 가 가장 큰 참석자를 화자로 정한다")
    void 음량이_가장_큰_참석자가_화자다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 10_000, 12_000, "-30.00")),
                Set.of(ALICE, BOB));

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("1·2등 차이가 3dB 미만이면 판정을 포기한다 — 큰 쪽을 고르는 건 동전 던지기다")
    void 음량_차이가_좁으면_포기한다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                // 차이 2.99dB — 임계값 바로 아래
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 10_000, 12_000, "-20.99")),
                Set.of(ALICE, BOB));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("차이가 정확히 3dB 면 확정한다 — 경계는 포함이다")
    void 차이가_정확히_임계값이면_확정한다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 10_000, 12_000, "-21.00")),
                Set.of(ALICE, BOB));

        // DECIMAL(6,2) 를 BigDecimal 로 받는 이유가 이 경계다 — double 이면 오차로 갈린다.
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
    @DisplayName("참석자 2명 중 한 명만 자막을 보내면, 자막 없는 구간은 나머지 한 명이다 (소거법)")
    void 참석자_2명이면_소거법이_성립한다() {
        // 앨리스만 자막을 보낸다. 60초 구간에 앨리스 자막이 없으므로 말한 사람은 밥이다.
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000), utterance(2L, 60_000, 62_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00")),
                Set.of(ALICE, BOB));

        assertThat(result).containsExactly(
                // 앨리스 자막이 있는 구간: 전원 자막이 아니므로 단독 후보로는 확정 못 한다
                new Attribution(2L, BOB, SpeakerSource.ELIMINATION));
    }

    @Test
    @DisplayName("참석자가 3명이면 소거법을 쓰지 않는다 — 지워도 나머지가 둘이다")
    void 참석자가_3명이면_소거법을_쓰지_않는다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 60_000, 62_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00")),
                Set.of(ALICE, BOB, CAROL));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("자막이 아예 없으면 전원 판정 포기다 — CAP-11 미구현 상태의 정상 동작")
    void 자막이_없으면_전원_포기한다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000), utterance(2L, 20_000, 22_000)),
                List.of(),
                Set.of(ALICE, BOB));

        // 참석자가 2명이지만 **둘 다** 자막을 안 보냈으므로 소거할 대상이 없다.
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
                List.of(caption(ALICE, 13_500, 20_000, "-10.00"),
                        caption(BOB, 10_000, 12_000, "-30.00")),
                Set.of(ALICE, BOB));

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("같은 참석자의 자막이 여러 개면 합이 아니라 최대를 쓴다")
    void 참석자별로_최대_음량을_쓴다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                // 밥은 조각 셋을 보냈다. 합(-60)으로 계산하면 앨리스가 이기지만,
                // 최대(-19)로 보면 차이가 1dB 라 포기해야 한다 — 청크를 잘게 보내는
                // 브라우저가 판정에서 유리해지는 경로를 막는 자리다.
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 10_000, 10_600, "-20.00"),
                        caption(BOB, 10_600, 11_200, "-21.00"),
                        caption(BOB, 11_200, 12_000, "-19.00")),
                Set.of(ALICE, BOB));

        assertThat(result).isEmpty();
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

    private static Utterance utterance(long id, int startMs, int endMs) {
        return new Utterance(id, null, startMs, endMs, "발화 " + id);
    }

    private static CaptionChunk caption(long memberId, int startMs, int endMs, String rms) {
        return new CaptionChunk(memberId, startMs, endMs, new BigDecimal(rms));
    }
}
