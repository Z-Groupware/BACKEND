package com.module06.backend.capture.domain.model;

import java.util.ArrayList;
import java.util.List;

/*
 * STT 가 준 단어들을 **발화 단위**로 묶는다.
 *
 * <h2>왜 도메인에 있나 — 제공자마다 다시 정할 값이 아니다</h2>
 * Transcribe 도 whisper 도 단어(또는 토큰)와 시간을 준다. "그 단어들을 어디서 끊어 한 발화로
 * 볼 것인가"는 제공자 사정이 아니라 **이 파이프라인의 판단**이다 — 뒤 계층 전부가 이 경계를
 * 좌표로 쓴다(근거 발화 ID · 자막 시간 매칭 · L4 의 1인칭 판정). 어댑터에 두면 제공자를
 * 바꿀 때 발화 경계가 조용히 달라지고, 그러면 같은 회의의 근거 발화가 다른 것을 가리킨다.
 *
 * <h2>끊는 기준 넷</h2>
 * <ol>
 *   <li><b>화자 전환</b> — 화자 라벨이 바뀌면 끊는다. 넷 중 가장 강한 기준이다(아래)</li>
 *   <li><b>문장 부호</b> — 마침표·물음표·느낌표에서 끊는다. 가장 신뢰할 만한 경계다</li>
 *   <li><b>침묵</b> — 단어 사이가 {@link #SILENCE_BREAK_MS} 이상 벌어지면 끊는다. 부호를
 *       못 붙인 구간을 위한 것이다</li>
 *   <li><b>길이 상한</b> — {@link #MAX_UTTERANCE_MS} 를 넘으면 끊는다</li>
 * </ol>
 *
 * <h2>화자 전환이 다른 셋을 이긴다</h2>
 * 다른 기준은 "여기서 끊는 편이 낫다"이지만 이것은 <b>끊지 않으면 틀린다</b>이다. 두 사람의
 * 말이 한 발화에 들어가면 그 발화의 화자는 애초에 하나로 정해질 수 없고, 그 위에 붙는 판정이
 * 전부 근거를 잃는다 — L4 가 "제가 하겠습니다"의 담당자를 정할 때 보는 것이 이 경계다.
 *
 * 그래서 부호나 침묵을 기다리지 않는다. 한국어 회의에서 사람이 말을 자르고 들어오는 자리는
 * 부호가 안 붙고 침묵도 없다("그건 제가— 아 그럼 제가 할게요"). 그 구간이 정확히 화자가 바뀌는
 * 자리다.
 *
 * <h2>라벨이 없으면 이 기준은 그냥 없는 것이 된다</h2>
 * 화자 분리를 끈 제공자·스텁·V5.23 이전 블록은 라벨이 전부 null 이라 전환이 일어나지 않고,
 * 예전과 똑같이 나머지 셋으로만 끊는다. 이 기준을 더한다고 옛 경로의 경계가 달라지지 않는다.
 *
 * <h2>침묵 기준을 VAD 와 같은 700ms 로 둔다</h2>
 * 그쪽이 "이보다 짧으면 문장 중간 호흡이라 자르면 안 된다"고 정한 값이다(설계 문서 튜닝 표).
 * 같은 오디오에 두 기준을 두면 블록 경계와 발화 경계가 서로 다른 이유로 갈리고, 품질을 조사할
 * 때 어느 값을 움직여야 하는지 알 수 없게 된다.
 *
 * <h2>길이 상한이 왜 필요한가 — L1 이 못 맞춘다</h2>
 * 부호 없이 이어지는 독백은 기준 ①②로 안 끊긴다. 그렇게 만들어진 60초짜리 발화는 자막과
 * ±1.5초 창으로 맞출 수가 없다 — 자막은 짧은 조각으로 오는데 정본은 통째라 어느 자막이 이
 * 발화의 화자인지 정할 근거가 사라진다. 화자가 비면 1인칭 액션이 전부 검토로 넘어간다.
 */
public final class TranscriptSegmenter {

    /* VAD 무음 임계와 같은 값이다(설계 문서 튜닝 시작값). */
    static final int SILENCE_BREAK_MS = 700;

    /*
     * 발화 하나의 상한. 자막 매칭 창(±1.5초)의 열 배 남짓이라 한 발화 안에 자막 여럿이
     * 겹치더라도 rms 최대값을 고르는 판정이 성립한다.
     */
    static final int MAX_UTTERANCE_MS = 20_000;

    private TranscriptSegmenter() {
    }

    /*
     * @param words 시간순으로 정렬된 단어. 오프셋은 **회의 기준**이어야 한다 — 블록 기준
     *              오프셋을 그대로 넘기면 두 번째 블록부터 좌표가 처음으로 되돌아간다
     * @return 발화. 단어가 없으면 빈 목록
     */
    public static List<Segment> segment(List<Word> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }

        List<Segment> segments = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int startMs = -1;
        int endMs = -1;
        int previousEndMs = -1;
        /* 지금 쌓고 있는 발화의 화자 라벨. 라벨을 쓰지 않는 경로에서는 계속 null 이다. */
        String label = null;

