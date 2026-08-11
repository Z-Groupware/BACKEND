package com.module06.backend.capture.domain.model;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.domain.model.TranscriptSegmenter.Segment;
import com.module06.backend.capture.domain.model.TranscriptSegmenter.Word;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정본 분절.
 *
 * <p>이 경계가 뒤 계층 전부의 좌표다 — 근거 발화 ID · 자막 시간 매칭(L1) · L4 의 1인칭 판정이
 * 모두 여기서 나온 발화를 가리킨다. 경계가 달라지면 같은 회의의 근거가 다른 것을 가리킨다.
 */
class TranscriptSegmenterTest {

    @Test
    @DisplayName("마침표에서 끊는다 — 부호는 앞말에 붙는다")
    void 문장_부호에서_끊는다() {
        List<Segment> segments = TranscriptSegmenter.segment(List.of(
                word(0, 400, "로드맵"), word(400, 900, "정리합시다"), punctuation(900, "."),
                word(1000, 1400, "네"), punctuation(1400, ".")));

        assertThat(segments).extracting(Segment::text)
                // "정리합시다 ." 가 되면 그 문자열이 그대로 프롬프트와 화면에 나간다.
                .containsExactly("로드맵 정리합시다.", "네.");
    }

    @Test
    @DisplayName("쉼표는 끊지 않는다 — 열거를 쪼개면 L4 가 읽을 문맥이 잘린다")
    void 쉼표는_끊지_않는다() {
        List<Segment> segments = TranscriptSegmenter.segment(List.of(
                word(0, 300, "김대리님"), punctuation(300, ","),
                word(350, 800, "금요일까지"), punctuation(800, ".")));

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("김대리님, 금요일까지.");
    }

    @Test
    @DisplayName("700ms 이상 벌어지면 끊는다 — 부호를 못 붙인 구간을 위한 것이다")
    void 침묵에서_끊는다() {
        List<Segment> segments = TranscriptSegmenter.segment(List.of(
                word(0, 500, "그건"), word(500, 900, "제가"),
                // 900 → 1700 = 800ms 공백
                word(1700, 2100, "다음"), word(2100, 2500, "안건")));

        assertThat(segments).extracting(Segment::text).containsExactly("그건 제가", "다음 안건");
    }

    @Test
    @DisplayName("699ms 는 끊지 않는다 — 문장 중간 호흡이다")
    void 짧은_공백은_끊지_않는다() {
        List<Segment> segments = TranscriptSegmenter.segment(List.of(
                word(0, 500, "그건"), word(1199, 1500, "제가")));

        assertThat(segments).hasSize(1);
    }

    @Test
    @DisplayName("길이 상한을 넘으면 끊는다 — 안 끊으면 L1 이 자막과 못 맞춘다")
    void 너무_길면_끊는다() {
        /*
         * 부호 없이 이어지는 독백은 부호·침묵 기준으로 안 끊긴다. 그렇게 만들어진 긴 발화는
         * 자막과 ±1.5초 창으로 맞출 수 없어 화자가 비고, 1인칭 액션이 전부 검토로 넘어간다.
         */
        List<Word> words = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            // 500ms 짜리 단어를 공백 없이 이어 붙인다(침묵 기준에 안 걸린다).
            words.add(word(i * 500, i * 500 + 500, "말" + i));
        }

        List<Segment> segments = TranscriptSegmenter.segment(words);

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments).allSatisfy(segment ->
                assertThat(segment.endOffsetMs() - segment.startOffsetMs())
                        .isLessThanOrEqualTo(TranscriptSegmenter.MAX_UTTERANCE_MS + 500));
    }

    @Test
    @DisplayName("부호 없이 끝난 마지막 발화도 남는다 — 버리면 회의 끝부분이 사라진다")
    void 마지막_발화를_버리지_않는다() {
        List<Segment> segments = TranscriptSegmenter.segment(List.of(
                word(0, 400, "그럼"), word(400, 900, "여기까지")));

        assertThat(segments).extracting(Segment::text).containsExactly("그럼 여기까지");
    }

    @Test
    @DisplayName("앞말 없는 부호는 버린다 — 부호만으로 발화를 시작하지 않는다")
    void 앞말_없는_부호는_버린다() {
        List<Segment> segments = TranscriptSegmenter.segment(List.of(
                punctuation(0, "."), word(100, 500, "시작")));

        assertThat(segments).extracting(Segment::text).containsExactly("시작");
    }

    @Test
    @DisplayName("오프셋은 첫 단어의 시작과 마지막의 끝이다")
    void 발화_오프셋을_잡는다() {
        List<Segment> segments = TranscriptSegmenter.segment(List.of(
                word(1_200, 1_600, "확인"), word(1_600, 2_050, "했습니다"), punctuation(2_050, ".")));

        assertThat(segments.get(0).startOffsetMs()).isEqualTo(1_200);
        assertThat(segments.get(0).endOffsetMs()).isEqualTo(2_050);
    }

    @Test
    @DisplayName("단어가 없으면 빈 목록이다")
    void 단어가_없으면_비운다() {
        assertThat(TranscriptSegmenter.segment(List.of())).isEmpty();
        assertThat(TranscriptSegmenter.segment(null)).isEmpty();
    }

    private static Word word(int startMs, int endMs, String text) {
        return new Word(startMs, endMs, text, false);
    }

    private static Word punctuation(int atMs, String mark) {
        return new Word(atMs, atMs, mark, true);
    }
}
