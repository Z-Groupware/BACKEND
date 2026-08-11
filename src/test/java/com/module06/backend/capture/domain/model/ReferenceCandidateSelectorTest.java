package com.module06.backend.capture.domain.model;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지시어 후보 선별.
 *
 * <p>여기서 빠진 발화는 <b>영구히</b> 안 풀린다 — Python 이 후보 밖 utteranceId 를 응답에서
 * 버리므로(parse_references) 조용히 사라진다. 그래서 이 테스트는 "안 걸리는 것"보다
 * <b>"걸려야 하는 것"</b>을 훨씬 많이 본다. 재현율이 이 클래스의 계약이다.
 */
class ReferenceCandidateSelectorTest {

    @Test
    @DisplayName("1인칭은 고른다 — 담당자가 여기서 갈린다")
    void 일인칭_발화를_고른다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(1L, "제가 금요일까지 정리하겠습니다"),
                utterance(2L, "배포 순서를 확정합시다"),
                utterance(3L, "저희 쪽에서 문서를 맡을게요")));

        // 2번은 지시어가 없다. 고르면 모델이 그 발화에 해소를 붙일 수 있게 된다.
        assertThat(targets).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("네 종류를 모두 고른다 — 종류를 미리 나누지 않는다")
    void 네_종류를_모두_고른다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(1L, "그분한테 물어보죠"),          // PERSON
                utterance(2L, "아까 나온 얘기로 돌아가면"),   // TOPIC
                utterance(3L, "그거 어디 있죠"),              // ARTIFACT
                utterance(4L, "그때 정한 기준이 뭐였죠")));   // TIME

        assertThat(targets).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("조사가 붙어도 고른다 — 형태소 분석을 하지 않으므로 어간으로 잡는다")
    void 조사가_붙어도_고른다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(1L, "그거를 먼저 봐야 합니다"),
                utterance(2L, "저쪽이 더 급해요"),
                utterance(3L, "그때부터 밀린 겁니다")));

        assertThat(targets).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("1인칭의 격을 빠짐없이 고른다 — 여격·목적격을 빼면 담당자가 조용히 미정이 된다")
    void 일인칭의_모든_격을_고른다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(1L, "저한테 주세요"),
                utterance(2L, "저에게 넘겨주시면 됩니다"),
                utterance(3L, "제게 공유해 주세요"),
                utterance(4L, "저를 넣어주세요"),
                utterance(5L, "저랑 같이 보죠"),
                utterance(6L, "저의 담당은 배포입니다"),
                utterance(7L, "내가 할게"),
                utterance(8L, "나한테 주면 돼")));

        assertThat(targets).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    @Test
    @DisplayName("놓친 발화는 폴백이 구해주지 않는다 — 다른 발화가 걸리면 그대로 사라진다")
    void 다른_후보가_있으면_폴백이_없다() {
        /*
         * 이 테스트가 왜 있나 — Python 은 targets 가 **완전히 비었을 때만** 전체로 되돌린다
         * (`return narrowed or request.utterances`). 그래서 "회의에 지시어가 많으면 어차피
         * 폴백이 돌 것"이라는 기대가 성립하지 않는다. 어간 목록의 빈칸은 그 발화 하나를
         * 영구히 잃는다.
         *
         * 아래에서 2번이 목록에 들어가야 한다. 1번이 이미 걸려 targets 가 비지 않기 때문이다.
         */
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(1L, "그거 확인해 주세요"),
                utterance(2L, "저한테 주시면 정리하겠습니다")));

        assertThat(targets).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("맨 '저'로는 걸리지 않는다 — '저장'·'저번'까지 걸면 좁히는 의미가 사라진다")
    void 저장은_후보가_아니다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(1L, "저장 로직을 손봐야 합니다"),
                utterance(2L, "저번 스프린트 회고 결과입니다")));

        /*
         * ⚠ 이 테스트는 정밀도를 위한 것이고, 재현율과 맞바꾼 자리다. "저번"은 사실 TOPIC
         * 지시어일 수 있다. 그래도 "저"를 넣으면 거의 모든 한국어 발화가 후보가 되어 선별이
         * 사라지므로, 애매한 이 둘은 포기한다 — 대신 "아까"·"앞에서" 같은 다른 표현이 같은
         * 발화에 함께 오는 경우가 많다.
         */
        assertThat(targets).isEmpty();
    }

    @Test
    @DisplayName("하나도 없으면 빈 목록 — 전체가 대상이 되어 예전 동작으로 돌아간다")
    void 후보가_없으면_비운다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(1L, "배포 순서를 확정합시다"),
                utterance(2L, "네 좋습니다")));

        // 계층 생략으로 쓰지 않는다는 계약이 여기 걸린다 — 비어 있음이 곧 "전체 대상"이다.
        assertThat(targets).isEmpty();
    }

    @Test
    @DisplayName("정본 순서를 유지한다 — 근거 발화 좌표가 프롬프트 순서와 어긋나면 안 된다")
    void 정본_순서를_유지한다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(30L, "그거 확인했습니다"),
                utterance(10L, "제가 맡을게요"),
                utterance(20L, "그때 정했죠")));

        // 정렬하지 않는다. 호출자가 넘긴 순서가 곧 회의의 시간 순서다.
        assertThat(targets).containsExactly(30L, 10L, 20L);
    }

    @Test
    @DisplayName("id·본문이 없는 발화는 건너뛴다 — null id 를 넘기면 Python 이 422 로 거절한다")
    void 불완전한_발화는_건너뛴다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                new Utterance(null, null, 0, 500, "그거 맞습니다"),
                new Utterance(2L, null, 500, 900, null),
                new Utterance(3L, null, 900, 1200, "   "),
                utterance(4L, "그거 맞습니다")));

        assertThat(targets).containsExactly(4L);
    }

    @Test
    @DisplayName("빈 입력과 null 입력에서 터지지 않는다")
    void 빈_입력을_견딘다() {
        assertThat(ReferenceCandidateSelector.select(List.of())).isEmpty();
        assertThat(ReferenceCandidateSelector.select(null)).isEmpty();
    }

    @Test
    @DisplayName("같은 발화가 두 종류에 걸려도 한 번만 담는다")
    void 중복으로_담지_않는다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                // PERSON("제가")과 TIME("그때")이 같이 있다.
                utterance(1L, "제가 그때 말한 부분입니다")));

        assertThat(targets).containsExactly(1L);
    }

    @Test
    @DisplayName("호칭을 고른다 — 이름을 부른 발화가 곧 담당자 지정인 경우가 많다")
    void 호칭을_고른다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(1L, "김대리님이 봐주시겠어요"),
                // 조사를 나열하는 대신 맨 "님"으로 잡으므로 "님도"·"님을"·"님하고"도 걸린다.
                utterance(2L, "박팀장님도 참석하시나요"),
                utterance(3L, "이사님을 모셔야 합니다"),
                utterance(4L, "담당자 정해야 합니다")));

        assertThat(targets).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("'날씨'는 후보가 아니다 — '씨'를 맨으로 잡을 수 없어 조사 형태로 나열했다")
    void 날씨는_후보가_아니다() {
        List<Long> targets = ReferenceCandidateSelector.select(List.of(
                utterance(1L, "날씨 때문에 행사를 미뤘습니다"),
                // "내 "를 넣지 않은 이유 — 업무 회의에서 자주 나오는 말들이다.
                utterance(2L, "사내 규정을 먼저 봐야 합니다"),
                utterance(3L, "국내 영업 실적입니다")));

        assertThat(targets).isEmpty();
    }

    private static Utterance utterance(long id, String text) {
        return new Utterance(id, null, 0, 500, text);
    }
}
