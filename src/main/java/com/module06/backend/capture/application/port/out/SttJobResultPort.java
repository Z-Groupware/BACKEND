package com.module06.backend.capture.application.port.out;

import java.util.List;

import com.module06.backend.capture.domain.model.TranscriptSegmenter.Word;

/*
 * 제출한 STT 잡이 어떻게 됐는지 묻는다.
 *
 * <h2>왜 폴링인가 — 콜백이 없다</h2>
 * AWS Transcribe 는 완료를 HTTP 로 알려주지 않는다. EventBridge → SQS/Lambda 로 받을 수는
 * 있지만 이 저장소에는 큐가 아직 없고(분석 트리거도 큐 대신 전용 스레드 풀을 쓴다), 큐를
 * 들이는 것은 이 작업보다 큰 결정이다. 그래서 주기 워커가 물어본다.
 *
 * <h2>포트가 단어를 돌려주는 이유</h2>
 * 발화 경계를 어디서 끊을지는 **이 파이프라인의 판단**이라 도메인이 정한다
 * ({@link com.module06.backend.capture.domain.model.TranscriptSegmenter}). 어댑터가 문장까지
 * 묶어서 주면 제공자를 바꿀 때 경계 규칙이 함께 바뀌고, 같은 회의의 근거 발화가 다른 것을
 * 가리키게 된다. 어댑터는 **제공자 응답을 단어로 펴는 것까지**만 한다.
 *
 * <h2>오프셋은 블록 기준이다</h2>
 * 제공자는 자기가 받은 오디오(=블록 하나)의 처음을 0 으로 준다. 회의 기준으로 옮기는 것은
 * 블록의 startOffsetMs 를 아는 쪽(폴링 서비스)의 몫이다 — 어댑터에 블록 오프셋을 넘겨
 * 거기서 더하게 하면, 같은 덧셈이 제공자마다 반복되고 한 곳이 빠지면 그 블록만 좌표가 어긋난다.
 */
public interface SttJobResultPort {

    /*
     * 잡 하나의 현재 상태를 읽는다.
     *
     * @param providerJobName 제출할 때 쓴 이름. 이것 말고 잡을 가리키는 방법이 없다
     * @return 제공자가 그 이름을 모르면 {@link State#UNKNOWN}. 예외를 던지지 않는다 —
     *         워커가 블록 하나 때문에 멈추면 나머지 블록도 함께 밀린다
     */
    SttJobOutcome fetch(String providerJobName);

    enum State {
        /* 아직 큐에 있다. 다음 주기에 다시 본다. */
        QUEUED,
        /* 제공자가 돌리는 중이다. */
        RUNNING,
        /* 끝났다. words 에 결과가 들어 있다. */
        COMPLETED,
        /* 제공자가 실패로 닫았다. */
        FAILED,
        /*
         * 그 이름의 잡이 없다. 제출이 실제로 안 됐거나(응답을 못 받아 우리만 QUEUED 로 남은
         * 경우) 보관 기간이 지난 것이다. 성공도 실패도 아니라 **따로 둔다** — 실패로 접으면
         * 사람이 재처리를 눌러 같은 이름으로 또 제출하고 같은 자리로 돌아온다.
         */
        UNKNOWN,
        /* 제공자를 못 읽었다(네트워크·권한). 상태를 바꾸지 않고 다음 주기에 다시 본다. */
        UNAVAILABLE
    }

    /*
     * @param errorCode 실패 사유. **제공자 메시지를 그대로 담지 않는다**(V5.4 주석) — 화면이
     *                  이 코드로 문구를 고르므로 제공자 문자열이 새면 되돌릴 수 없다
     * @param words     COMPLETED 일 때만 채워진다. 오프셋은 **블록 기준**이다
     */
    record SttJobOutcome(State state, String errorCode, List<Word> words) {

        public static SttJobOutcome of(State state) {
            return new SttJobOutcome(state, null, List.of());
        }

        public static SttJobOutcome failed(String errorCode) {
            return new SttJobOutcome(State.FAILED, errorCode, List.of());
        }

        public static SttJobOutcome completed(List<Word> words) {
            return new SttJobOutcome(State.COMPLETED, null, words);
        }
    }
}