        for (Word word : words) {
            /*
             * 부호는 앞말에 **붙인다.** 공백을 넣으면 "안녕하세요 ." 가 되고, 그 텍스트가 그대로
             * 프롬프트와 화면에 나간다. 부호만으로는 발화를 시작하지도 않는다 — 앞말이 없는
             * 부호는 버린다(제공자가 구간 첫 토큰으로 부호를 주는 경우가 있다).
             *
             * ⚠ 부호로는 화자 전환을 판정하지 않는다. 제공자가 부호 항목에 라벨을 안 주거나
             * 뒷사람 라벨을 주기도 하는데, 그걸 전환으로 읽으면 문장 끝의 마침표 하나 때문에
             * 빈 발화가 하나 생긴다. 부호는 언제나 **앞말이 속한** 발화의 것이다.
             */
            if (word.punctuation()) {
                if (text.isEmpty()) {
                    continue;
                }
                text.append(word.text());
                endMs = Math.max(endMs, word.endMs());
                if (isSentenceEnd(word.text())) {
                    segments.add(new Segment(startMs, endMs, text.toString(), label));
                    text.setLength(0);
                    startMs = -1;
                    endMs = -1;
                    label = null;
                }
                previousEndMs = word.endMs();
                continue;
            }

            /*
             * 화자가 바뀌었다. 라벨이 둘 다 있을 때만 본다 — 한쪽이 null 인 것은 "화자가
             * 바뀌었다"가 아니라 "그 단어의 화자를 모른다"이고, 그걸 전환으로 읽으면 라벨을
             * 띄엄띄엄 주는 응답에서 발화가 잘게 부서진다.
             */
            boolean speakerBreak = label != null && word.speakerLabel() != null
                    && !label.equals(word.speakerLabel());
            boolean silenceBreak = previousEndMs >= 0 && word.startMs() - previousEndMs >= SILENCE_BREAK_MS;
            boolean tooLong = startMs >= 0 && word.endMs() - startMs > MAX_UTTERANCE_MS;
            if (!text.isEmpty() && (speakerBreak || silenceBreak || tooLong)) {
                segments.add(new Segment(startMs, endMs, text.toString(), label));
                text.setLength(0);
                startMs = -1;
                endMs = -1;
                label = null;
            }

            if (text.isEmpty()) {
                startMs = word.startMs();
                /*
                 * 발화의 라벨은 **첫 단어의 것**이다. 뒤 단어가 다른 라벨이면 위에서 끊기므로
                 * 한 발화 안의 라벨은 언제나 하나다. 첫 단어의 라벨이 null 이면 그 발화는
                 * 라벨 없이 남는다 — 뒤에 라벨 있는 단어가 이어져도 소급해 채우지 않는다.
                 * 채우면 그 발화의 앞부분이 누구 말인지 모르는데 전체를 그 사람 것으로
                 * 확정하게 된다.
                 */
                label = word.speakerLabel();
            } else {
                text.append(' ');
            }
            text.append(word.text());
            endMs = word.endMs();
            previousEndMs = word.endMs();
        }

        // 부호 없이 끝난 마지막 발화. 버리면 회의 끝부분이 통째로 사라진다.
        if (!text.isEmpty()) {
            segments.add(new Segment(startMs, endMs, text.toString(), label));
        }
        return segments;
    }

    /*
     * 문장을 끝내는 부호인가.
     *
     * 쉼표·가운뎃점은 끊지 않는다 — 한 문장 안의 열거를 발화 여럿으로 쪼개면 근거 발화가
     * 문장의 반쪽만 가리키게 되고, L4 가 "누가 무엇을" 을 읽을 문맥이 잘린다.
     */
    private static boolean isSentenceEnd(String punctuation) {
        return punctuation.contains(".") || punctuation.contains("?") || punctuation.contains("!");
    }

    /*
     * STT 가 준 토큰 하나.
     *
     * @param startMs      회의 기준 시작 오프셋. 부호 토큰은 앞말과 같은 자리일 수 있다
     * @param punctuation  부호인가. 제공자가 이 구분을 주므로 우리가 문자로 추측하지 않는다 —
     *                     한국어에는 마침표 없이 끝나는 문장도 많아 추측이 잘 틀린다
     * @param speakerLabel 제공자가 붙인 화자 라벨. **사람이 아니다** — {@code spk_0} 같은
     *                     군집 번호이고 누구인지는 말하지 않는다(SpeakerLabelAnchorResolver 가
     *                     사람으로 바꾼다). 화자 분리를 끈 경로에서는 null 이다
     */
    public record Word(int startMs, int endMs, String text, boolean punctuation, String speakerLabel) {
    }

    /*
     * 묶인 발화 하나. 그대로 transcript_chunk 한 행이 된다.
     *
     * speakerLabel 은 이 발화 전체의 화자 라벨이다 — 라벨이 바뀌면 발화를 끊으므로 하나뿐이다.
     */
    public record Segment(int startOffsetMs, int endOffsetMs, String text, String speakerLabel) {
    }
}
