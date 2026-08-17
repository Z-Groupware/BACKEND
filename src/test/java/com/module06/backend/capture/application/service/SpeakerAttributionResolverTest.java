package com.module06.backend.capture.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.module06.backend.capture.application.service.SpeakerAttributionResolver.Attribution;
import com.module06.backend.capture.domain.model.CaptionChunk;
import com.module06.backend.capture.domain.model.SpeakerSource;
import com.module06.backend.capture.domain.model.Utterance;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

/**
 * L1 화자 귀속 판정 — 코드 판정이다(모델 아님).
 *
 * <p>이 판정이 틀리면 그 발화가 근거인 액션이 엉뚱한 사람에게 배정되고, 받은 사람은 자기 일이
 * 아닌 것을 처리해야 한다. 그래서 <b>애매할 때 포기하는지</b>가 확정하는지보다 중요하다 —
 * 아래 테스트 절반이 포기 경로다.
 *
 * <p>두 경로가 있다. 발화에 화자 분리 라벨이 있으면 라벨 경로(V5.23), 없으면 예전 rms 경로다.
 * <b>둘이 섞이지 않는 것</b>이 이 파일이 지키는 가장 중요한 성질이다 — 한 발화를 두고 두 판정이
 * 다투면 둘이 다를 때 어느 쪽이 맞는지 정할 근거가 없다.
 */
class SpeakerAttributionResolverTest {

    private static final long ALICE = 7L;
    private static final long BOB = 8L;
    private static final long CAROL = 9L;

    private final SpeakerAttributionResolver resolver =
            new SpeakerAttributionResolver(new SpeakerLabelAnchorResolver());

    // ── 라벨 경로 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("닻이 내린 라벨의 발화는 회의 전체가 함께 확정된다")
    void 닻이_내린_라벨을_확정한다() {
        /*
         * 이것이 이 설계의 핵심이다. 라벨은 회의 내내 일관되므로 근거가 한 자리에만 있어도
         * 그 사람의 발화 전부가 한꺼번에 풀린다 — 회의 후반의 근거가 전반부까지 소급한다.
         *
         * 여기서는 앨리스가 자막을 보내 spk_0 을 덮는다(자막 겹침 앵커).
         */
        List<Attribution> result = resolver.resolve(
                List.of(labelled(1L, 0, 4_000, "spk_0", "로드맵부터 봅시다"),
                        labelled(2L, 5_000, 9_000, "spk_1", "네 그러시죠"),
                        labelled(3L, 60_000, 64_000, "spk_0", "그럼 제가 정리할게요")),
                List.of(caption(ALICE, 0, 4_000, "-18.00"), caption(ALICE, 60_000, 64_000, "-18.00")),
                Set.of(ALICE, BOB), Map.of());

        assertThat(result).containsExactly(
                new Attribution(1L, ALICE, SpeakerSource.DIARIZATION_ANCHORED),
                // spk_1 은 닻이 없다. 소거법은 라벨 수와 참석자 수가 같을 때만 도는데 여기서는
                // 둘 다 2 라 성립한다 — 그래서 밥으로 채워진다.
                new Attribution(2L, BOB, SpeakerSource.DIARIZATION_ANCHORED),
                new Attribution(3L, ALICE, SpeakerSource.DIARIZATION_ANCHORED));
    }

    @Test
    @DisplayName("닻이 없는 라벨은 포기한다 — 구분됐다는 것이 누구인지 안다는 뜻은 아니다")
    void 닻이_없는_라벨은_포기한다() {
        /*
         * 참석자 3명 · 라벨 3개인데 자막이 없어 앵커가 하나도 없다. 소거법도 못 돈다
         * (남은 라벨이 셋이다). 아무 참석자에게나 붙이면 그 사람의 회의 전체가 남의 말로
         * 채워진다 — 판정 단위가 라벨이라 틀렸을 때의 피해도 그만큼 크다.
         */
        List<Attribution> result = resolver.resolve(
                List.of(labelled(1L, 0, 4_000, "spk_0", "시작합시다"),
                        labelled(2L, 5_000, 9_000, "spk_1", "네"),
                        labelled(3L, 10_000, 14_000, "spk_2", "저도요")),
                List.of(),
                Set.of(ALICE, BOB, CAROL), Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("⚠ 라벨이 있는 발화는 rms 경로를 타지 않는다 — 두 판정이 다투면 안 된다")
    void 라벨이_있으면_rms_경로를_타지_않는다() {
        /*
         * 앨리스 혼자 참석자이고 자막도 보냈으므로 rms 경로였다면 확정됐을 발화다. 그런데
         * 라벨이 붙어 있고 그 라벨에는 닻이 없다 — 이때 rms 로 넘어가 확정해 버리면 두 경로가
         * 한 발화를 두고 다투게 된다. 화자 분리를 켜지 않기로 했던 원래 이유가 그것이다.
         *
         * 자막 겹침 앵커가 안 붙는 이유: 앨리스 자막이 spk_0·spk_1 을 고르게 덮어(대면 회의의
         * 방 마이크 모양) 판별력이 없다.
         */
        List<Attribution> result = resolver.resolve(
                List.of(labelled(1L, 0, 4_000, "spk_0", "가"), labelled(2L, 5_000, 9_000, "spk_1", "나")),
                List.of(caption(ALICE, 0, 4_000, "-18.00"), caption(ALICE, 5_000, 9_000, "-18.00")),
                Set.of(ALICE), Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("라벨 있는 발화와 없는 발화가 섞이면 각자의 경로로 간다")
    void 두_경로가_한_회의에_섞일_수_있다() {
        /*
         * whisper 로 재처리한 블록이나 V5.23 이전에 적재된 블록이 섞인 회의다. 라벨 없는 발화는
         * 예전과 똑같이 rms 로 판정하고, 라벨 있는 발화는 앵커로 판정한다.
         */
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000),
                        labelled(2L, 60_000, 64_000, "spk_0", "제가 하겠습니다")),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(ALICE, 60_000, 64_000, "-18.00")),
                Set.of(ALICE), Map.of());

        assertThat(result).containsExactly(
                // 참석자가 앨리스뿐이고 자막을 보냈으므로 전원 자막이 성립한다.
                new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM),
                new Attribution(2L, ALICE, SpeakerSource.DIARIZATION_ANCHORED));
    }

    // ── rms 경로(라벨 없는 발화) ──────────────────────────────────────────

    @Test
    @DisplayName("창 안 유일한 후보가 있고 전원이 자막을 보냈으면 화자로 정한다")
    void 유일한_후보를_화자로_정한다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00")),
                Set.of(ALICE), Map.of());

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("창 안 후보가 둘 이상이면 확정하지 않는다 — HashMap 반복 순서로 화자를 고르면 안 된다")
    void 후보가_둘_이상이면_포기한다() {
        // host-only 전환 이전에 저장된 참석자 자막이 남아 있거나, 명단·전송 경로 문제로
        // 같은 창 안에 두 사람 이상의 자막이 잡히는 상황이다.
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 10_000, 12_000, "-19.00")),
                Set.of(ALICE, BOB), Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("자막을 안 켠 참석자가 있으면 후보가 한 명이어도 포기한다")
    void 전원이_자막을_보내지_않으면_단독_후보를_믿지_않는다() {
        // 참석자 3명, 자막은 앨리스만 보낸다. 이때 모든 구간의 유일한 후보가 앨리스가 되고,
        // 확정해 버리면 밥·캐롤의 발화까지 전부 앨리스 것이 된다 — 회의 전체 규모의 오귀속이다.
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00")),
                Set.of(ALICE, BOB, CAROL), Map.of());

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
                Set.of(ALICE, BOB), Map.of());

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("자막이 아예 없으면 전원 판정 포기다 — 화자 분리를 안 쓴 경로의 정상 동작")
    void 자막이_없으면_전원_포기한다() {
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000), utterance(2L, 20_000, 22_000)),
                List.of(),
                Set.of(ALICE, BOB), Map.of());

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
                Set.of(ALICE, BOB), Map.of());

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
                Set.of(ALICE), Map.of());

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("시작 오프셋이 없는 발화는 판정하지 않는다 — 창을 만들 수 없다")
    void 오프셋이_없는_발화는_포기한다() {
        List<Attribution> result = resolver.resolve(
                List.of(new Utterance(1L, null, null, null, "위치를 모르는 발화", null)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 60_000, 62_000, "-30.00")),
                Set.of(ALICE, BOB), Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("종료 오프셋이 없으면 시작 지점만으로 창을 만든다")
    void 종료_오프셋이_없어도_판정한다() {
        List<Attribution> result = resolver.resolve(
                // endOffsetMs = null → 길이 0 으로 보고 10초 ±1.5초 창
                List.of(new Utterance(1L, null, 10_000, null, "종료 시각이 없는 발화", null)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00"),
                        caption(BOB, 60_000, 62_000, "-30.00")),
                Set.of(ALICE, BOB), Map.of());

        assertThat(result).containsExactly(new Attribution(1L, ALICE, SpeakerSource.SELF_STREAM));
    }

    @Test
    @DisplayName("참석자 명단이 비어 있으면 아무것도 확정하지 않는다")
    void 참석자_명단이_비면_포기한다() {
        // 명단을 못 읽은 상황이다. "전원 자막"도 소거법도 판단할 분모가 없다.
        List<Attribution> result = resolver.resolve(
                List.of(utterance(1L, 10_000, 12_000)),
                List.of(caption(ALICE, 10_000, 12_000, "-18.00")),
                Set.of(), Map.of());

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
                Set.of(ALICE), Map.of());

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
                Set.of(ALICE, BOB), Map.of());

        assertThat(result).isEmpty();
    }

    /*
     * 판정을 한 건도 못 하는 이유가 **로그로 갈려 나오는지** 본다.
     *
     * 이 계층은 "0건 판정"이 정상 동작이기도 하고 구조적 불가이기도 하다. 로그가 그 둘을
     * 구분하지 못하면 항상 실패하는 상태가 정상으로 보이고, 아무도 찾지 못한다 — VAD 가
     * 전 구간 무음을 VAD_SILENCE(성공)로 집계하던 것과 같은 실패다.
     * 그래서 여기서 검증하는 계약은 "찍힌 한 줄" 자체다(AuthzAuditLoggerTest 와 같은 판단).
     */
    @Nested
    @DisplayName("판정 불가 진단")
    class CannotAttributeDiagnostics {

        private Logger logger;
        private ListAppender<ILoggingEvent> appender;

        @BeforeEach
        void attachAppender() {
            logger = (Logger) LoggerFactory.getLogger(SpeakerAttributionResolver.class);
            appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
        }

        @AfterEach
        void cleanUp() {
            logger.detachAppender(appender);
            appender.stop();
        }

        @Test
        @DisplayName("일부만 자막을 보내면 '자막이 더 쌓여도 달라지지 않는다'까지 남긴다")
        void 일부만_자막이면_구조적_불가임을_남긴다() {
            // host(ALICE)만 자막을 보낸 3명 회의 — host-only 정책에서 다인원 회의의 영구 상태다.
            List<Attribution> attributions = resolver.resolve(
                    List.of(utterance(1L, 0, 3_000)),
                    List.of(caption(ALICE, 0, 3_000, "0.8")),
                    Set.of(ALICE, BOB, CAROL), Map.of());

            assertThat(attributions).isEmpty();
            assertThat(warnings()).singleElement(as(STRING))
                    .contains("참석자 3명 중 1명만 자막")
                    .contains("달라지지 않는다");
        }

        @Test
        @DisplayName("⚠ 라벨이 붙은 발화에는 이 경고를 남기지 않는다 — 게이트가 막는 것은 rms 경로뿐이다")
        void 라벨이_있으면_rms_경고를_남기지_않는다() {
            /*
             * 같은 회의 조건(host 만 자막)이지만 발화에 화자 분리 라벨이 있다. 라벨 경로는
             * 자막과 무관하게 성립하므로 이 경고는 사실이 아니다 — 여기서 울리면 "자막을 더
             * 모아야 한다"로 읽히고, 실제로 손댈 곳(화자 분리)에서 눈을 돌리게 만든다.
             */
            List<Attribution> attributions = resolver.resolve(
                    List.of(labelled(1L, 0, 3_000, "3:spk_0", "제가 하겠습니다")),
                    List.of(caption(ALICE, 0, 3_000, "0.8")),
                    Set.of(ALICE, BOB, CAROL), Map.of());

            // 자막이 spk_0 을 100% 덮고 2등이 없으므로 앨리스로 확정된다.
            assertThat(attributions)
                    .containsExactly(new Attribution(1L, ALICE, SpeakerSource.DIARIZATION_ANCHORED));
            assertThat(warnings()).isEmpty();
        }

        @Test
        @DisplayName("자막 0건은 '자막이 없다'로 따로 남긴다 — 원인도 손댈 곳도 다르다")
        void 자막이_없으면_그렇게_남긴다() {
            List<Attribution> attributions = resolver.resolve(
                    List.of(utterance(1L, 0, 3_000)), List.of(), Set.of(ALICE, BOB), Map.of());

            assertThat(attributions).isEmpty();
            assertThat(warnings()).singleElement(as(STRING)).contains("자막이 0건");
        }

        @Test
        @DisplayName("참석자 명단이 비면 그것도 따로 남긴다")
        void 명단이_비면_그렇게_남긴다() {
            List<Attribution> attributions = resolver.resolve(
                    List.of(utterance(1L, 0, 3_000)),
                    List.of(caption(ALICE, 0, 3_000, "0.8")),
                    Set.of(), Map.of());

            assertThat(attributions).isEmpty();
            /*
             * 경고가 둘이다 — scopedToAttendees 가 "명단 밖 자막을 제외했다"를 먼저 찍는다.
             * 명단이 비면 모든 자막이 명단 밖이라 그게 맞다. 여기서 세는 것은 판정 불가 사유다.
             */
            assertThat(warnings()).anySatisfy(message ->
                    assertThat(message).contains("참석자 명단이 비어"));
        }

        @Test
        @DisplayName("정상 판정에는 경고를 남기지 않는다 — 늘 울리는 경고는 아무도 안 본다")
        void 정상_판정에는_경고가_없다() {
            List<Attribution> attributions = resolver.resolve(
                    List.of(utterance(1L, 0, 3_000)),
                    List.of(caption(ALICE, 0, 3_000, "0.8")),
                    Set.of(ALICE), Map.of());

            assertThat(attributions).hasSize(1);
            assertThat(warnings()).isEmpty();
        }

        private List<String> warnings() {
            return appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
        }
    }

    private static Utterance utterance(long id, int startMs, int endMs) {
        return new Utterance(id, null, startMs, endMs, "발화 " + id, null);
    }

    private static Utterance labelled(long id, int startMs, int endMs, String label, String text) {
        return new Utterance(id, null, startMs, endMs, text, label);
    }

    private static CaptionChunk caption(long memberId, int startMs, int endMs, String rms) {
        return new CaptionChunk(memberId, startMs, endMs, new BigDecimal(rms));
    }
}
